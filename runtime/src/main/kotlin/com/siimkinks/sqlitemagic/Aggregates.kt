@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.identity
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

/**
 * DISTINCT input of an aggregate function.
 *
 * @param E Input expression type
 */
class Distinct<out E : SqlExpression<*, *, *>> internal constructor(
  internal val value: E
)

/**
 * Remove duplicate values from the input of an aggregate function.
 *
 * Example:
 * ```
 * COUNT(DISTINCT(BOOK.AUTHOR))
 * ```
 *
 * @param value Input of the aggregate function
 * @return DISTINCT input of an aggregate function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun <E : SqlExpression<*, *, *>> DISTINCT(value: E) = Distinct(value)

/**
 * Result of an aggregate function.
 *
 * It can be followed by `FILTER_WHERE` and `OVER`, in this order.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class AggregateSqlExpression<W, out R, out N : SqlNullability, out T> internal constructor(
  private val aggregate: ExpressionNode.Aggregate,
  nullness: N,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  encode: (W) -> NativeValue
) : WindowableSqlExpression<W, R, N, T>(
  node = aggregate,
  nullness = nullness,
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  encode = encode
) {
  /**
   * Add a FILTER clause to this aggregate function.
   *
   * Only the rows for which [predicate] is true are aggregated. Requires SQLite 3.30.0 or newer.
   *
   * Example:
   * ```
   * COUNT() FILTER_WHERE (BOOK.PAGES GREATER_THAN 300L)
   * ```
   *
   * @param predicate The filter condition
   * @return Filtered aggregate function
   * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
   */
  infix fun FILTER_WHERE(predicate: SqlExpression<Boolean, *, *>) = WindowableSqlExpression(
    node = aggregate.filtered(predicate.node),
    nullness = nullness,
    nonNullRead = nonNullRead,
    selectRead = selectRead,
    encode = encode
  )
}

/**
 * Result of an aggregate function that can be used as a window function, for example one with a FILTER clause.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
open class WindowableSqlExpression<W, out R, out N : SqlNullability, out T> internal constructor(
  node: ExpressionNode,
  nullness: N,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  encode: (W) -> NativeValue
) : EncodableSqlExpression<W, R, N, T>(
  node = node,
  nullness = nullness,
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  encode = encode
) {
  /**
   * Use this aggregate function as a window function.
   *
   * Window functions require SQLite 3.25.0 or newer.
   *
   * Example:
   * ```
   * SUM(BOOK.PAGES) OVER PARTITION_BY(BOOK.AUTHOR)
   * ```
   *
   * @param window The window definition
   * @return Expression representing the result of this function
   * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
   */
  infix fun OVER(window: WindowSpec) = EncodableSqlExpression(
    node = ExpressionNode.Over(
      function = node,
      window = window
    ),
    nullness = nullness,
    nonNullRead = nonNullRead,
    selectRead = selectRead,
    encode = encode
  )

  /**
   * Use this aggregate function as a window function over all rows.
   *
   * SQL: OVER ()
   *
   * Window functions require SQLite 3.25.0 or newer.
   *
   * @return Expression representing the result of this function
   * @see [SQLite documentation: Window Functions](https://www.sqlite.org/windowfunctions.html)
   */
  fun OVER() = OVER(emptyWindow)
}

/**
 * The count(*) function returns the total number of rows in the group.
 *
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun COUNT() = requiredAggregate(
  node = aggregate(
    function = "COUNT",
    value = null,
    distinct = false
  ),
  type = INTEGER
)

/**
 * The count(X) function returns a count of the number of times that X is not NULL in a group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun COUNT(value: SqlExpression<*, *, *>) = requiredAggregate(
  node = aggregate(
    function = "COUNT",
    value = value,
    distinct = false
  ),
  type = INTEGER
)

/**
 * The count(DISTINCT X) function returns the number of distinct values of X that are not NULL.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun COUNT(value: Distinct<SqlExpression<*, *, *>>) = requiredAggregate(
  node = aggregate(
    function = "COUNT",
    value = value.value,
    distinct = true
  ),
  type = INTEGER
)

/**
 * The min() aggregate function returns the minimum non-NULL value of all values in the group.
 * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
 * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun <W, R> MIN(value: EncodableSqlExpression<W, R, *, *>) = extreme(
  function = "MIN",
  value = value,
  encode = value.encode,
  distinct = false
)

/**
 * The min() aggregate function returns the minimum non-NULL value of all values in the group.
 * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
 * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun <R> MIN(value: SqlExpression<R, *, *>) = extreme<Nothing, R>(
  function = "MIN",
  value = value,
  encode = ::identity,
  distinct = false
)

/**
 * The min() aggregate function returns the minimum value of distinct values of X.
 * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
 * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
@JvmName("minDistinctEncodable")
fun <W, R> MIN(value: Distinct<EncodableSqlExpression<W, R, *, *>>) = extreme(
  function = "MIN",
  value = value.value,
  encode = value.value.encode,
  distinct = true
)

/**
 * The min() aggregate function returns the minimum value of distinct values of X.
 * The minimum value is the first non-NULL value that would appear in an ORDER BY of the column.
 * Aggregate min() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
@JvmName("minDistinct")
fun <R> MIN(value: Distinct<SqlExpression<R, *, *>>) = extreme<Nothing, R>(
  function = "MIN",
  value = value.value,
  encode = ::identity,
  distinct = true
)

/**
 * The max() aggregate function returns the maximum value of all values in the group.
 * The maximum value is the value that would be returned last in an ORDER BY on the same column.
 * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun <W, R> MAX(value: EncodableSqlExpression<W, R, *, *>) = extreme(
  function = "MAX",
  value = value,
  encode = value.encode,
  distinct = false
)

/**
 * The max() aggregate function returns the maximum value of all values in the group.
 * The maximum value is the value that would be returned last in an ORDER BY on the same column.
 * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun <R> MAX(value: SqlExpression<R, *, *>) = extreme<Nothing, R>(
  function = "MAX",
  value = value,
  encode = ::identity,
  distinct = false
)

/**
 * The max() aggregate function returns the maximum value of distinct values of X.
 * The maximum value is the value that would be returned last in an ORDER BY on the same column.
 * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
@JvmName("maxDistinctEncodable")
fun <W, R> MAX(value: Distinct<EncodableSqlExpression<W, R, *, *>>) = extreme(
  function = "MAX",
  value = value.value,
  encode = value.value.encode,
  distinct = true
)

/**
 * The max() aggregate function returns the maximum value of distinct values of X.
 * The maximum value is the value that would be returned last in an ORDER BY on the same column.
 * Aggregate max() returns NULL if and only if there are no non-NULL values in the group.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
@JvmName("maxDistinct")
fun <R> MAX(value: Distinct<SqlExpression<R, *, *>>) = extreme<Nothing, R>(
  function = "MAX",
  value = value.value,
  encode = ::identity,
  distinct = true
)

/**
 * The avg() function returns the average value of all non-NULL X within a group.
 * String and BLOB values that do not look like numbers are interpreted as 0.
 *
 * The result of avg() is always a floating point value as long as there is at least
 * one non-NULL input even if all inputs are integers.
 * The result of avg() is NULL if there are no non-NULL inputs, or if very large values with differing signs make
 * the correct result undeterminable.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun AVG(value: SqlExpression<Number?, *, *>) = nullableAggregate(
  node = aggregate(
    function = "AVG",
    value = value,
    distinct = false
  ),
  type = REAL
)

/**
 * The avg() function returns the average value of distinct values of X.
 * String and BLOB values that do not look like numbers are interpreted as 0.
 *
 * The result of avg() is always a floating point value as long as there is at least
 * one non-NULL input even if all inputs are integers.
 * The result of avg() is NULL if there are no non-NULL inputs, or if very large values with differing signs make
 * the correct result undeterminable.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun AVG(value: Distinct<SqlExpression<Number?, *, *>>) = nullableAggregate(
  node = aggregate(
    function = "AVG",
    value = value.value,
    distinct = true
  ),
  type = REAL
)

/**
 * The sum() function returns the sum of all non-NULL values in the group.
 * If there are no non-NULL input rows then sum() returns NULL.
 *
 * The result of sum() is an integer value, read as `Long`, if all non-NULL inputs are integers, and a floating
 * point value, read as `Double`, otherwise. Sum() throws an "integer overflow" exception if all inputs are integers
 * or NULL and an integer overflow occurs. Use [TOTAL] to avoid the overflow.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun SUM(value: SqlExpression<Number?, *, *>) = nullableAggregate(
  node = aggregate(
    function = "SUM",
    value = value,
    distinct = false
  ),
  type = NUMERIC
)

/**
 * The sum() function returns the sum of distinct values of X.
 * If there are no non-NULL input rows then sum() returns NULL.
 *
 * The result of sum() is an integer value, read as `Long`, if all non-NULL inputs are integers, and a floating
 * point value, read as `Double`, otherwise. Sum() throws an "integer overflow" exception if all inputs are integers
 * or NULL and an integer overflow occurs. Use [TOTAL] to avoid the overflow.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun SUM(value: Distinct<SqlExpression<Number?, *, *>>) = nullableAggregate(
  node = aggregate(
    function = "SUM",
    value = value.value,
    distinct = true
  ),
  type = NUMERIC
)

/**
 * The total() function returns the sum of all non-NULL values in the group.
 * If there are no non-NULL input rows then total() returns 0.0.
 *
 * The result of total() is a floating point value. It is NULL only when very large values with differing signs make
 * the correct result undeterminable. This function never throws an integer overflow.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun TOTAL(value: SqlExpression<Number?, *, *>) = nullableAggregate(
  node = aggregate(
    function = "TOTAL",
    value = value,
    distinct = false
  ),
  type = REAL
)

/**
 * The total() function returns the sum of distinct values of X.
 * If there are no non-NULL input rows then total() returns 0.0.
 *
 * The result of total() is a floating point value. It is NULL only when very large values with differing signs make
 * the correct result undeterminable. This function never throws an integer overflow.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun TOTAL(value: Distinct<SqlExpression<Number?, *, *>>) = nullableAggregate(
  node = aggregate(
    function = "TOTAL",
    value = value.value,
    distinct = true
  ),
  type = REAL
)

/**
 * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
 * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun GROUP_CONCAT(value: SqlExpression<*, *, *>) = nullableAggregate(
  node = aggregate(
    function = "GROUP_CONCAT",
    value = value,
    distinct = false
  ),
  type = TEXT
)

/**
 * The group_concat() function returns a string which is the concatenation of all non-NULL values of X.
 * Parameter [separator] is used as the separator between instances of X.
 * The order of the concatenated elements is arbitrary.
 *
 * @param value Input of this aggregate function
 * @param separator Separator between instances of X
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun GROUP_CONCAT(value: SqlExpression<*, *, *>, separator: String) = nullableAggregate(
  node = ExpressionNode.Aggregate(
    function = "GROUP_CONCAT",
    arguments = listOf(value.node, textValue(separator)),
    distinct = false
  ),
  type = TEXT
)

/**
 * The group_concat() function returns a string which is the concatenation of distinct values of X.
 * A comma (",") is used as the separator. The order of the concatenated elements is arbitrary.
 *
 * @param value Input of this aggregate function
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Aggregate Functions](https://www.sqlite.org/lang_aggfunc.html)
 */
fun GROUP_CONCAT(value: Distinct<SqlExpression<*, *, *>>) = nullableAggregate(
  node = aggregate(
    function = "GROUP_CONCAT",
    value = value.value,
    distinct = true
  ),
  type = TEXT
)

private fun aggregate(
  function: String,
  value: SqlExpression<*, *, *>?,
  distinct: Boolean
) = ExpressionNode.Aggregate(
  function = function,
  arguments = listOfNotNull(value?.node),
  distinct = distinct
)

private fun <C> requiredAggregate(
  node: ExpressionNode.Aggregate,
  type: CastType<C>
) = AggregateSqlExpression(
  aggregate = node,
  nullness = Required,
  nonNullRead = type.read,
  selectRead = type.read,
  encode = type.encode
)

private fun <C> nullableAggregate(
  node: ExpressionNode.Aggregate,
  type: CastType<C>
) = AggregateSqlExpression(
  aggregate = node,
  nullness = SqlNullable,
  nonNullRead = type.read,
  selectRead = type.nullableRead,
  encode = type.encode
)

private fun <W, R> extreme(
  function: String,
  value: SqlExpression<R, *, *>,
  encode: (W) -> NativeValue,
  distinct: Boolean
) = AggregateSqlExpression(
  aggregate = aggregate(
    function = function,
    value = value,
    distinct = distinct
  ),
  nullness = SqlNullable,
  nonNullRead = value.nonNullRead,
  selectRead = contextualRead(value.nonNullRead),
  encode = encode
)
