package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.EncodingType
import com.siimkinks.sqlitemagic.NullAwareRead
import com.siimkinks.sqlitemagic.Required
import com.siimkinks.sqlitemagic.SqlNullability
import com.siimkinks.sqlitemagic.SqlNullable
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.requiredReader

@InternalSqliteMagicApi
class ParameterEncoding<in W, out R, out N : SqlNullability, out T> internal constructor(
  internal val encode: (W) -> NativeValue,
  internal val nonNullRead: (SQLiteStatement, Int) -> R,
  internal val selectRead: (SQLiteStatement, Int) -> T,
  internal val nullness: N
)

@InternalSqliteMagicApi
@JvmName("requiredEncodingParameterEncoding")
fun <W, R, RN : SqlNullability> EncodingType<W, R, Required, RN>.parameterEncoding() = requiredEncoding()

@InternalSqliteMagicApi
@JvmName("nullableEncodingParameterEncoding")
fun <W, R, RN : SqlNullability> EncodingType<W, R, SqlNullable, RN>.parameterEncoding() = nullableEncoding()

@InternalSqliteMagicApi
fun <H : Any, R, E : SqlNullability, RN : SqlNullability> EncodingType<H, R, E, RN>.nullBypassParameterEncoding() =
  nullBypassEncoding()

@InternalSqliteMagicApi
@Suppress("UNUSED_PARAMETER")
fun <W, R, S : Any, E : SqlNullability> requiredReadType(
  storage: Storage<S>,
  encode: (W) -> S?,
  decode: (S) -> R,
  encodingNullness: E
): EncodingType<W, R, E, Required> {
  val nonNullRead = storage.decodedRead(decode)
  return EncodingType(
    encode = storage.encoding(encode),
    nonNullRead = nonNullRead,
    selectRead = requiredReader(
      read = nonNullRead,
      checkedRead = { statement, index -> decode(storage.requiredRead(statement, index)) }
    ),
    readNullness = Required
  )
}

@InternalSqliteMagicApi
@Suppress("UNUSED_PARAMETER")
fun <W, R, S : Any, E : SqlNullability> nullableReadType(
  storage: Storage<S>,
  encode: (W) -> S?,
  decode: (S?) -> R,
  encodingNullness: E
): EncodingType<W, R, E, SqlNullable> = EncodingType(
  encode = storage.encoding(encode),
  nonNullRead = { statement, index ->
    decode(storage.read(statement, index))
  },
  selectRead = when (val readOrNull = storage.nullableRead) {
    null -> { statement, index ->
      decode(
        when {
          statement.isNull(index) -> null
          else -> storage.read(statement, index)
        }
      )
    }
    else -> { statement, index -> decode(readOrNull(statement, index)) }
  },
  readNullness = SqlNullable
)

private fun <S : Any, R> Storage<S>.decodedRead(decode: (S) -> R): (SQLiteStatement, Int) -> R {
  val decoded = { statement: SQLiteStatement, index: Int -> decode(read(statement, index)) }
  return when (val readOrNull = nullableRead) {
    null -> decoded
    else -> NullAwareRead(
      read = decoded,
      orNull = { statement, index -> readOrNull(statement, index)?.let(decode) }
    )
  }
}

private fun <W, S : Any> Storage<S>.encoding(toStorage: (W) -> S?): (W) -> NativeValue = {
  toStorage(it)?.let(encode) ?: NativeValue.Null
}
