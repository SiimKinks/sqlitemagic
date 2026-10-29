@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.MutationNode
import com.siimkinks.sqlitemagic.internal.sql.compileMutation

/**
 * SQL UPDATE or DELETE statement.
 *
 * Statements are immutable and can be reused.
 */
sealed interface MutationQuery {
  /**
   * Configure this statement to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return SQL statement
   */
  @CheckResult
  fun usingConnection(connection: DbConnection): MutationQuery

  /**
   * Bind values of the statement parameters.
   *
   * Calling this method again replaces the previously bound values.
   *
   * Example:
   * ```
   * val name = AUTHOR.NAME.asParameter("name")
   * val rename = UPDATE TABLE AUTHOR SET (AUTHOR.NAME to name) WHERE (AUTHOR.ID IS 1L)
   * rename
   *     .bind(name TO "Ada")
   *     .execute()
   * ```
   *
   * @param assignments Values of all the statement parameters
   * @return SQL statement with the bound values
   */
  @CheckResult
  fun bind(vararg assignments: ParameterAssignment): MutationQuery

  /**
   * Compile this SQL statement.
   *
   * Result object is immutable and can be shared between threads without any side effects.
   *
   * @return Immutable compiled statement
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
   *     parameter, or the WHERE clause uses a column of another table outside a subquery
   */
  @CheckResult
  fun compile(): CompiledMutation

  /**
   * Compile and execute this statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Number of updated or deleted rows
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
   *     parameter, or the WHERE clause uses a column of another table outside a subquery
   */
  fun execute(): Int = compile().execute()
}

internal class MutationStatement(val node: MutationNode<*>) : MutationQuery {
  override fun usingConnection(connection: DbConnection) = MutationStatement(
    node.copy(connection = connection as DbConnectionImpl)
  )

  override fun bind(vararg assignments: ParameterAssignment) = MutationStatement(
    node.copy(parameterValues = assignments.asList())
  )

  override fun compile() = compileMutation(
    renderable = node,
    connection = node.connection,
    tables = arrayOf(node.head.table.name),
    parameterValues = node.parameterValues
  )
}

/**
 * Builder for SQL DELETE statement.
 *
 * Without WHERE clause all rows are deleted.
 */
class DeleteFromStage internal constructor(
  private val statement: MutationStatement
) : MutationQuery by statement {
  /**
   * Define SQL DELETE statement WHERE clause.
   *
   * @param predicate WHERE clause expression
   * @return SQL DELETE statement
   */
  @CheckResult
  infix fun WHERE(predicate: SqlExpression<Boolean, *, *>) = statement.node.filtered(predicate)
}
