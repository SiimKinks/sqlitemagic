@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

internal enum class SqlOperator(val sql: String) {
  EQUAL("="),
  NOT_EQUAL("!="),
  GREATER(">"),
  GREATER_OR_EQUAL(">="),
  LESS("<"),
  LESS_OR_EQUAL("<="),
  AND("AND"),
  OR("OR"),
  AND_NOT("AND NOT"),
  OR_NOT("OR NOT"),
  LIKE("LIKE"),
  NOT_LIKE("NOT LIKE"),
  GLOB("GLOB"),
  NOT_GLOB("NOT GLOB"),
  PLUS("+"),
  MINUS("-"),
  TIMES("*"),
  DIV("/"),
  REM("%"),
  CONCAT("||"),
  IN("IN"),
  NOT_IN("NOT IN")
}

private val predicateRead = BOOLEAN.nonNullRead
private val nullablePredicateRead = contextualRead(predicateRead)

private fun nullablePredicate(node: ExpressionNode) = SqlExpression(
  node = node,
  nonNullRead = predicateRead,
  selectRead = nullablePredicateRead,
  nullness = SqlNullable
)

private fun requiredPredicate(node: ExpressionNode) = SqlExpression(
  node = node,
  nonNullRead = predicateRead,
  selectRead = predicateRead,
  nullness = Required
)

internal fun binaryPredicate(
  left: ExpressionNode,
  operator: SqlOperator,
  right: ExpressionNode
) = nullablePredicate(
  ExpressionNode.Binary(
    left = left,
    operator = operator,
    right = right
  )
)

internal fun membership(
  operand: ExpressionNode,
  values: List<ExpressionNode>,
  negated: Boolean
) = binaryPredicate(
  left = operand,
  operator = inOperator(negated),
  right = ExpressionNode.Row(values)
)

internal fun hostMembership(
  operand: ExpressionNode,
  arguments: List<NativeValue>,
  negated: Boolean
) = nullablePredicate(
  ExpressionNode.HostMembership(
    operand = operand,
    arguments = arguments,
    negated = negated
  )
)

internal fun queryMembership(
  operand: ExpressionNode,
  query: SelectQuery<*, *>,
  negated: Boolean
) = binaryPredicate(
  left = operand,
  operator = inOperator(negated),
  right = ExpressionNode.Subquery(query.nestedNode())
)

private fun inOperator(negated: Boolean) = when {
  negated -> SqlOperator.NOT_IN
  else -> SqlOperator.IN
}

internal fun textValue(value: String) = ExpressionNode.Bound(NativeValue.Text(value))

internal fun integerValue(value: Long) = ExpressionNode.Bound(NativeValue.Integer(value))

internal fun nullTest(operand: ExpressionNode, negated: Boolean) = requiredPredicate(
  ExpressionNode.NullTest(
    operand = operand,
    negated = negated
  )
)

/**
 * This numeric expression = numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 * If either operand is NULL then the result is NULL. Use [SqlExpression.IS_NULL] to test for NULL.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericIs")
infix fun SqlExpression<Number?, *, *>.IS(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.EQUAL,
  right = other.node
)

/**
 * This numeric expression != numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 * If either operand is NULL then the result is NULL. Use [SqlExpression.IS_NOT_NULL] to test for not NULL.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericIsNot")
infix fun SqlExpression<Number?, *, *>.IS_NOT(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.NOT_EQUAL,
  right = other.node
)

/**
 * This numeric expression > numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericGreaterThan")
infix fun SqlExpression<Number?, *, *>.GREATER_THAN(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.GREATER,
  right = other.node
)

/**
 * This numeric expression >= numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericGreaterOrEqual")
infix fun SqlExpression<Number?, *, *>.GREATER_OR_EQUAL(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.GREATER_OR_EQUAL,
  right = other.node
)

/**
 * This numeric expression < numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericLessThan")
infix fun SqlExpression<Number?, *, *>.LESS_THAN(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.LESS,
  right = other.node
)

/**
 * This numeric expression <= numeric expression.
 *
 * Compares numeric expressions of different types, for example a `Long` column with an `Int` column.
 *
 * @param other The expression to test against this expression
 * @return Expression
 */
@JvmName("numericLessOrEqual")
infix fun SqlExpression<Number?, *, *>.LESS_OR_EQUAL(other: SqlExpression<Number?, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.LESS_OR_EQUAL,
  right = other.node
)

/**
 * Create an expression to check this numeric expression against a subquery of another numeric type.
 * Note that the subquery must select exactly one column.
 *
 * SQL: this IN (SELECT...)
 *
 * @param query The subquery to test against this expression
 * @return Expression
 */
@JvmName("numericInQuery")
infix fun SqlExpression<Number?, *, *>.IN(query: SelectQuery<*, out ScalarSelection<out Number?, *, *>>) =
  queryMembership(
    operand = node,
    query = query,
    negated = false
  )

/**
 * Create an expression to check this numeric expression against a subquery of another numeric type.
 * Note that the subquery must select exactly one column.
 *
 * SQL: this NOT IN (SELECT...)
 *
 * @param query The subquery to test against this expression
 * @return Expression
 */
@JvmName("numericNotInQuery")
infix fun SqlExpression<Number?, *, *>.NOT_IN(query: SelectQuery<*, out ScalarSelection<out Number?, *, *>>) =
  queryMembership(
    operand = node,
    query = query,
    negated = true
  )

/**
 * Create an expression to check whether a subquery returns any rows.
 *
 * SQL: EXISTS (SELECT...)
 *
 * @param query The subquery to check
 * @return Expression
 */
fun EXISTS(query: SelectQuery<*, *>) = existence(
  query = query,
  negated = false
)

/**
 * Create an expression to check whether a subquery returns no rows.
 *
 * SQL: NOT EXISTS (SELECT...)
 *
 * @param query The subquery to check
 * @return Expression
 */
fun NOT_EXISTS(query: SelectQuery<*, *>) = existence(
  query = query,
  negated = true
)

private fun existence(query: SelectQuery<*, *>, negated: Boolean) = requiredPredicate(
  ExpressionNode.Exists(
    select = query.nestedNode(),
    negated = negated
  )
)

/**
 * Create an expression to check this numeric expression against several numeric expressions of other types.
 *
 * SQL: this IN (values...)
 *
 * @param values The expressions to test against this expression
 * @return Expression
 */
@JvmName("numericIn")
fun SqlExpression<Number?, *, *>.IN(vararg values: SqlExpression<Number?, *, *>) = membership(
  operand = node,
  values = values.map(SqlExpression<*, *, *>::node),
  negated = false
)

/**
 * Create an expression to check this numeric expression against several numeric expressions of other types.
 *
 * SQL: this NOT IN (values...)
 *
 * @param values The expressions to test against this expression
 * @return Expression
 */
@JvmName("numericNotIn")
fun SqlExpression<Number?, *, *>.NOT_IN(vararg values: SqlExpression<Number?, *, *>) = membership(
  operand = node,
  values = values.map(SqlExpression<*, *, *>::node),
  negated = true
)

/**
 * Create a new builder for SQL BETWEEN operator with a lower bound of another numeric type.
 *
 * @param lower The lower bound of the BETWEEN operator
 * @return Between operator builder
 */
@JvmName("numericBetween")
infix fun SqlExpression<Number?, *, *>.BETWEEN(lower: SqlExpression<Number?, *, *>) = SqlRangeStart<Number?>(
  operand = node,
  lower = lower.node,
  negated = false
)

/**
 * Create a new builder for SQL NOT BETWEEN operator with a lower bound of another numeric type.
 *
 * @param lower The lower bound of the NOT BETWEEN operator
 * @return Between operator builder
 */
@JvmName("numericNotBetween")
infix fun SqlExpression<Number?, *, *>.NOT_BETWEEN(lower: SqlExpression<Number?, *, *>) = SqlRangeStart<Number?>(
  operand = node,
  lower = lower.node,
  negated = true
)

/**
 * Create a new builder for SQL BETWEEN operator with a lower bound of another numeric type.
 *
 * @param lower The lower bound of the BETWEEN operator
 * @return Between operator builder
 */
@JvmName("numericEncodableBetween")
infix fun <W> EncodableSqlExpression<W, Number?, *, *>.BETWEEN(lower: SqlExpression<Number?, *, *>) =
  EncodableSqlRangeStart(
    receiver = this,
    lower = lower.node,
    negated = false
  )

/**
 * Create a new builder for SQL NOT BETWEEN operator with a lower bound of another numeric type.
 *
 * @param lower The lower bound of the NOT BETWEEN operator
 * @return Between operator builder
 */
@JvmName("numericEncodableNotBetween")
infix fun <W> EncodableSqlExpression<W, Number?, *, *>.NOT_BETWEEN(lower: SqlExpression<Number?, *, *>) =
  EncodableSqlRangeStart(
    receiver = this,
    lower = lower.node,
    negated = true
  )

/**
 * Complete the BETWEEN operator with an upper bound of another numeric type.
 *
 * @param upper The upper bound of the BETWEEN operator
 * @return Expression
 */
@JvmName("numericAnd")
infix fun SqlRangeStart<Number?>.AND(upper: SqlExpression<Number?, *, *>) = range(upper.node)

/**
 * Builder for SQL BETWEEN operator.
 *
 * @param R Kotlin type of the bounds
 */
open class SqlRangeStart<out R> internal constructor(
  private val operand: ExpressionNode,
  private val lower: ExpressionNode,
  private val negated: Boolean
) {
  /**
   * Complete the BETWEEN operator.
   *
   * @param upper The upper bound of the BETWEEN operator
   * @return Expression
   */
  infix fun AND(upper: SqlExpression<@UnsafeVariance R?, *, *>) = range(upper.node)

  internal fun range(upper: ExpressionNode) = nullablePredicate(
    ExpressionNode.Range(
      operand = operand,
      lower = lower,
      upper = upper,
      negated = negated
    )
  )
}

/**
 * Builder for SQL BETWEEN operator that also takes a value as the upper bound.
 *
 * @param W Kotlin type of the values the operand can be compared with
 * @param R Kotlin type of the bounds
 */
class EncodableSqlRangeStart<W, out R> internal constructor(
  private val receiver: EncodableSqlExpression<W, R, *, *>,
  lower: ExpressionNode,
  negated: Boolean
) : SqlRangeStart<R>(
  operand = receiver.node,
  lower = lower,
  negated = negated
) {
  /**
   * Complete the BETWEEN operator.
   *
   * @param upper The upper bound of the BETWEEN operator
   * @return Expression
   */
  infix fun AND(upper: W & Any) = range(receiver.hostValue(upper))
}

/** SQL LIKE expression. */
class LikeSqlExpression internal constructor(
  private val like: ExpressionNode.Binary
) : SqlExpression<Boolean, SqlNullable, Boolean?>(
  node = like,
  nullness = SqlNullable,
  nonNullRead = predicateRead,
  selectRead = nullablePredicateRead
) {

  /**
   * Define an escape character for the LIKE pattern.
   *
   * Example:
   * ```
   * BOOK.TITLE LIKE "100!%%" ESCAPE "!"
   * ```
   *
   * @param escape The escape character
   * @return Expression
   */
  infix fun ESCAPE(escape: String) = nullablePredicate(
    ExpressionNode.Escape(
      pattern = like,
      escape = textValue(escape)
    )
  )
}

/**
 * Combine this expression with another one using the AND operator.
 *
 * Note that infix calls are grouped from left to right, as Kotlin groups them, regardless of SQL operator
 * precedence. Use parentheses to group conditions.
 *
 * @param other Expression to combine with
 * @return Combined expression
 */
infix fun SqlExpression<Boolean, *, *>.AND(other: SqlExpression<Boolean, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.AND,
  right = other.node
)

/**
 * Combine this expression with another one using the OR operator.
 *
 * Note that infix calls are grouped from left to right, as Kotlin groups them, regardless of SQL operator
 * precedence. Use parentheses to group conditions.
 *
 * @param other Expression to combine with
 * @return Combined expression
 */
infix fun SqlExpression<Boolean, *, *>.OR(other: SqlExpression<Boolean, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.OR,
  right = other.node
)

/**
 * Combine this expression with another one using the AND NOT operator combination.
 *
 * Note that infix calls are grouped from left to right, as Kotlin groups them, regardless of SQL operator
 * precedence. Use parentheses to group conditions.
 *
 * @param other Expression to combine with
 * @return Combined expression
 */
infix fun SqlExpression<Boolean, *, *>.AND_NOT(other: SqlExpression<Boolean, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.AND_NOT,
  right = other.node
)

/**
 * Combine this expression with another one using the OR NOT operator combination.
 *
 * Note that infix calls are grouped from left to right, as Kotlin groups them, regardless of SQL operator
 * precedence. Use parentheses to group conditions.
 *
 * @param other Expression to combine with
 * @return Combined expression
 */
infix fun SqlExpression<Boolean, *, *>.OR_NOT(other: SqlExpression<Boolean, *, *>) = binaryPredicate(
  left = node,
  operator = SqlOperator.OR_NOT,
  right = other.node
)

/**
 * Negate this expression.
 *
 * @return Negated expression
 */
operator fun SqlExpression<Boolean, *, *>.not() = nullablePredicate(ExpressionNode.Not(node))
