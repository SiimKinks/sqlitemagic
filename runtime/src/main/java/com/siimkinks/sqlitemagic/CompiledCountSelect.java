package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

/**
 * Compiled SQL count select statement.
 *
 * @param <S> Selection type
 */
public interface CompiledCountSelect<S> {
  /**
   * Execute this compiled count select statement against a database.
   * <p>
   * This method runs synchronously in the calling thread.
   *
   * @return Query result
   */
  @CheckResult
  @WorkerThread
  long execute();

  /**
   * Create an observable which will notify subscribers with a {@linkplain Query query} for
   * execution.
   * <p>
   * Subscribers will receive an immediate notification for initial data as well as subsequent
   * notifications for when the supplied {@code table}'s data changes through the SqliteMagic
   * provided model operations. Unsubscribe when you no longer want updates to a query.
   * <p>
   * Since database triggers are inherently asynchronous, items emitted from the returned
   * observable use the {@link rx.Scheduler} supplied to
   * {@link com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder#scheduleRxQueriesOn}. For
   * consistency, the immediate notification sent on subscribe also uses this scheduler. As such,
   * calling {@link rx.Observable#subscribeOn subscribeOn} on the returned observable has no effect.
   * <p>
   * Note: To skip the immediate notification and only receive subsequent notifications when data
   * has changed call {@code skip(1)} on the returned observable.
   * <p>
   * One might want to explore the returned type methods for convenience query related
   * operators.
   * <p>
   * <b>Warning:</b> this method does not perform the query! Only by subscribing to the returned
   * {@link rx.Observable} will the operation occur.
   */
  @NonNull
  @CheckResult
  CountQueryObservable observe();
}
