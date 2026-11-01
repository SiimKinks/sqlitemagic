package com.siimkinks.sqlitemagic.internal.sql

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.SqlStorage.BOOLEAN
import com.siimkinks.sqlitemagic.internal.SqlStorage.BYTE
import com.siimkinks.sqlitemagic.internal.SqlStorage.BYTE_ARRAY
import com.siimkinks.sqlitemagic.internal.SqlStorage.DOUBLE
import com.siimkinks.sqlitemagic.internal.SqlStorage.FLOAT
import com.siimkinks.sqlitemagic.internal.SqlStorage.INT
import com.siimkinks.sqlitemagic.internal.SqlStorage.LONG
import com.siimkinks.sqlitemagic.internal.SqlStorage.SHORT
import com.siimkinks.sqlitemagic.internal.SqlStorage.STRING

private const val HEX_DIGITS = "0123456789ABCDEF"

internal sealed interface NativeValue : QueryBinding {
  data object Null : NativeValue
  data class Integer(val value: Long) : NativeValue
  data class Real(val value: Double) : NativeValue
  data class Text(val value: String) : NativeValue

  class Blob private constructor(val value: ByteArray) : NativeValue {
    companion object {
      fun copyOf(value: ByteArray) = Blob(value.copyOf())

      fun owned(value: ByteArray) = Blob(value)
    }

    override fun equals(other: Any?) = other is Blob && value.contentEquals(other.value)

    override fun hashCode() = value.contentHashCode()
  }
}

internal fun nativeValue(value: Any?) = when (value) {
  null -> NativeValue.Null
  is Long -> LONG.encode(value)
  is Int -> INT.encode(value)
  is Short -> SHORT.encode(value)
  is Double -> DOUBLE.encode(value)
  is Float -> FLOAT.encode(value)
  is String -> STRING.encode(value)
  is Boolean -> BOOLEAN.encode(value)
  is Byte -> BYTE.encode(value)
  is ByteArray -> BYTE_ARRAY.encode(value)
  else -> throw IllegalArgumentException("Unsupported native SQL argument: ${value.javaClass.name}")
}

internal fun numericValue(value: Number) = when (value) {
  is Byte -> NativeValue.Integer(value.toLong())
  else -> nativeValue(value)
}

internal fun bindNativeValues(statement: SQLiteStatement, values: List<NativeValue>) {
  for (index in values.indices) {
    bindNativeValue(
      statement = statement,
      index = index + 1,
      value = values[index]
    )
  }
}

internal fun bindNativeValue(
  statement: SQLiteStatement,
  index: Int,
  value: NativeValue
) = when (value) {
  NativeValue.Null -> statement.bindNull(index)
  is NativeValue.Integer -> statement.bindLong(index, value.value)
  is NativeValue.Real -> statement.bindDouble(index, value.value)
  is NativeValue.Text -> statement.bindText(index, value.value)
  is NativeValue.Blob -> statement.bindBlob(index, value.value)
}

internal fun appendNativeLiteral(sql: StringBuilder, value: NativeValue) {
  when (value) {
    NativeValue.Null -> sql.append("NULL")
    is NativeValue.Integer -> sql.append(value.value)
    is NativeValue.Real -> sql.append(
      when (value.value) {
        Double.POSITIVE_INFINITY -> "9e999"
        Double.NEGATIVE_INFINITY -> "-9e999"
        else -> value.value.toString()
      }
    )
    is NativeValue.Text -> when {
      '\u0000' in value.value -> {
        sql.append("CAST(")
        appendBlobLiteral(
          sql = sql,
          bytes = value.value.toByteArray(Charsets.UTF_8)
        )
        sql.append(" AS TEXT)")
      }
      else -> {
        sql.append('\'')
        value.value.forEach { character ->
          sql.append(character)
          if (character == '\'') {
            sql.append('\'')
          }
        }
        sql.append('\'')
      }
    }
    is NativeValue.Blob -> appendBlobLiteral(
      sql = sql,
      bytes = value.value
    )
  }
}

private fun appendBlobLiteral(sql: StringBuilder, bytes: ByteArray) {
  sql.append("X'")
  bytes.forEach { byte ->
    val value = byte.toInt() and 0xff
    sql.append(HEX_DIGITS[value ushr 4])
    sql.append(HEX_DIGITS[value and 0x0f])
  }
  sql.append('\'')
}
