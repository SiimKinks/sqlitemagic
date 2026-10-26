package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.Storage

/**
 * Built-in types of SQL values.
 *
 * Use them with raw SQL, `readAs` and `asValue`.
 *
 * Example:
 * ```
 * "count(*)".expr(SqlType.LONG)
 * ```
 */
object SqlType {
  /** `Long` values stored as INTEGER. */
  val LONG = builtinType(SqlStorage.LONG)

  /** `Int` values stored as INTEGER. */
  val INT = builtinType(SqlStorage.INT)

  /** `Short` values stored as INTEGER. */
  val SHORT = builtinType(SqlStorage.SHORT)

  /** `Double` values stored as REAL. */
  val DOUBLE = builtinType(SqlStorage.DOUBLE)

  /** `Float` values stored as REAL. */
  val FLOAT = builtinType(SqlStorage.FLOAT)

  /** `String` values stored as TEXT. */
  val STRING = builtinType(SqlStorage.STRING)

  /** `Boolean` values stored as INTEGER 0 or 1. */
  val BOOLEAN = builtinType(SqlStorage.BOOLEAN)

  /** `Byte` values stored as one byte BLOB. */
  val BYTE = builtinType(SqlStorage.BYTE)

  /** `ByteArray` values stored as BLOB. */
  val BYTE_ARRAY = builtinType(SqlStorage.BYTE_ARRAY)

  /** `Array<Byte>` values stored as BLOB. */
  val BOXED_BYTE_ARRAY = builtinType(SqlStorage.BOXED_BYTE_ARRAY)

  /**
   * Numeric values, read as `Long` for INTEGER and as `Double` for REAL values.
   *
   * It can only be used for reading values.
   */
  val NUMBER = ReadType(
    nonNullRead = ::readNumber,
    selectRead = ::readNumber,
    readNullness = Required
  )
}

private fun <T : Any> builtinType(storage: Storage<T>) = EncodingType<T, T, Required, Required>(
  encode = storage.encode,
  nonNullRead = storage.read,
  selectRead = requiredReader(storage.read),
  readNullness = Required
)

private fun readNumber(cursor: Cursor, index: Int): Number = when (cursor.getType(index)) {
  FIELD_TYPE_INTEGER -> cursor.getLong(index)
  FIELD_TYPE_FLOAT -> cursor.getDouble(index)
  FIELD_TYPE_NULL -> throw requiredSqlNull()
  else -> error("NUMBER requires an INTEGER or REAL SQL result")
}
