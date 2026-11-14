@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Builder step that assigns new values to columns of the target table with SET.
 *
 * @param P Target table model type
 * @param S Builder step that follows an assignment
 */
sealed class AssignmentStage<P, out S> {
  /**
   * Assign a new value to a column.
   *
   * The value is converted to SQL in the same way as the column's other values, including the column's transformer.
   *
   * @param column Column to assign. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value A new value to set for [column]
   * @return The next step of the statement
   * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
   *     aliased table
   */
  @CheckResult
  fun <W> SET(column: Column<P, *, *, *, W, *, *>, value: W) = assign(
    column = column,
    value = hostValue(
      column = column,
      value = value
    )
  )

  /**
   * Assign a new value to a column.
   *
   * The value is converted to SQL in the same way as the column's other values, including the column's transformer.
   *
   * Example:
   * ```
   * UPDATE TABLE AUTHOR SET (AUTHOR.NAME to "Ada")
   * ```
   *
   * @param assignment Column to assign and a new value to set for it
   * @return The next step of the statement
   * @throws IllegalArgumentException if the assigned column is not a column of the target table, for example a
   *     column of an aliased table
   */
  @CheckResult
  @JvmName("setValue")
  infix fun <W> SET(assignment: Pair<Column<P, *, *, *, W, *, *>, W>) = assign(
    column = assignment.first,
    value = hostValue(
      column = assignment.first,
      value = assignment.second
    )
  )

  /**
   * Assign the value of a not nullable expression, such as another column or a subquery, to a not nullable column.
   *
   * @param column Column to assign. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value Expression whose value to assign to [column]
   * @return The next step of the statement
   * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
   *     aliased table
   */
  @CheckResult
  @JvmName("setRequiredExpression")
  fun <R> SET(column: Column<P, R, Required, *, *, *, *>, value: SqlExpression<R?, Required, *>) = assign(
    column = column,
    value = value.node
  )

  /**
   * Assign the value of a not nullable expression, such as another column or a subquery, to a not nullable column.
   *
   * @param assignment Column to assign and expression whose value to assign to it
   * @return The next step of the statement
   * @throws IllegalArgumentException if the assigned column is not a column of the target table, for example a
   *     column of an aliased table
   */
  @CheckResult
  @JvmName("setRequiredExpressionPair")
  infix fun <R> SET(assignment: Pair<Column<P, R, Required, *, *, *, *>, SqlExpression<R?, Required, *>>) = assign(
    column = assignment.first,
    value = assignment.second.node
  )

  /**
   * Assign the value of an expression, such as another column or a subquery, to a nullable column.
   *
   * @param column Column to assign. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value Expression whose value to assign to [column]
   * @return The next step of the statement
   * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
   *     aliased table
   */
  @CheckResult
  @JvmName("setNullableExpression")
  fun <R> SET(column: Column<P, R, SqlNullable, *, *, *, *>, value: SqlExpression<R?, *, *>) = assign(
    column = column,
    value = value.node
  )

  /**
   * Assign the value of an expression, such as another column or a subquery, to a nullable column.
   *
   * @param assignment Column to assign and expression whose value to assign to it
   * @return The next step of the statement
   * @throws IllegalArgumentException if the assigned column is not a column of the target table, for example a
   *     column of an aliased table
   */
  @CheckResult
  @JvmName("setNullableExpressionPair")
  infix fun <R> SET(assignment: Pair<Column<P, R, SqlNullable, *, *, *, *>, SqlExpression<R?, *, *>>) = assign(
    column = assignment.first,
    value = assignment.second.node
  )

  /**
   * Assign NULL to a column.
   *
   * NULL is set as is, without the column's transformer. For a NOT NULL column, UPDATE fails with SQLite's NOT NULL
   * constraint error, except that `CONFLICT_IGNORE` skips the row and `CONFLICT_REPLACE` sets the column's default
   * value instead, failing when the column has none. DO UPDATE of an upsert clause always fails, whatever conflict
   * algorithm the INSERT statement has.
   *
   * @param column Column to assign. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @return The next step of the statement
   * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
   *     aliased table
   */
  @CheckResult
  infix fun SET_NULL(column: Column<P, *, *, *, *, *, *>) = assign(
    column = column,
    value = NULL.node
  )

  internal abstract fun assign(column: Column<P, *, *, *, *, *, *>, value: ExpressionNode): S

  private fun <W> hostValue(column: Column<P, *, *, *, W, *, *>, value: W): ExpressionNode {
    requireHostValue(value)
    return ExpressionNode.Bound(column.encode(value))
  }
}

/**
 * Assign the value of a not nullable numeric expression of any numeric type to a not nullable numeric column.
 *
 * @param column Column to assign. This param must be one of annotation processor
 *               generated column objects that corresponds to column in a database table
 * @param value Expression whose value to assign to [column]
 * @return The next step of the statement
 * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
 *     aliased table
 */
@CheckResult
@JvmName("setRequiredNumber")
fun <P, S> AssignmentStage<P, S>.SET(
  column: Column<P, out Number?, Required, *, *, *, *>,
  value: SqlExpression<Number?, Required, *>
) = assign(
  column = column,
  value = value.node
)

/**
 * Assign the value of a not nullable numeric expression of any numeric type to a not nullable numeric column.
 *
 * Example:
 * ```
 * UPDATE TABLE BOOK SET (BOOK.PAGES to BOOK.PAGES + 1)
 * ```
 *
 * @param assignment Column to assign and expression whose value to assign to it
 * @return The next step of the statement
 * @throws IllegalArgumentException if the assigned column is not a column of the target table, for example a
 *     column of an aliased table
 */
@CheckResult
@JvmName("setRequiredNumberPair")
infix fun <P, S> AssignmentStage<P, S>.SET(
  assignment: Pair<Column<P, out Number?, Required, *, *, *, *>, SqlExpression<Number?, Required, *>>
) = assign(
  column = assignment.first,
  value = assignment.second.node
)

/**
 * Assign the value of a numeric expression of any numeric type to a nullable numeric column.
 *
 * @param column Column to assign. This param must be one of annotation processor
 *               generated column objects that corresponds to column in a database table
 * @param value Expression whose value to assign to [column]
 * @return The next step of the statement
 * @throws IllegalArgumentException if [column] is not a column of the target table, for example a column of an
 *     aliased table
 */
@CheckResult
@JvmName("setNullableNumber")
fun <P, S> AssignmentStage<P, S>.SET(
  column: Column<P, out Number?, SqlNullable, *, *, *, *>,
  value: SqlExpression<Number?, *, *>
) = assign(
  column = column,
  value = value.node
)

/**
 * Assign the value of a numeric expression of any numeric type to a nullable numeric column.
 *
 * @param assignment Column to assign and expression whose value to assign to it
 * @return The next step of the statement
 * @throws IllegalArgumentException if the assigned column is not a column of the target table, for example a
 *     column of an aliased table
 */
@CheckResult
@JvmName("setNullableNumberPair")
infix fun <P, S> AssignmentStage<P, S>.SET(
  assignment: Pair<Column<P, out Number?, SqlNullable, *, *, *, *>, SqlExpression<Number?, *, *>>
) = assign(
  column = assignment.first,
  value = assignment.second.node
)
