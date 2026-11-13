package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.InsertHead
import com.siimkinks.sqlitemagic.internal.sql.MutationNode

/**
 * SQL INSERT statement that inserts at most one row and reads the row ID of the inserted row.
 *
 * Statements are immutable and can be reused.
 */
class RowIdInsertQuery internal constructor(
  private val node: MutationNode<InsertHead>
) {
  /**
   * Configure this statement to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return SQL statement
   */
  @CheckResult
  fun usingConnection(connection: DbConnection) = RowIdInsertQuery(
    node.copy(connection = connection as DbConnectionImpl)
  )

  /**
   * Bind values of the statement parameters.
   *
   * Calling this method again replaces the previously bound values.
   *
   * Example:
   * ```
   * val name = AUTHOR.NAME.asParameter("name")
   * val insert = INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES name
   * insert
   *     .readRowId()
   *     .bind(name TO "Ada")
   *     .execute()
   * ```
   *
   * @param assignments Values of all the statement parameters
   * @return SQL statement with the bound values
   */
  @CheckResult
  fun bind(vararg assignments: ParameterAssignment) = RowIdInsertQuery(
    node.copy(parameterValues = assignments.asList())
  )

  /**
   * Compile this SQL statement.
   *
   * Result object is immutable and can be shared between threads without any side effects.
   *
   * @return Immutable compiled statement
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
   *     parameter, or the statement uses a column outside a subquery in VALUES
   */
  @CheckResult
  fun compile() = CompiledRowIdInsert(node.compile())

  /**
   * Compile and execute this statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Row ID of the inserted row, or `null` when no row was inserted, such as a row that `CONFLICT_IGNORE`
   *     ignored
   * @throws IllegalArgumentException if a parameter value is missing or duplicated, the statement has no such
   *     parameter, or the statement uses a column outside a subquery in VALUES
   */
  fun execute(): Long? = compile().execute()
}

/**
 * Compiled SQL INSERT statement that inserts at most one row and reads the row ID of the inserted row.
 */
class CompiledRowIdInsert internal constructor(
  internal val mutation: CompiledMutation
) {
  /**
   * SQL of this statement.
   */
  val sql get() = mutation.sql

  /**
   * Execute this compiled statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Row ID of the inserted row, or `null` when no row was inserted, such as a row that `CONFLICT_IGNORE`
   *     ignored
   */
  fun execute(): Long? = mutation.executeRowId()
}

internal fun MutationNode<InsertHead>.rowIdInsert() = RowIdInsertQuery(
  also {
    require(!head.table.withoutRowId) {
      "readRowId() cannot read the row ID of an INSERT into ${head.table.name}, a WITHOUT ROWID table"
    }
  }
)
