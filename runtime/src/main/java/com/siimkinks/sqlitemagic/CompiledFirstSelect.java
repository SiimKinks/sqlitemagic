package com.siimkinks.sqlitemagic;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Compiled SQL select statement with {@code LIMIT 1} clause.
 *
 * @param <T> Selected table type
 * @param <S> Selection type
 */
public interface CompiledFirstSelect<T, S> {
  /**
   * Execute this compiled select statement against a database.
   * <p>
   * Returned value will be {@code null} only if query returns no rows.<br>
   * This method runs synchronously in the calling thread.
   *
   * @return Query result or {@code null} if query returns no rows
   */
  @Nullable
  @CheckResult
  @WorkerThread
  T execute();

  /**
   * Create an observable which will notify subscribers with a {@linkplain Query query} for
   * execution.
   * <p>
   * Subscribers will receive an immediate notification for initial data as well as subsequent
   * notifications for when the supplied {@code table}'s data changes through the SqliteMagic
   * provided model operations. Unsubscribe when you no longer want updates to a query.
   * <p>
   * Since database triggers are inherently asynchronous, items emitted from the returned
   * observable use the {@link io.reactivex.Scheduler} supplied to
   * {@link com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder#scheduleRxQueriesOn}. For
   * consistency, the immediate notification sent on subscribe also uses this scheduler. As such,
   * calling {@link io.reactivex.Observable#subscribeOn subscribeOn} on the returned observable has no effect.
   * <p>
   * Note: To skip the immediate notification and only receive subsequent notifications when data
   * has changed call {@code skip(1)} on the returned observable.
   * <p>
   * One might want to explore the returned type methods for convenience query related
   * operators.
   * <p>
   * <b>Warning:</b> this method does not perform the query! Only by subscribing to the returned
   * {@link io.reactivex.Observable} will the operation occur.
   */
  @NonNull
  @CheckResult
  SingleItemQueryObservable<T> observe();
}
