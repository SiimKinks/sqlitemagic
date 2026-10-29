package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.NO_REGISTRATION
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SelectPlan

/**
 * Change notifications of a compiled SELECT statement, for reactive adapters.
 *
 * Create it with [ReactiveAdapterSupport.observation].
 *
 * @param T Result type
 */
@InternalSqliteMagicApi
class QueryObservation<T> internal constructor(
  private val plan: SelectPlan<T>
) {
  /** Query to run for the initial result and after every change. */
  val query: Query<T> = plan.execution()

  /** Whether the query reads tables whose changes are published. A query that reads none runs only once. */
  val observesChanges get() = plan.prepared.dependencies.observedTables.isNotEmpty()

  /**
   * Resolve the connection that the query runs on: the one it was configured with, or else the current default
   * connection.
   *
   * @return Connection to pass to [register]
   */
  fun connection(): DbConnection = query.resolveConnection()

  /**
   * Deliver the changes of the tables that the query reads to [observer] until the returned registration is closed.
   *
   * If [connection] is already closed, [TableChangeObserver.onClosed] is called before this returns.
   *
   * @param connection Connection returned by [connection]
   * @param observer Receiver of the changes
   * @return Registration to close when the changes are no longer needed
   * @throws IllegalStateException if the calling thread has an active transaction on [connection]
   */
  fun register(
    connection: DbConnection,
    observer: TableChangeObserver
  ): AutoCloseable {
    val target = connection as DbConnectionImpl
    target.ensureNotInTransaction()
    val observedTables = plan.prepared.dependencies.observedTables
    return when {
      observedTables.isNotEmpty() -> target.tableObservers.add(
        tables = observedTables,
        observer = observer
      )
      else -> NO_REGISTRATION.also {
        if (target.isClosed) {
          observer.onClosed()
        }
      }
    }
  }
}
