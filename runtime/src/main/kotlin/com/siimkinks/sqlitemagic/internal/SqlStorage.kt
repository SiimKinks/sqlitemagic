package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

@InternalSqliteMagicApi
class Storage<S : Any> internal constructor(
  internal val encode: (S) -> NativeValue,
  internal val read: (SQLiteStatement, Int) -> S,
  internal val nullableRead: ((SQLiteStatement, Int) -> S?)? = null
)

@InternalSqliteMagicApi
object SqlStorage {
  val LONG = Storage(
    encode = NativeValue::Integer,
    read = SQLiteStatement::getLong
  )
  val INT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = SQLiteStatement::getInt
  )
  val SHORT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = { statement, index -> statement.getLong(index).toShort() }
  )
  val DOUBLE = Storage(
    encode = ::encodeReal,
    read = SQLiteStatement::getDouble
  )
  val FLOAT = Storage(
    encode = { encodeReal(it.toDouble()) },
    read = SQLiteStatement::getFloat
  )
  val STRING = Storage(
    encode = NativeValue::Text,
    read = SQLiteStatement::getText,
    nullableRead = SQLiteStatement::getTextOrNull
  )
  val BOOLEAN = Storage(
    encode = ::encodeBoolean,
    read = SQLiteStatement::getBoolean
  )
  val BYTE = Storage(
    encode = { NativeValue.Blob.owned(byteArrayOf(it)) },
    read = { statement, index -> statement.getBlob(index).first() }
  )
  val BYTE_ARRAY = Storage(
    encode = NativeValue.Blob::copyOf,
    read = SQLiteStatement::getBlob,
    nullableRead = SQLiteStatement::getBlobOrNull
  )
  val BOXED_BYTE_ARRAY = Storage(
    encode = { NativeValue.Blob.owned(it.toByteArray()) },
    read = { statement, index -> statement.getBlob(index).toTypedArray() },
    nullableRead = { statement, index -> statement.getBlobOrNull(index)?.toTypedArray() }
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
