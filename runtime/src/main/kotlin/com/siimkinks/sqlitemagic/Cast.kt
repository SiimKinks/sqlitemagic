@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.SqlType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.NUMBER
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.numericValue

/**
 * Target type of the SQL CAST expression.
 *
 * @param C Kotlin type of the converted values
 */
class CastType<C> internal constructor(
  internal val sql: String,
  internal val read: (Cursor, Int) -> C,
  internal val encode: (C) -> NativeValue
) {
  internal val nullableRead = contextualRead(read)
}

/**
 * INTEGER type. Converted values are read as `Long`.
 */
val INTEGER = CastType(
  sql = "INTEGER",
  read = LONG.nonNullRead,
  encode = LONG.encode
)

/**
 * REAL type. Converted values are read as `Double`.
 */
val REAL = CastType(
  sql = "REAL",
  read = DOUBLE.nonNullRead,
  encode = DOUBLE.encode
)

/**
 * TEXT type. Converted values are read as `String`.
 */
val TEXT = CastType(
  sql = "TEXT",
  read = STRING.nonNullRead,
  encode = STRING.encode
)

/**
 * BLOB type. Converted values are read as `ByteArray`.
 */
val BLOB = CastType(
  sql = "BLOB",
  read = BYTE_ARRAY.nonNullRead,
  encode = BYTE_ARRAY.encode
)

/**
 * NUMERIC type. Converted values are read as `Long` or `Double`, as SQLite stores them, and compare with `Long`,
 * `Int`, `Short`, `Double`, `Float` and `Byte` values.
 */
val NUMERIC = CastType(
  sql = "NUMERIC",
  read = NUMBER.nonNullRead,
  encode = ::numericValue
)

/**
 * An expression and its target type in the SQL CAST expression.
 *
 * Create one with `expression AS type`, for example `BOOK.ID AS TEXT`.
 *
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param C Kotlin type of the converted values
 */
class CastTarget<out N : SqlNullability, C> internal constructor(
  internal val operand: SqlExpression<*, N, *>,
  internal val type: CastType<C>
)

/**
 * Create SQL CAST expression that converts a value to another type.
 *
 * The conversion is done by SQLite, for example `CAST('abc' AS INTEGER)` is 0. Converted values are read as
 * the target type, not through the transformer of the converted expression.
 *
 * Example:
 * ```
 * CAST(BOOK.ID AS TEXT)
 * ```
 *
 * @param target The expression and its target type
 * @return Expression representing the result of this conversion
 * @see [SQLite documentation: CAST expressions](https://www.sqlite.org/lang_expr.html#castexpr)
 */
@JvmName("requiredCast")
fun <C> CAST(target: CastTarget<Required, C>) = requiredNativeResult(
  node = castNode(target),
  type = target.type
)

/**
 * Create SQL CAST expression that converts a value to another type.
 *
 * The conversion is done by SQLite, for example `CAST('abc' AS INTEGER)` is 0. Converted values are read as
 * the target type, not through the transformer of the converted expression.
 *
 * Example:
 * ```
 * CAST(BOOK.ID AS TEXT)
 * ```
 *
 * @param target The expression and its target type
 * @return Expression representing the result of this conversion
 * @see [SQLite documentation: CAST expressions](https://www.sqlite.org/lang_expr.html#castexpr)
 */
fun <C> CAST(target: CastTarget<*, C>) = nullableNativeResult(
  node = castNode(target),
  type = target.type
)

private fun castNode(target: CastTarget<*, *>) = ExpressionNode.Cast(
  operand = target.operand.node,
  type = target.type.sql
)
