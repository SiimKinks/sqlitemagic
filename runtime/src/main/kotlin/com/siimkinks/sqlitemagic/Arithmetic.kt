package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqlOperator.DIV
import com.siimkinks.sqlitemagic.SqlOperator.MINUS
import com.siimkinks.sqlitemagic.SqlOperator.PLUS
import com.siimkinks.sqlitemagic.SqlOperator.REM
import com.siimkinks.sqlitemagic.SqlOperator.TIMES
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

private fun requiredArithmetic(node: ExpressionNode) = requiredNativeResult(
  node = node,
  type = NUMERIC
)

private fun nullableArithmetic(node: ExpressionNode) = nullableNativeResult(
  node = node,
  type = NUMERIC
)

/**
 * This expression + expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to add
 * @return Expression representing the result of this operation
 */
@JvmName("requiredPlus")
operator fun SqlExpression<Number?, Required, *>.plus(other: SqlExpression<Number?, Required, *>) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = PLUS,
    right = other.node
  )
)

/**
 * This expression + expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to add
 * @return Expression representing the result of this operation
 */
operator fun SqlExpression<Number?, *, *>.plus(other: SqlExpression<Number?, *, *>) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = PLUS,
    right = other.node
  )
)

/**
 * This expression - expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to subtract
 * @return Expression representing the result of this operation
 */
@JvmName("requiredMinus")
operator fun SqlExpression<Number?, Required, *>.minus(other: SqlExpression<Number?, Required, *>) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = MINUS,
    right = other.node
  )
)

/**
 * This expression - expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to subtract
 * @return Expression representing the result of this operation
 */
operator fun SqlExpression<Number?, *, *>.minus(other: SqlExpression<Number?, *, *>) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = MINUS,
    right = other.node
  )
)

/**
 * This expression * expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to multiply by
 * @return Expression representing the result of this operation
 */
@JvmName("requiredTimes")
operator fun SqlExpression<Number?, Required, *>.times(other: SqlExpression<Number?, Required, *>) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = TIMES,
    right = other.node
  )
)

/**
 * This expression * expression.
 *
 * Operands can be of any numeric types.
 *
 * @param other The expression to multiply by
 * @return Expression representing the result of this operation
 */
operator fun SqlExpression<Number?, *, *>.times(other: SqlExpression<Number?, *, *>) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = TIMES,
    right = other.node
  )
)

/**
 * This expression / expression.
 *
 * Operands can be of any numeric types. Integer operands are divided as integers. If the divisor is zero then the
 * result is NULL.
 *
 * @param other The expression to divide by
 * @return Expression representing the result of this operation
 */
operator fun SqlExpression<Number?, *, *>.div(other: SqlExpression<Number?, *, *>) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = DIV,
    right = other.node
  )
)

/**
 * This expression % expression.
 *
 * Operands can be of any numeric types. If the divisor is zero then the result is NULL.
 *
 * @param other The expression to divide by
 * @return Expression representing the result of this operation
 */
operator fun SqlExpression<Number?, *, *>.rem(other: SqlExpression<Number?, *, *>) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = REM,
    right = other.node
  )
)

/**
 * This expression + value.
 *
 * @param value The value to add
 * @return Expression representing the result of this operation
 */
@JvmName("requiredPlusValue")
operator fun <W> EncodableSqlExpression<W, Number?, Required, *>.plus(value: W & Any) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = PLUS,
    right = hostValue(value)
  )
)

/**
 * This expression + value.
 *
 * @param value The value to add
 * @return Expression representing the result of this operation
 */
@JvmName("plusValue")
operator fun <W> EncodableSqlExpression<W, Number?, *, *>.plus(value: W & Any) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = PLUS,
    right = hostValue(value)
  )
)

/**
 * This expression - value.
 *
 * @param value The value to subtract
 * @return Expression representing the result of this operation
 */
@JvmName("requiredMinusValue")
operator fun <W> EncodableSqlExpression<W, Number?, Required, *>.minus(value: W & Any) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = MINUS,
    right = hostValue(value)
  )
)

/**
 * This expression - value.
 *
 * @param value The value to subtract
 * @return Expression representing the result of this operation
 */
@JvmName("minusValue")
operator fun <W> EncodableSqlExpression<W, Number?, *, *>.minus(value: W & Any) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = MINUS,
    right = hostValue(value)
  )
)

/**
 * This expression * value.
 *
 * @param value The value to multiply by
 * @return Expression representing the result of this operation
 */
@JvmName("requiredTimesValue")
operator fun <W> EncodableSqlExpression<W, Number?, Required, *>.times(value: W & Any) = requiredArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = TIMES,
    right = hostValue(value)
  )
)

/**
 * This expression * value.
 *
 * @param value The value to multiply by
 * @return Expression representing the result of this operation
 */
@JvmName("timesValue")
operator fun <W> EncodableSqlExpression<W, Number?, *, *>.times(value: W & Any) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = TIMES,
    right = hostValue(value)
  )
)

/**
 * This expression / value.
 *
 * Integer operands are divided as integers. If the divisor is zero then the result is NULL.
 *
 * @param value The value to divide by
 * @return Expression representing the result of this operation
 */
@JvmName("divValue")
operator fun <W> EncodableSqlExpression<W, Number?, *, *>.div(value: W & Any) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = DIV,
    right = hostValue(value)
  )
)

/**
 * This expression % value.
 *
 * If the divisor is zero then the result is NULL.
 *
 * @param value The value to divide by
 * @return Expression representing the result of this operation
 */
@JvmName("remValue")
operator fun <W> EncodableSqlExpression<W, Number?, *, *>.rem(value: W & Any) = nullableArithmetic(
  ExpressionNode.Binary(
    left = node,
    operator = REM,
    right = hostValue(value)
  )
)

/**
 * Negate this expression.
 *
 * @return Negated expression
 */
@JvmName("requiredUnaryMinus")
operator fun SqlExpression<Number?, Required, *>.unaryMinus() = requiredArithmetic(ExpressionNode.Negate(node))

/**
 * Negate this expression.
 *
 * @return Negated expression
 */
operator fun SqlExpression<Number?, *, *>.unaryMinus() = nullableArithmetic(ExpressionNode.Negate(node))
