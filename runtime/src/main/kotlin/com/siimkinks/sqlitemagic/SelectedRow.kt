@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap

/**
 * Selected value in multiple columns selection: an expression or a list of table columns.
 */
sealed interface SqlOutput

/**
 * All columns of a table, in column order.
 *
 * Create one with `all()` of a table.
 */
class TableFields internal constructor(
  private val columns: List<Column<*, *, *, *, *, *, *>>,
  private val key: String? = null,
  private val lifted: Boolean = false
) : SqlOutput {
  internal val fields: List<SqlExpression<*, *, *>>
    get() = when {
      key == null && !lifted -> columns
      else -> columns.map(::output)
    }

  /**
   * Create an alias for these columns in a view definition.
   *
   * The columns are named `key.column`, which a view model property annotated with `@ViewColumn("key")` reads.
   *
   * Example:
   * ```
   * TODO_LIST.all() AS "list"
   * ```
   *
   * @param key The alias name
   * @return Columns with provided alias
   */
  infix fun AS(key: String) = TableFields(
    columns = columns,
    key = key,
    lifted = lifted
  )

  /**
   * Convert these columns to nullable columns.
   *
   * The SQL stays the same. Use it for selecting all columns of a table that an outer join can leave NULL. In a view
   * definition they fill a nullable model property, which is `null` when all its columns are NULL. In a selected row
   * or a subquery, read a not nullable column with its `nullable()`, for example `row[BOOK.TITLE.nullable()]`.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(AUTHOR.all() AS "author", BOOK.all().nullable() AS "book")
   *     .FROM(AUTHOR)
   *     .LEFT_JOIN(BOOK)
   *     .ON(AUTHOR.ID IS BOOK.AUTHOR)
   * ```
   *
   * @return Nullable columns
   */
  fun nullable() = TableFields(
    columns = columns,
    key = key,
    lifted = true
  )

  private fun output(column: Column<*, *, *, *, *, *, *>): SqlExpression<*, *, *> {
    val field = when {
      lifted && column.nullness == Required -> column.nullable()
      else -> column
    }
    return when (key) {
      null -> field
      else -> KeyedField(
        field = field,
        name = column.name,
        key = key
      )
    }
  }
}

internal class KeyedField<R, N : SqlNullability, T>(
  val field: SqlExpression<R, N, T>,
  name: String,
  val key: String
) : SqlExpression<R, N, T>(
  source = field,
  outputAlias = "$key.$name"
)

/**
 * Row of multiple columns selection.
 *
 * Values are read by the selected expressions, for example `row[BOOK.ID]`. A row passed to `mapTo` reads the
 * current result row and is valid only during the call.
 */
sealed class SelectedRow(
  internal val outputs: SelectedOutputs
) {
  /**
   * Get the value of a selected expression.
   *
   * @param expression Selected expression or a column of selected `all()` columns. It must be the same instance that
   *     is selected. A not nullable column of selected `all().nullable()` columns is read with its `nullable()`
   * @return Value of the expression
   * @throws IllegalArgumentException if [expression] is not selected
   */
  abstract operator fun <T> get(expression: SqlExpression<*, *, T>): T
}

internal class SelectedOutputs(expressions: List<SqlExpression<*, *, *>>) {
  private val positions = MutableObjectIntMap<SqlExpression<*, *, *>>(initialCapacity = expressions.size)
    .apply {
      expressions.forEachIndexed { index, expression ->
        if (!containsKey(expression)) {
          this[expression] = index
        }
        if (expression is KeyedField<*, *, *> && !containsKey(expression.field)) {
          this[expression.field] = index
        }
      }
    }

  fun position(expression: SqlExpression<*, *, *>) = positions
    .getOrDefault(
      key = expression,
      defaultValue = -1
    )
    .also {
      require(it >= 0) {
        when {
          positions.containsKey(expression.nullable()) ->
            "$expression is selected as nullable; read it with .nullable()"
          else -> "$expression was not selected"
        }
      }
    }
}

internal class OwnedRowsReader(
  private val expressions: List<SqlExpression<*, *, *>>
) : ExecutionReader<SelectedRow> {
  private var read: OwnedRowRead? = null

  override fun open(): (SQLiteStatement, Int) -> SelectedRow = read ?: OwnedRowRead(expressions).also { read = it }
}

private class OwnedRowRead(expressions: List<SqlExpression<*, *, *>>) : (SQLiteStatement, Int) -> SelectedRow {
  private val outputs = SelectedOutputs(expressions)
  private val readers = Array(expressions.size) { expressions[it].selectRead }

  override fun invoke(statement: SQLiteStatement, startIndex: Int): SelectedRow = OwnedSelectedRow(
    outputs = outputs,
    values = Array(readers.size) { readers[it](statement, startIndex + it) }
  )
}

internal class LiveRowsReader<M>(
  private val expressions: List<SqlExpression<*, *, *>>,
  private val mapper: (SelectedRow) -> M
) : ExecutionReader<M> {
  private var outputs: SelectedOutputs? = null

  override fun open(): (SQLiteStatement, Int) -> M {
    val row = LiveSelectedRow(outputs ?: SelectedOutputs(expressions).also { outputs = it })
    return { statement, startIndex ->
      mapper(
        row.moveTo(
          statement = statement,
          startIndex = startIndex
        )
      )
    }
  }
}

private class OwnedSelectedRow(
  outputs: SelectedOutputs,
  private val values: Array<Any?>
) : SelectedRow(outputs) {
  @Suppress("UNCHECKED_CAST")
  override fun <T> get(expression: SqlExpression<*, *, T>) = values[outputs.position(expression)] as T

  override fun equals(other: Any?) = other is OwnedSelectedRow && values.contentEquals(other.values)

  override fun hashCode() = values.contentHashCode()

  override fun toString() = values.joinToString(
    prefix = "SelectedRow(",
    postfix = ")"
  )
}

private class LiveSelectedRow(outputs: SelectedOutputs) : SelectedRow(outputs) {
  private lateinit var statement: SQLiteStatement
  private var startIndex = 0

  fun moveTo(statement: SQLiteStatement, startIndex: Int) = apply {
    this.statement = statement
    this.startIndex = startIndex
  }

  override fun <T> get(expression: SqlExpression<*, *, T>) = expression.selectRead(
    statement,
    startIndex + outputs.position(expression)
  )
}
