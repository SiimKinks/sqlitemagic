package com.siimkinks.sqlitemagic

import android.database.SQLException
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.sql.PreparedQuery

internal class QueryRows<T>(
  private val statement: SQLiteStatement,
  private val prepared: PreparedQuery,
  private val read: (SQLiteStatement, Int) -> T
) : Sequence<T> {
  private var open = true
  private var iterated = false

  fun <R> use(block: (Sequence<T>) -> R): R = try {
    block(this)
  } finally {
    open = false
  }

  override fun iterator(): Iterator<T> {
    checkRowsOpen(open)
    check(!iterated) { "Rows of useRows can be iterated only once" }
    iterated = true
    return RowIterator()
  }

  private inner class RowIterator : Iterator<T> {
    private var started = false
    private var stepped = false
    private var hasRow = false

    override fun hasNext(): Boolean {
      checkRowsOpen(open)
      if (!stepped) {
        hasRow = when {
          started -> statement.step()
          else -> prepared.firstStep(statement)
        }
        started = true
        stepped = true
      }
      return hasRow
    }

    override fun next(): T {
      if (!hasNext()) {
        throw NoSuchElementException()
      }
      stepped = false
      return read(statement, 0)
    }
  }
}

/**
 * Rows of a raw SQL query whose columns are known only when it runs.
 *
 * Move to each row with [step] and read its values with [read], using the same types as `readAs`. The rows are valid
 * only inside the `useRows` block that receives them, on the calling thread.
 *
 * Example:
 * ```
 * val columnNames = SELECT.RAW("PRAGMA table_info(book)").useRows { rows ->
 *   buildList {
 *     while (rows.step()) {
 *       add(rows.read(1, SqlType.STRING))
 *     }
 *   }
 * }
 * ```
 */
class RawRows internal constructor(private val statement: SQLiteStatement) {
  private var open = true
  private var done = false
  private var onRow = false
  private var started = false
  private var pending = false
  private var firstRow = false
  private var width = 0

  /**
   * Number of columns in each row.
   */
  val columnCount: Int
    get() {
      startedStatement()
      return width
    }

  /**
   * Move to the next row.
   *
   * @return `true` if the next row can be read, `false` after the last row
   */
  fun step(): Boolean {
    val statement = startedStatement()
    onRow = when {
      pending -> firstRow
      else -> !done && statement.step()
    }
    pending = false
    done = !onRow
    return onRow
  }

  /**
   * Get the name of a column.
   *
   * @param index Position of the column, starting from 0
   * @return Name of the column
   */
  fun columnName(index: Int): String = startedStatement().getColumnName(index)

  /**
   * Check whether a value of the current row is SQL NULL.
   *
   * @param index Position of the column, starting from 0
   * @return `true` if the value is SQL NULL
   * @throws SQLException if there is no current row or [index] is out of range
   */
  fun isNull(index: Int) = rowStatement(index).isNull(index)

  /**
   * Get the storage class of a value of the current row.
   *
   * SQLite stores each value with its own storage class, so values of one column can have different types.
   *
   * @param index Position of the column, starting from 0
   * @return One of [SQLITE_DATA_INTEGER], [SQLITE_DATA_FLOAT], [SQLITE_DATA_TEXT], [SQLITE_DATA_BLOB] and
   *     [SQLITE_DATA_NULL]
   * @throws SQLException if there is no current row or [index] is out of range
   * @see [SQLite documentation: Storage Classes and Datatypes](https://www.sqlite.org/datatype3.html)
   */
  fun columnType(index: Int) = rowStatement(index).getColumnType(index)

  /**
   * Read a value of the current row.
   *
   * Values are read as `readAs` reads them: a nullable type such as `SqlType.STRING.nullable` reads SQL NULL as
   * `null`, and a column reads the values it stores, decoded by its transformer.
   *
   * @param index Position of the column, starting from 0
   * @param type Type that reads the value
   * @return Value of the column
   * @throws SQLException if there is no current row, [index] is out of range, or [type] is not nullable and the
   *     value is SQL NULL
   */
  fun <T> read(index: Int, type: ReadContract<*, *, T>): T = type.readValue(
    statement = rowStatement(index),
    index = index
  )

  internal fun <R> use(block: (RawRows) -> R): R = try {
    block(this)
  } finally {
    open = false
  }

  private fun startedStatement(): SQLiteStatement {
    checkRowsOpen(open)
    if (!started) {
      started = true
      pending = true
      firstRow = statement.step()
      width = statement.getColumnCount()
    }
    return statement
  }

  private fun rowStatement(index: Int): SQLiteStatement {
    checkRowsOpen(open)
    when {
      !onRow -> throw SQLException("No current row")
      index !in 0..<width -> throw SQLException("Column index $index out of range")
    }
    return statement
  }
}

private fun checkRowsOpen(open: Boolean) = check(open) { "Rows can be read only inside their useRows block" }
