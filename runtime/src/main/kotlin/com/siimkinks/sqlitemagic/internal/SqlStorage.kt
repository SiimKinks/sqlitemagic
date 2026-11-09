package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.NullAwareRead
import com.siimkinks.sqlitemagic.REQUIRED_SQL_NULL
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

@InternalSqliteMagicApi
class Storage<S : Any> internal constructor(
  internal val encode: (S) -> NativeValue,
  read: ValueRead<S>,
  internal val requiredRead: ValueRead<S>,
  internal val nullableRead: ValueRead<S?>? = null
) {
  internal val read = when (nullableRead) {
    null -> read
    else -> NullAwareRead(
      read = read,
      orNull = nullableRead
    )
  }
}

@InternalSqliteMagicApi
object SqlStorage {
  val LONG = Storage(
    encode = NativeValue::Integer,
    read = SQLiteStatement::getLong,
    requiredRead = SQLiteStatement::requiredLong
  )
  val INT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = SQLiteStatement::getInt,
    requiredRead = { statement, index -> statement.requiredLong(index).toInt() }
  )
  val SHORT = Storage(
    encode = { NativeValue.Integer(it.toLong()) },
    read = { statement, index -> statement.getLong(index).toShort() },
    requiredRead = { statement, index -> statement.requiredLong(index).toShort() }
  )
  val DOUBLE = Storage(
    encode = ::encodeReal,
    read = SQLiteStatement::getDouble,
    requiredRead = SQLiteStatement::requiredDouble
  )
  val FLOAT = Storage(
    encode = { encodeReal(it.toDouble()) },
    read = SQLiteStatement::getFloat,
    requiredRead = { statement, index -> statement.requiredDouble(index).toFloat() }
  )
  val STRING = Storage(
    encode = NativeValue::Text,
    read = SQLiteStatement::getText,
    requiredRead = SQLiteStatement::requiredText,
    nullableRead = SQLiteStatement::getTextOrNull
  )
  val BOOLEAN = Storage(
    encode = ::encodeBoolean,
    read = SQLiteStatement::getBoolean,
    requiredRead = { statement, index -> statement.requiredLong(index) != 0L }
  )
  val BYTE = Storage(
    encode = { NativeValue.Blob.owned(byteArrayOf(it)) },
    read = { statement, index -> statement.getBlob(index).first() },
    requiredRead = { statement, index -> statement.requiredBlob(index).first() }
  )
  val BYTE_ARRAY = Storage(
    encode = NativeValue.Blob::copyOf,
    read = SQLiteStatement::getBlob,
    requiredRead = SQLiteStatement::requiredBlob,
    nullableRead = SQLiteStatement::getBlobOrNull
  )
  val BOXED_BYTE_ARRAY = Storage(
    encode = { NativeValue.Blob.owned(it.toByteArray()) },
    read = { statement, index -> statement.getBlob(index).toTypedArray() },
    requiredRead = { statement, index -> statement.requiredBlob(index).toTypedArray() },
    nullableRead = { statement, index -> statement.getBlobOrNull(index)?.toTypedArray() }
  )
}

private fun SQLiteStatement.requiredLong(index: Int) = getRequiredLong(
  index = index,
  nullMessage = REQUIRED_SQL_NULL
)

private fun SQLiteStatement.requiredDouble(index: Int) = getRequiredDouble(
  index = index,
  nullMessage = REQUIRED_SQL_NULL
)

private fun SQLiteStatement.requiredText(index: Int) = getRequiredText(
  index = index,
  nullMessage = REQUIRED_SQL_NULL
)

private fun SQLiteStatement.requiredBlob(index: Int) = getRequiredBlob(
  index = index,
  nullMessage = REQUIRED_SQL_NULL
)

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
