@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
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
  key: String? = null
) : SqlOutput {
  internal val fields: List<SqlExpression<*, *, *>> = when (key) {
    null -> columns
    else -> columns.map { column ->
      KeyedField(
        field = column,
        name = column.name,
        key = key
      )
    }
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
    key = key
  )
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
 * current cursor row and is valid only during the call.
 */
sealed class SelectedRow(
  internal val outputs: SelectedOutputs
) {
  /**
   * Get the value of a selected expression.
   *
   * @param expression Selected expression or a column of selected `all()` columns. It must be the same instance that
   *     is selected
   * @return Value of the expression
   * @throws IllegalArgumentException if [expression] is not selected
   */
  abstract operator fun <T> get(expression: SqlExpression<*, *, T>): T
}

internal class SelectedOutputs(
  val expressions: List<SqlExpression<*, *, *>>
) {
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

  private val readers = Array(expressions.size) { expressions[it].selectRead }

  fun position(expression: SqlExpression<*, *, *>) = positions
    .getOrDefault(
      key = expression,
      defaultValue = -1
    )
    .also {
      require(it >= 0) {
        "$expression was not selected"
      }
    }

  fun ownedRow(cursor: Cursor, startIndex: Int): SelectedRow = OwnedSelectedRow(
    outputs = this,
    values = Array(readers.size) { index -> readers[index](cursor, startIndex + index) }
  )

  fun <M> liveRows(mapper: (SelectedRow) -> M): ExecutionReader<M> = ExecutionReader {
    val row = LiveSelectedRow(this)
    return@ExecutionReader { cursor, startIndex ->
      mapper(
        row.moveTo(
          cursor = cursor,
          startIndex = startIndex
        )
      )
    }
  }
}

internal class OwnedSelectedRow(
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

internal class LiveSelectedRow(outputs: SelectedOutputs) : SelectedRow(outputs) {
  private lateinit var cursor: Cursor
  private var startIndex = 0

  fun moveTo(cursor: Cursor, startIndex: Int) = apply {
    this.cursor = cursor
    this.startIndex = startIndex
  }

  override fun <T> get(expression: SqlExpression<*, *, T>) = expression.selectRead(
    cursor,
    startIndex + outputs.position(expression)
  )
}
