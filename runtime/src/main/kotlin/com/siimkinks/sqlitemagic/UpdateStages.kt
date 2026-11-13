@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
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
) : AssignmentStage<P, UpdateSetStage<P>>() {
  override fun assign(column: Column<P, *, *, *, *, *, *>, value: ExpressionNode) = UpdateSetStage<P>(
    node.withAssignment(
      column = column,
      value = value
    )
  )
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
