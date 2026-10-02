package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread

/**
 * Compiled SQL select statement.
 *
 * @param T Selected table type
 * @param S Selection type
 */
interface CompiledSelect<T, S> {
  /**
   * Execute this compiled select statement against a database.
   *
   * Returned value will never be `null`. If query returns no rows then resulting list will be empty. This method runs
   * synchronously in the calling thread.
   *
   * @return Query result
   */
  @CheckResult
  @WorkerThread
  fun execute(): List<T>

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
  fun observe(): ListQueryObservable<T>

  /**
   * Compile select builder and instruct it to take only the first element from the result set.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * It is important to note that this method also modifies the underlying SQL and adds "LIMIT 1" clause. It will
   * throw [android.database.SQLException] if user has defined LIMIT clause with anything other than "1". If there is
   * already "LIMIT 1" clause then SQL will not be modified.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  @CheckResult
  fun takeFirst(): CompiledFirstSelect<T, S>

  /**
   * Compile select builder and instruct it to count only the rows of the resulting query.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * It is important to note that this method also modifies the underlying SQL and adds "`count(*)`" function. It
   * also discards any other columns defined in the SQL builder. Resulting SQL will be "SELECT count(*) FROM ..."
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  @CheckResult
  fun count(): CompiledCountSelect<S>

  /**
   * Compile select builder and instruct it to return raw [android.database.Cursor] when query is executed.
   *
   * Result is immutable object which can be shared across multiple threads without side effects.
   *
   * NB! This method does not compile the underlying SQL statement against a database.
   *
   * @return Immutable compiled select statement
   */
  @CheckResult
  fun toCursor(): CompiledCursorSelect<T, S>
}
