package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.BYTE
import com.siimkinks.sqlitemagic.SqlType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.FLOAT
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.SHORT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

private fun <W, R, N : SqlNullability, T> valueExpression(
  value: W,
  encoding: ParameterEncoding<W, R, N, T>,
  node: (NativeValue) -> ExpressionNode
): EncodedSqlExpression<W, R, N, T> {
  requireHostValue(value)
  return EncodedSqlExpression(
    node = node(encoding.encode(value)),
    parameterEncoding = encoding
  )
}

@InternalSqliteMagicApi
fun <W, R, N : SqlNullability, T> W.asValue(
  encoding: ParameterEncoding<W, R, N, T>
): EncodedSqlExpression<W, R, N, T> = valueExpression(
  value = this,
  encoding = encoding,
  node = ExpressionNode::Bound
)

/**
 * Bind this value as a query argument.
 *
 * The value is converted to SQL with [type], for example a generated transformer type.
 *
 * Example:
 * ```
 * ExternalId("customer-42").asValue(SqlType.EXTERNAL_ID)
 * ```
 *
 * @param type Type of this value
 * @return Expression representing this value
 */
@JvmName("requiredSqlValue")
fun <W, R> W.asValue(type: EncodingType<W, R, Required, *>) = valueExpression(
  value = this,
  encoding = type.parameterEncoding(),
  node = ExpressionNode::Bound
)

/**
 * Bind this value as a query argument.
 *
 * The value is converted to SQL with [type], for example a generated transformer type.
 *
 * Example:
 * ```
 * ExternalId("customer-42").asValue(SqlType.EXTERNAL_ID)
 * ```
 *
 * @param type Type of this value
 * @return Expression representing this value
 */
@JvmName("nullableEncodingSqlValue")
fun <W, R> W.asValue(type: EncodingType<W, R, SqlNullable, *>) = valueExpression(
  value = this,
  encoding = type.parameterEncoding(),
  node = ExpressionNode::Bound
)

/**
 * Inline this value into the SQL as a literal.
 *
 * The value is converted to SQL with [type], for example a generated transformer type.
 *
 * Example:
 * ```
 * ExternalId("customer-42").asLiteral(SqlType.EXTERNAL_ID)
 * ```
 *
 * @param type Type of this value
 * @return Expression representing this value
 */
@JvmName("requiredSqlLiteral")
fun <W, R> W.asLiteral(type: EncodingType<W, R, Required, *>) = valueExpression(
  value = this,
  encoding = type.parameterEncoding(),
  node = ExpressionNode::Literal
)

/**
 * Inline this value into the SQL as a literal.
 *
 * The value is converted to SQL with [type], for example a generated transformer type.
 *
 * Example:
 * ```
 * ExternalId("customer-42").asLiteral(SqlType.EXTERNAL_ID)
 * ```
 *
 * @param type Type of this value
 * @return Expression representing this value
 */
@JvmName("nullableEncodingSqlLiteral")
fun <W, R> W.asLiteral(type: EncodingType<W, R, SqlNullable, *>) = valueExpression(
  value = this,
  encoding = type.parameterEncoding(),
  node = ExpressionNode::Literal
)

/** Bind this value as a query argument. */
fun Long.asValue() = asValue(LONG)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Long?.asValue() = asValue(LONG.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Int.asValue() = asValue(INT)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Int?.asValue() = asValue(INT.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Short.asValue() = asValue(SHORT)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Short?.asValue() = asValue(SHORT.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Double.asValue() = asValue(DOUBLE)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Double?.asValue() = asValue(DOUBLE.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Float.asValue() = asValue(FLOAT)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Float?.asValue() = asValue(FLOAT.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun String.asValue() = asValue(STRING)

/** Bind this value as a query argument; `null` is bound as NULL. */
@JvmName("nullableStringSqlValue")
fun String?.asValue() = asValue(STRING.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Boolean.asValue() = asValue(BOOLEAN)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Boolean?.asValue() = asValue(BOOLEAN.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun Byte.asValue() = asValue(BYTE)

/** Bind this value as a query argument; `null` is bound as NULL. */
fun Byte?.asValue() = asValue(BYTE.nullBypassParameterEncoding())

/** Bind this value as a query argument. */
fun ByteArray.asValue() = asValue(BYTE_ARRAY)

/** Bind this value as a query argument; `null` is bound as NULL. */
@JvmName("nullableByteArraySqlValue")
fun ByteArray?.asValue() = asValue(BYTE_ARRAY.nullBypassParameterEncoding())

/** Inline this value into the SQL as a literal. */
fun Long.asLiteral() = asLiteral(LONG)

/** Inline this value into the SQL as a literal. */
fun Int.asLiteral() = asLiteral(INT)

/** Inline this value into the SQL as a literal. */
fun Short.asLiteral() = asLiteral(SHORT)

/** Inline this value into the SQL as a literal. */
fun Double.asLiteral() = asLiteral(DOUBLE)

/** Inline this value into the SQL as a literal. */
fun Float.asLiteral() = asLiteral(FLOAT)

/** Inline this value into the SQL as a literal. */
fun String.asLiteral() = asLiteral(STRING)

/** Inline this value into the SQL as a literal. */
fun Boolean.asLiteral() = asLiteral(BOOLEAN)

/** Inline this value into the SQL as a literal. */
fun Byte.asLiteral() = asLiteral(BYTE)

/** Inline this value into the SQL as a literal. */
fun ByteArray.asLiteral() = asLiteral(BYTE_ARRAY)

private val nullRead: (Cursor, Int) -> Nothing? = { _, _ -> null }

/**
 * SQL NULL literal.
 */
val NULL: SqlExpression<Nothing?, SqlNullable, Nothing?> = SqlExpression(
  node = ExpressionNode.Literal(NativeValue.Null),
  nonNullRead = nullRead,
  selectRead = nullRead,
  nullness = SqlNullable
)
