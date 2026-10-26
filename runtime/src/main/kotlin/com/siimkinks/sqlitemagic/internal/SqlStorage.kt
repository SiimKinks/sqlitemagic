package com.siimkinks.sqlitemagic.internal

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

@InternalSqliteMagicApi
class Storage<S : Any> internal constructor(
  internal val encode: (S) -> NativeValue,
  internal val read: (Cursor, Int) -> S
)

@InternalSqliteMagicApi
object SqlStorage {
  val LONG = Storage(
    encode = NativeValue::Integer,
    read = Cursor::getLong
  )
  val INT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = Cursor::getInt
  )
  val SHORT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = Cursor::getShort
  )
  val DOUBLE = Storage(
    encode = ::encodeReal,
    read = Cursor::getDouble
  )
  val FLOAT = Storage(
    encode = { encodeReal(it.toDouble()) },
    read = Cursor::getFloat
  )
  val STRING = Storage(
    encode = NativeValue::Text,
    read = Cursor::getString
  )
  val BOOLEAN = Storage(
    encode = ::encodeBoolean,
    read = { cursor, index -> cursor.getInt(index) != 0 }
  )
  val BYTE = Storage(
    encode = { NativeValue.Blob.owned(byteArrayOf(it)) },
    read = { cursor, index -> cursor.getBlob(index).first() }
  )
  val BYTE_ARRAY = Storage(
    encode = NativeValue.Blob::copyOf,
    read = Cursor::getBlob
  )
  val BOXED_BYTE_ARRAY = Storage(
    encode = { NativeValue.Blob.owned(it.toByteArray()) },
    read = { cursor, index -> cursor.getBlob(index).toTypedArray() }
  )
}

private fun encodeReal(value: Double): NativeValue.Real {
  require(!value.isNaN()) { "NaN cannot be encoded as a required SQL REAL value" }
  return NativeValue.Real(value)
}

private fun encodeBoolean(value: Boolean) = NativeValue.Integer(
  when {
    value -> 1L
    else -> 0L
  }
)
