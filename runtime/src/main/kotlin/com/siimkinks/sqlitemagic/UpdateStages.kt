@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.UpdateHead
import com.siimkinks.sqlitemagic.internal.sql.withAssignment

/**
 * Builder for SQL UPDATE statement.
 */
class UpdateConflictStage internal constructor(
  private val conflictAlgorithm: Int
) {
  /**
   * Define a table to update.
   *
   * @param table Table to update. This param must be one of annotation processor
   *              generated table objects that corresponds to table in a database
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [table] is aliased
   */
  @CheckResult
  infix fun <P> TABLE(table: Table<P>) = updateTable(
    table = table,
    conflictAlgorithm = conflictAlgorithm
  )
}

internal fun <P> updateTable(table: Table<P>, conflictAlgorithm: Int) = UpdateTableStage<P>(
  MutationNode.update(
    table = table,
    conflictAlgorithm = conflictAlgorithm
  )
)

/**
 * Builder for SQL UPDATE statement.
 *
 * @param P Updated table model type
 */
open class UpdateTableStage<P> internal constructor(
  internal val node: MutationNode<UpdateHead>
) {
  /**
   * Update a column with new value.
   *
   * The value is converted to SQL in the same way as the column's other values, including the column's transformer.
   *
   * @param column Column to update. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value A new value to set for updated column
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
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
   * Update a column with new value.
   *
   * The value is converted to SQL in the same way as the column's other values, including the column's transformer.
   *
   * Example:
   * ```
   * UPDATE TABLE AUTHOR SET (AUTHOR.NAME to "Ada")
   * ```
   *
   * @param assignment Column to update and a new value to set for it
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if the assigned column is not a column of the updated table, for example a
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
   * Update a not nullable column with the value of a not nullable expression, such as another column or a subquery.
   *
   * @param column Column to update. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value Expression whose value to assign to [column]
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
   *     aliased table
   */
  @CheckResult
  @JvmName("setRequiredExpression")
  fun <R> SET(column: Column<P, R, Required, *, *, *, *>, value: SqlExpression<R?, Required, *>) = assign(
    column = column,
    value = value.node
  )

  /**
   * Update a not nullable column with the value of a not nullable expression, such as another column or a subquery.
   *
   * @param assignment Column to update and expression whose value to assign to it
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if the assigned column is not a column of the updated table, for example a
   *     column of an aliased table
   */
  @CheckResult
  @JvmName("setRequiredExpressionPair")
  infix fun <R> SET(assignment: Pair<Column<P, R, Required, *, *, *, *>, SqlExpression<R?, Required, *>>) = assign(
    column = assignment.first,
    value = assignment.second.node
  )

  /**
   * Update a nullable column with the value of an expression, such as another column or a subquery.
   *
   * @param column Column to update. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @param value Expression whose value to assign to [column]
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
   *     aliased table
   */
  @CheckResult
  @JvmName("setNullableExpression")
  fun <R> SET(column: Column<P, R, SqlNullable, *, *, *, *>, value: SqlExpression<R?, *, *>) = assign(
    column = column,
    value = value.node
  )

  /**
   * Update a nullable column with the value of an expression, such as another column or a subquery.
   *
   * @param assignment Column to update and expression whose value to assign to it
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if the assigned column is not a column of the updated table, for example a
   *     column of an aliased table
   */
  @CheckResult
  @JvmName("setNullableExpressionPair")
  infix fun <R> SET(assignment: Pair<Column<P, R, SqlNullable, *, *, *, *>, SqlExpression<R?, *, *>>) = assign(
    column = assignment.first,
    value = assignment.second.node
  )

  /**
   * Update a column with NULL.
   *
   * NULL is set as is, without the column's transformer. For NOT NULL columns SQLite resolves the constraint
   * violation with the statement's conflict algorithm.
   *
   * @param column Column to update. This param must be one of annotation processor
   *               generated column objects that corresponds to column in a database table
   * @return SQL UPDATE statement builder
   * @throws IllegalArgumentException if [column] is not a column of the updated table, for example a column of an
   *     aliased table
   */
  @CheckResult
  infix fun SET_NULL(column: Column<P, *, *, *, *, *, *>) = assign(
    column = column,
    value = ExpressionNode.Literal(NativeValue.Null)
  )

  internal fun assign(column: Column<P, *, *, *, *, *, *>, value: ExpressionNode) = UpdateSetStage<P>(
    node.withAssignment(
      column = column,
      value = value
    )
  )

  private fun <W> hostValue(column: Column<P, *, *, *, W, *, *>, value: W): ExpressionNode {
    requireHostValue(value)
    return ExpressionNode.Bound(column.encode(value))
  }
}

/**
 * Builder for SQL UPDATE statement.
 *
 * Without WHERE clause all rows are updated.
 *
 * @param P Updated table model type
 */
class UpdateSetStage<P> internal constructor(
  node: MutationNode<UpdateHead>
) : UpdateTableStage<P>(node), MutationQuery by MutationStatement(node) {
  /**
   * Define SQL UPDATE statement WHERE clause.
   *
   * @param predicate WHERE clause expression
   * @return SQL UPDATE statement
   */
  @CheckResult
  infix fun WHERE(predicate: SqlExpression<Boolean, *, *>) = node.filtered(predicate)
}
