package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutationResults
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.PreparedQuery
import com.siimkinks.sqlitemagic.internal.sql.bindNativeValues

/**
 * Compiled SQL statement that changes rows: INSERT, UPDATE or DELETE.
 */
class CompiledMutation internal constructor(
  private val prepared: PreparedQuery,
  internal val arguments: List<NativeValue>,
  private val tables: Array<out String>,
  private val deletedTable: String?,
  private val cache: Boolean,
  private val skipped: Boolean
) {
  /**
   * SQL of this statement.
   */
  val sql get() = prepared.sql

  /**
   * Execute this compiled statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Number of inserted, updated or deleted rows
   */
  fun execute(): Int = when {
    skipped -> 0
    else -> execute(
      run = MutationResults::executeUpdateDelete,
      changed = { it > 0 }
    )
  }

  internal fun executeRowId() = execute(
    run = MutationResults::executeInsertRowId,
    changed = { it != null }
  )

  private inline fun <T> execute(
    run: (MutationResults, SQLiteStatement) -> T,
    changed: (T) -> Boolean
  ): T {
    val connection = resolveConnection()
    val result = connection.write { pooled ->
      pooled.withStatement(
        sql = prepared.sql,
        cache = cache
      ) { statement ->
        bindNativeValues(
          statement = statement,
          values = arguments
        )
        notifyingOnFailure(
          notify = { connection.sendChangedTables() },
          block = { run(pooled.mutations, statement) }
        )
      }
    }
    if (changed(result)) {
      connection.sendChangedTables()
    }
    return result
  }

  private fun DbConnectionImpl.sendChangedTables() = when (deletedTable) {
    null -> sendTableTriggers(tables)
    else -> sendDeleteTriggers(deletedTable)
  }

  internal fun resolveConnection() = prepared.connection.orDefault()
}

internal inline fun <T> notifyingOnFailure(notify: () -> Unit, block: () -> T) = try {
  block()
} catch (failure: Throwable) {
  try {
    notify()
  } catch (triggerFailure: Throwable) {
    failure.addSuppressed(triggerFailure)
  }
  throw failure
}
