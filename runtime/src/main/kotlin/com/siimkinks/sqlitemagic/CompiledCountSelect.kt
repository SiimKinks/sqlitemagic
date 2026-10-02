package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread

/**
 * Compiled SQL count select statement.
 *
 * @param S Selection type
 */
interface CompiledCountSelect<S> {
  /**
   * Execute this compiled count select statement against a database.
   *
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   */
  @CheckResult
  @WorkerThread
  fun execute(): Long

  /**
   * Create an observable which will notify subscribers with a [Query] for execution.
   *
   * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
   * the supplied `table`'s data changes through the SqliteMagic provided model operations. Unsubscribe when you no
   * longer want updates to a query.
   *
   * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
   * [io.reactivex.Scheduler] supplied to [SqliteMagic.DatabaseSetupBuilder.scheduleRxQueriesOn]. For consistency,
   * the immediate notification sent on subscribe also uses this scheduler. As such, calling
   * [io.reactivex.Observable.subscribeOn] on the returned observable has no effect.
   *
   * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
   * `skip(1)` on the returned observable.
   *
   * One might want to explore the returned type methods for convenience query related operators.
   *
   * **Warning:** this method does not perform the query! Only by subscribing to the returned [io.reactivex.Observable]
   * will the operation occur.
   */
  @CheckResult
  fun observe(): CountQueryObservable
}
