@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Selected value in multiple columns selection: an expression, an entity or a list of table columns.
 */
sealed interface SqlOutput

/**
 * One selected value: an expression, read from its column, or a table, whose entity is read from the columns that
 * `SELECT FROM` the table selects.
 *
 * A table must be a source that the query names, in `FROM` or a join, and not one that an outer join can leave NULL.
 * Its related objects are built from their stored IDs, so a table with a relationship that is always joined is
 * rejected; select it with `SELECT FROM` instead. These are checked when the query is compiled.
 *
 * @param R Kotlin type of the stored values of an expression, or [EntityRows] for an entity
 * @param T Type that the value is read as
 */
sealed interface SelectValue<out R, out T> : SqlOutput

/**
 * Stored type of a selected entity. No column has this type, so the typed `INSERT … SELECT` stages do not accept a
 * selection with an entity.
 */
class EntityRows private constructor()

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

  /**
   * Get the entity of a selected table.
   *
   * @param entity Selected table. It must be the same instance that is selected
   * @return Entity read from the row
   * @throws IllegalArgumentException if [entity] is not selected
   */
  abstract operator fun <T> get(entity: Table<T>): T
}

internal class SelectedOutputs(
  values: List<SelectValue<*, *>>,
  val layout: ValueLayout
) {
  private val positions = MutableObjectIntMap<SelectValue<*, *>>(initialCapacity = values.size)
    .apply {
      values.forEachIndexed { index, value ->
        if (!containsKey(value)) {
          this[value] = index
        }
        if (value is KeyedField<*, *, *> && !containsKey(value.field)) {
          this[value.field] = index
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
        val entity = (expression.node as? ExpressionNode.Field)?.table?.takeIf(positions::containsKey)
        when {
          positions.containsKey(expression.nullable()) ->
            "$expression is selected as nullable; read it with .nullable()"
          entity != null -> "$expression is only part of the selected ${entity.referenceName} entity; select it to " +
              "read it"
          else -> "$expression was not selected"
        }
      }
    }

  fun position(entity: Table<*>) = positions
    .getOrDefault(
      key = entity,
      defaultValue = -1
    )
    .also { require(it >= 0) { "The ${entity.referenceName} entity was not selected" } }
}

internal class OwnedRowsReader(
  private val values: List<SelectValue<*, *>>,
  private val layout: ValueLayout
) : ExecutionReader<SelectedRow> {
  private var read: OwnedRowRead? = null

  override fun open(): ValueRead<SelectedRow> = read ?: OwnedRowRead(
    values = values,
    layout = layout
  ).also { read = it }
}

private class OwnedRowRead(
  values: List<SelectValue<*, *>>,
  layout: ValueLayout
) : ValueRead<SelectedRow> {
  private val outputs = SelectedOutputs(
    values = values,
    layout = layout
  )
  private val starts = layout.starts
  private val readers = Array(values.size) { values[it].read(layout.related(it)) }

  override fun invoke(statement: SQLiteStatement, index: Int): SelectedRow = OwnedSelectedRow(
    outputs = outputs,
    values = Array(readers.size) { readers[it](statement, index + starts[it]) }
  )
}

internal class LiveRowsReader<M>(
  private val values: List<SelectValue<*, *>>,
  private val layout: ValueLayout,
  private val mapper: (SelectedRow) -> M
) : ExecutionReader<M> {
  private var outputs: SelectedOutputs? = null

  override fun open(): ValueRead<M> {
    val row = LiveSelectedRow(
      outputs ?: SelectedOutputs(
        values = values,
        layout = layout
      ).also { outputs = it }
    )
    return ValueRead { statement, startIndex ->
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

  @Suppress("UNCHECKED_CAST")
  override fun <T> get(entity: Table<T>) = values[outputs.position(entity)] as T

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
  private val layout = outputs.layout

  fun moveTo(statement: SQLiteStatement, startIndex: Int) = apply {
    this.statement = statement
    this.startIndex = startIndex
  }

  override fun <T> get(expression: SqlExpression<*, *, T>) = expression.selectRead(
    statement,
    startIndex + layout.starts[outputs.position(expression)]
  )

  override fun <T> get(entity: Table<T>): T {
    val position = outputs.position(entity)
    return entity.readEntityAt(
      statement = statement,
      startIndex = startIndex + layout.starts[position],
      related = layout.related(position)
    )
  }
}
