package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement

/**
 * Reads of TEXT and BLOB values that report SQL NULL as `null`.
 *
 * A [SQLiteStatement] implements this when its engine reads a value and its SQL NULL check in one call. Other
 * statements check [SQLiteStatement.isNull] before reading the value.
 */
@InternalSqliteMagicApi
interface NullableValueReads {
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
  this is NullableValueReads -> getTextOrNull(index)
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
  this is NullableValueReads -> getBlobOrNull(index)
  isNull(index) -> null
  else -> getBlob(index)
}
