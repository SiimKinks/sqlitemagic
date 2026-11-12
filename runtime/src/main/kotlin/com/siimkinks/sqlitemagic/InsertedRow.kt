package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.InsertedColumns

/**
 * Row of an INSERT statement that lists its columns with `COLUMNS(vararg columns)`.
 *
 * Assign a value to every listed column, for example `row[EVENT.KIND] = "tap"`. Assigning a column again replaces
 * the earlier value. The row is read when the `VALUES` call that created it returns.
 *
 * @param P Inserted table model type
 */
class InsertedRow<P> internal constructor(
  private val columns: InsertedColumns,
  count: Int
) {
  internal val values = arrayOfNulls<Any>(count * columns.size)
  private var offset = 0

  /**
   * Assign a value to a column.
   *
   * The value is converted to SQL in the same way as the column's other values, including the column's transformer.
   *
   * @param column Listed column to insert the value into
   * @param value Value to insert into [column]
   * @throws IllegalArgumentException if the statement does not list [column]
   */
  operator fun <W> set(column: Column<P, *, *, *, W, *, *>, value: W) {
    requireHostValue(value)
    values[offset + columns.position(column)] = column.encode(value)
  }

  /**
   * Assign an expression, such as a subquery, a parameter, a function or an `asValue` value, to a column.
   *
   * The expression has the value type of its column, with any nullability: NULL inserted into a NOT NULL column
   * fails with SQLite's constraint error. State the read type of a wider number with `readAs`, as in
   * `(BOOK.PAGES + 1).readAs(SqlType.INT)`.
   *
   * @param column Listed column to insert the expression's value into
   * @param value Expression whose value to insert into [column]
   * @throws IllegalArgumentException if the statement does not list [column]
   */
  operator fun <R> set(column: Column<P, R, *, *, *, *, *>, value: SqlExpression<R?, *, *>) {
    values[offset + columns.position(column)] = value.node
  }

  internal fun next() {
    repeat(columns.size) { index ->
      if (values[offset + index] == null) {
        values[offset + index] = requireNotNull(values[offset + columns.position(columns[index])]) {
          "INSERT row assigns no value to column ${columns[index].name}"
        }
      }
    }
    offset += columns.size
  }
}
