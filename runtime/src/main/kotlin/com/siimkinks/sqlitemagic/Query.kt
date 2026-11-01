package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread

/** An executable query. */
abstract class Query<T> internal constructor(
  private val dbConnection: DbConnectionImpl?
) {
  internal fun resolveConnection() = dbConnection.orDefault()

  /**
   * Execute this query against a database and return the resulting data.
   * This method runs synchronously in the calling thread.
   *
   * Depending on the query type and result data type structure this method
   * can return `null`.
   * Rule of thumb is: If the result type is [List] it will
   * never be `null` otherwise it might be `null`.
   *
   * @return This query result
   * @throws IllegalStateException if called inside a transaction
   */
  @CheckResult
  @WorkerThread
  fun runBlocking(): T? = run(executionConnection())

  internal fun executionConnection() = resolveConnection().also {
    check(!it.hasActiveTransaction) { "Cannot execute observable query in a transaction." }
  }

  internal abstract fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean = NOT_DISPOSED): T
}

internal val NOT_DISPOSED = { false }
