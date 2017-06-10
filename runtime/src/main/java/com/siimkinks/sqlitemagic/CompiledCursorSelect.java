package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

/**
 * Compiled SQL select statement that returns query results in raw {@link Cursor} objects.
 *
 * @param <T> Selected table type
 * @param <S> Selection type
 */
public interface CompiledCursorSelect<T, S> {
  /**
   * Parse selected table type from current cursor position.
   * <p>
   * Provided cursor must contain all selected table columns in correct order.
   *
   * @param cursor Cursor with query results
   * @return Parsed object
   */
  @Nullable
  @CheckResult
  @WorkerThread
  T getFromCurrentPosition(@NonNull Cursor cursor);

  /**
   * Execute this compiled select statement against a database.
   * <p>
   * Caller of this method is responsible for <b>always</b> closing {@link Cursor} instance
   * returned from the {@link Query}.
   * This method runs synchronously in the calling thread.
   *
   * @return Cursor over the result set.
   */
  @NonNull
  @CheckResult
  @WorkerThread
  Cursor execute();

  /**
   * Create an observable which will notify subscribers with a {@linkplain Query query} for
   * execution. Subscribers are responsible for <b>always</b> closing {@link Cursor} instance
   * returned from the {@link Query}.
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
  SingleItemQueryObservable<Cursor> observe();
}
