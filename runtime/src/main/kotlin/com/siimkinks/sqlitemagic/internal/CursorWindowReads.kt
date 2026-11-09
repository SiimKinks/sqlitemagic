package com.siimkinks.sqlitemagic.internal

import android.database.SQLException
import androidx.sqlite.SQLiteStatement

/**
 * Reads of a [SQLiteStatement] whose engine copies the rows of an execution into a window before they are read.
 *
 * Such a statement reads a value and its SQL NULL check in one call and knows its row count once it has stepped to
 * its first row. Other statements check [SQLiteStatement.isNull] before reading the value.
 */
@InternalSqliteMagicApi
interface CursorWindowReads {
  /**
   * Number of rows the current execution returns, valid after [SQLiteStatement.step] returned `true`.
   */
  val rowCount: Int

  /**
   * Read a TEXT value of the current row.
   *
   * @param index Position of the column, starting from 0
   * @return Value of the column, or `null` for SQL NULL
   */
  fun getTextOrNull(index: Int): String?

  /**
   * Read a BLOB value of the current row.
   *
   * @param index Position of the column, starting from 0
   * @return Value of the column, or `null` for SQL NULL
   */
  fun getBlobOrNull(index: Int): ByteArray?
}

/**
 * Read a TEXT value of the current row.
 *
 * @param index Position of the column, starting from 0
 * @return Value of the column, or `null` for SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getTextOrNull(index: Int) = when {
  this is CursorWindowReads -> getTextOrNull(index)
  isNull(index) -> null
  else -> getText(index)
}

/**
 * Read a BLOB value of the current row.
 *
 * @param index Position of the column, starting from 0
 * @return Value of the column, or `null` for SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getBlobOrNull(index: Int) = when {
  this is CursorWindowReads -> getBlobOrNull(index)
  isNull(index) -> null
  else -> getBlob(index)
}

/**
 * Read an INTEGER value of the current row. Only a zero or SQL NULL costs a further SQL NULL check, which suits columns
 * that rarely hold either.
 *
 * @param index Position of the column, starting from 0
 * @return Value of the column, or `null` for SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getLongOrNull(index: Int): Long? {
  val value = getLong(index)
  return when {
    value == 0L && isNull(index) -> null
    else -> value
  }
}

/**
 * Read an INTEGER value of a required column of the current row. Only a zero costs a further SQL NULL check.
 *
 * @param index Position of the column, starting from 0
 * @param nullMessage Message of the failure when the value is SQL NULL
 * @return Value of the column
 * @throws SQLException if the value is SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getRequiredLong(index: Int, nullMessage: String): Long {
  val value = getLong(index)
  if (value == 0L && isNull(index)) {
    throw SQLException(nullMessage)
  }
  return value
}

/**
 * Read a REAL value of a required column of the current row. Only a zero costs a further SQL NULL check.
 *
 * @param index Position of the column, starting from 0
 * @param nullMessage Message of the failure when the value is SQL NULL
 * @return Value of the column
 * @throws SQLException if the value is SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getRequiredDouble(index: Int, nullMessage: String): Double {
  val value = getDouble(index)
  if (value == 0.0 && isNull(index)) {
    throw SQLException(nullMessage)
  }
  return value
}

/**
 * Read a TEXT value of a required column of the current row.
 *
 * @param index Position of the column, starting from 0
 * @param nullMessage Message of the failure when the value is SQL NULL
 * @return Value of the column
 * @throws SQLException if the value is SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getRequiredText(index: Int, nullMessage: String) =
  getTextOrNull(index) ?: throw SQLException(nullMessage)

/**
 * Read a BLOB value of a required column of the current row.
 *
 * @param index Position of the column, starting from 0
 * @param nullMessage Message of the failure when the value is SQL NULL
 * @return Value of the column
 * @throws SQLException if the value is SQL NULL
 */
@InternalSqliteMagicApi
fun SQLiteStatement.getRequiredBlob(index: Int, nullMessage: String) =
  getBlobOrNull(index) ?: throw SQLException(nullMessage)
