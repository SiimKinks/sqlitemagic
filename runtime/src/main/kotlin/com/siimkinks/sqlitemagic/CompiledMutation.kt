package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.PreparedQuery
import com.siimkinks.sqlitemagic.internal.sql.bindNativeValues

/**
 * Compiled SQL UPDATE or DELETE statement.
 */
class CompiledMutation internal constructor(
  private val prepared: PreparedQuery,
  internal val arguments: List<NativeValue>,
  private val tables: Array<out String>
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
   * @return Number of updated or deleted rows
   */
  fun execute(): Int = resolveConnection()
    .let { connection ->
      connection
        .compileStatement(prepared.sql)
        .use { statement ->
          bindNativeValues(
            statement = statement,
            values = arguments
          )
          val affected = notifyingOnFailure(
            notify = { connection.sendTableTriggers(tables) },
            block = statement::executeUpdateDelete
          )
          if (affected > 0) {
            connection.sendTableTriggers(tables)
          }
          affected
        }
    }

  internal fun resolveConnection() = prepared.connection.orDefault()
}

internal inline fun <T> notifyingOnFailure(notify: () -> Unit, block: () -> T): T = try {
  block()
} catch (failure: Throwable) {
  try {
    notify()
  } catch (triggerFailure: Throwable) {
    failure.addSuppressed(triggerFailure)
  }
  throw failure
}
