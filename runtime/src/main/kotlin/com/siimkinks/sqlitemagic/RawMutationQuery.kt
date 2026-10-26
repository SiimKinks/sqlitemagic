package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.compileMutation
import io.reactivex.Single

/**
 * Raw SQL UPDATE or DELETE statement.
 *
 * Statements are immutable and can be reused.
 */
class RawMutationQuery internal constructor(
  private val raw: ExpressionNode.Raw,
  private val notifiedTables: Array<String> = emptyArray(),
  private val connection: DbConnectionImpl? = null
) {
  /**
   * Define tables that this statement changes.
   *
   * Observed queries of these tables are re-run after the statement changes any rows. Calling this method again
   * replaces the previously defined tables.
   *
   * @param table Table that this statement changes
   * @param tables Additional tables that this statement changes
   * @return Raw SQL statement with the defined tables
   */
  @CheckResult
  fun notifies(table: Table<*>, vararg tables: Table<*>) = RawMutationQuery(
    raw = raw,
    notifiedTables = Array(tables.size + 1) { index ->
      when (index) {
        0 -> table.name
        else -> tables[index - 1].name
      }
    },
    connection = connection
  )

  /**
   * Configure this statement to use provided connection for database operation.
   *
   * @param connection Database connection
   * @return Raw SQL statement
   */
  @CheckResult
  fun usingConnection(connection: DbConnection) = RawMutationQuery(
    raw = raw,
    notifiedTables = notifiedTables,
    connection = connection as DbConnectionImpl
  )

  /**
   * Compile this SQL statement.
   *
   * Result object is immutable and can be shared between threads without any side effects.
   *
   * @return Immutable compiled statement
   */
  @CheckResult
  fun compile() = compileMutation(
    renderable = raw,
    connection = connection,
    tables = notifiedTables
  )

  /**
   * Compile and execute this statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Number of updated or deleted rows
   */
  fun execute(): Int = compile().execute()

  /**
   * Compile this statement and create a [Single] that when subscribed to executes this statement against a database
   * and emits nr of updated or deleted records to downstream only once.
   *
   * @return Deferred [Single] that when subscribed to executes the statement and emits its result to downstream
   */
  @CheckResult
  fun observe(): Single<Int> = compile().observe()
}
