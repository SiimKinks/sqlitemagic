package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread

/** An executable query. */
abstract class Query<T> internal constructor(
  internal val dbConnection: DbConnectionImpl?
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
  fun runBlocking(): T? = map(openCursor())

  internal fun openCursor() = executionConnection().let(::rawQuery)

  internal fun executionConnection() = resolveConnection().also { connection ->
    check(!connection.hasActiveTransaction) { "Cannot execute observable query in a transaction." }
  }

  internal abstract fun rawQuery(dbConnection: DbConnectionImpl): Cursor

  internal open fun discardResult(result: T) = Unit

  internal abstract fun map(cursor: Cursor, isDisposed: () -> Boolean = NOT_DISPOSED): T
}

internal val NOT_DISPOSED: () -> Boolean = { false }
