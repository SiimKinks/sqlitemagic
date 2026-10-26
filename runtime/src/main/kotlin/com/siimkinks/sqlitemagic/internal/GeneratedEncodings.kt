package com.siimkinks.sqlitemagic.internal

import android.database.Cursor
import com.siimkinks.sqlitemagic.EncodingType
import com.siimkinks.sqlitemagic.Required
import com.siimkinks.sqlitemagic.SqlNullability
import com.siimkinks.sqlitemagic.SqlNullable
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.requiredReader

@InternalSqliteMagicApi
class ParameterEncoding<in W, out R, out N : SqlNullability, out T> internal constructor(
  internal val encode: (W) -> NativeValue,
  internal val nonNullRead: (Cursor, Int) -> R,
  internal val selectRead: (Cursor, Int) -> T,
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
  val nonNullRead = { cursor: Cursor, index: Int -> decode(storage.read(cursor, index)) }
  return EncodingType(
    encode = storage.encoding(encode),
    nonNullRead = nonNullRead,
    selectRead = requiredReader(nonNullRead),
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
  nonNullRead = { cursor, index ->
    decode(storage.read(cursor, index))
  },
  selectRead = { cursor, index ->
    decode(
      when {
        cursor.isNull(index) -> null
        else -> storage.read(cursor, index)
      }
    )
  },
  readNullness = SqlNullable
)

private fun <W, S : Any> Storage<S>.encoding(toStorage: (W) -> S?): (W) -> NativeValue = {
  toStorage(it)?.let(encode) ?: NativeValue.Null
}
