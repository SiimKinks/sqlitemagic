package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.util.List;

/**
 * Compiled SQL select statement.
 *
 * @param <T> Selected table type
 * @param <S> Selection type
 */
public interface CompiledSelect<T, S> {
	/**
	 * Execute this compiled select statement against a database.
	 * <p>
	 * Returned value will never be {@code null}. If query returns no rows then resulting
	 * list will be empty.<br>
	 * This method runs synchronously in the calling thread.
	 *
	 * @return Query result
	 */
	@NonNull
	@CheckResult
	@WorkerThread
	List<T> execute();

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
	QueryObservable<List<T>> observe();

	/**
	 * Compile select builder and instruct it to take only the first element from the
	 * result set.
	 * <p>
	 * Result is immutable object which can be shared across multiple threads
	 * without side effects.
	 * <p>
	 * It is important to note that this method also modifies the underlying SQL and
	 * adds "LIMIT 1" clause. It will throw {@link android.database.SQLException SQLException}
	 * if user has defined LIMIT clause with anything other than "1". If there is
	 * already "LIMIT 1" clause then SQL will not be modified.
	 * <p>
	 * NB! This method does not compile the underlying SQL statement against a database.
	 *
	 * @return Immutable compiled select statement
	 */
	@NonNull
	@CheckResult
	CompiledFirstSelect<T, S> takeFirst();

	/**
	 * Compile select builder and instruct it to count only the rows of the resulting
	 * query.
	 * <p>
	 * Result is immutable object which can be shared across multiple threads
	 * without side effects.
	 * <p>
	 * It is important to note that this method also modifies the underlying SQL and
	 * adds "{@code count(*)}" function. It also discards any other columns defined in the
	 * SQL builder. Resulting SQL will be "SELECT count(*) FROM ..."
	 * <p>
	 * NB! This method does not compile the underlying SQL statement against a database.
	 * @return Immutable compiled select statement
	 */
	@NonNull
	@CheckResult
	CompiledCountSelect<S> count();

	/**
	 * Compile select builder and instruct it to return raw
	 * {@link android.database.Cursor Cursor} when query is executed.
	 * <p>
	 * Result is immutable object which can be shared across multiple threads
	 * without side effects.
	 * <p>
	 * NB! This method does not compile the underlying SQL statement against a database.
	 *
	 * @return Immutable compiled select statement
	 */
	@NonNull
	@CheckResult
	CompiledCursorSelect<T, S> toCursor();
}
