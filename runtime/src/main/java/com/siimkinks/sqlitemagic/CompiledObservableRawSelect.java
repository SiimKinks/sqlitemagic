package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

/**
 * Immutable object that contains raw SQL SELECT statement. This object
 * can be shared between threads without any side effects.
 * <p>
 * Note: This class does not contain SQL statement compiled against the database.
 */
public interface CompiledObservableRawSelect extends CompiledRawSelect {
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
   * calling {@link io.reactivex.Observable#subscribeOn subscribeOn} on the returned observable has no
   * effect.
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
