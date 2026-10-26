package com.siimkinks.sqlitemagic

import android.database.Cursor
import android.database.SQLException
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

/**
 * Nullability of SQL values -- either [Required] or [SqlNullable].
 */
sealed interface SqlNullability

/**
 * SQL values that are never NULL.
 */
data object Required : SqlNullability

/**
 * SQL values that can be NULL.
 */
data object SqlNullable : SqlNullability

/**
 * Type of selected values: a type such as `SqlType.LONG`, or a column whose values are read the same way.
 *
 * @param R Kotlin type of the values
 * @param N Nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the value is selected)
 */
sealed interface ReadContract<out R, out N : SqlNullability, out T>

@Suppress("UNCHECKED_CAST")
internal val <T> ReadContract<*, *, T>.columnRead
  get() = when (this) {
    is ReadType -> selectRead
    is Column<*, *, *, *, *, *, *> -> selectRead as (Cursor, Int) -> T
  }

/**
 * Type of SQL values, which defines how they are read.
 *
 * @param R Kotlin type of the values
 * @param N Nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the value is selected)
 */
open class ReadType<out R, out N : SqlNullability, out T> internal constructor(
  internal val nonNullRead: (Cursor, Int) -> R,
  internal val selectRead: (Cursor, Int) -> T,
  internal val readNullness: N
) : ReadContract<R, N, T> {
  /** A racing first use may build an equal immutable copy; final fields make either safe to publish. */
  private var nullableForm: ReadType<R, SqlNullable, R?>? = null

  /**
   * Nullable version of this type, which reads SQL NULL values as `null`.
   */
  open val nullable: ReadType<R, SqlNullable, R?>
    get() = nullableForm ?: ReadType(
      nonNullRead = nonNullRead,
      selectRead = contextualRead(nonNullRead),
      readNullness = SqlNullable
    ).also { nullableForm = it }
}

/**
 * Type of SQL values, which defines how they are read and how Kotlin values are converted to SQL.
 *
 * @param W Kotlin type of the values converted to SQL
 * @param R Kotlin type of the read values
 * @param N Nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the value is selected)
 */
open class EncodableReadType<W, out R, out N : SqlNullability, out T> internal constructor(
  internal val encode: (W) -> NativeValue,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  readNullness: N
) : ReadType<R, N, T>(
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  readNullness = readNullness
) {
  private var encodableNullableForm: EncodableReadType<W, R, SqlNullable, R?>? = null

  /**
   * Nullable version of this type, which reads SQL NULL values as `null`.
   */
  override val nullable: EncodableReadType<W, R, SqlNullable, R?>
    get() = encodableNullableForm ?: EncodableReadType(
      encode = encode,
      nonNullRead = nonNullRead,
      selectRead = contextualRead(nonNullRead),
      readNullness = SqlNullable
    ).also { encodableNullableForm = it }
}

/**
 * Type of SQL values that can be bound as query arguments, for example [SqlType.STRING] or a generated transformer
 * type.
 *
 * @param W Kotlin type of the values converted to SQL
 * @param R Kotlin type of the read values
 * @param E Nullability of the converted values -- either [Required] or [SqlNullable]
 * @param N Nullability of the read values -- either [Required] or [SqlNullable]
 */
class EncodingType<W, out R, out E : SqlNullability, out N : SqlNullability> internal constructor(
  encode: (W) -> NativeValue,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> R,
  readNullness: N
) : EncodableReadType<W, R, N, R>(
  encode = encode,
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  readNullness = readNullness
) {
  private var requiredEncodingCache: ParameterEncoding<W, R, Required, R>? = null
  private var nullableEncodingCache: ParameterEncoding<W, R, SqlNullable, R?>? = null
  private var nullBypassEncodingCache: ParameterEncoding<W?, R, SqlNullable, R?>? = null

  internal fun requiredEncoding() = requiredEncodingCache ?: ParameterEncoding(
    encode = encode,
    nonNullRead = nonNullRead,
    selectRead = selectRead,
    nullness = Required
  ).also { requiredEncodingCache = it }

  internal fun nullableEncoding() = nullableEncodingCache ?: ParameterEncoding(
    encode = encode,
    nonNullRead = nonNullRead,
    selectRead = contextualRead(nonNullRead),
    nullness = SqlNullable
  ).also { nullableEncodingCache = it }

  internal fun nullBypassEncoding() = nullBypassEncodingCache ?: ParameterEncoding(
    encode = { value: W? -> value?.let(encode) ?: NativeValue.Null },
    nonNullRead = nonNullRead,
    selectRead = contextualRead(nonNullRead),
    nullness = SqlNullable
  ).also { nullBypassEncodingCache = it }
}

internal fun requiredSqlNull() = SQLException("Required SQL result returned SQL NULL")

internal fun <T> requiredReader(read: (Cursor, Int) -> T): (Cursor, Int) -> T = { cursor, index ->
  if (cursor.isNull(index)) {
    throw requiredSqlNull()
  }
  read(cursor, index)
}

internal fun <R> contextualRead(nonNullRead: (Cursor, Int) -> R): (Cursor, Int) -> R? = { cursor, index ->
  when {
    cursor.isNull(index) -> null
    else -> nonNullRead(cursor, index)
  }
}
