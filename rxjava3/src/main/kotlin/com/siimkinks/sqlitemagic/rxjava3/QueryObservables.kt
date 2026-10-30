package com.siimkinks.sqlitemagic.rxjava3

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledCursor
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledRawCursor
import com.siimkinks.sqlitemagic.CompiledRawFirst
import com.siimkinks.sqlitemagic.CompiledRawSelect
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.DeclaredTables
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.RawSelectQuery
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.UndeclaredTables
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
 * longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
 * `skip(1)` on the returned observable.
 *
 * One might want to explore the returned type methods for convenience query related operators.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 *
 * @throws IllegalArgumentException if the query has parameters. Use [SelectQuery.compile] and bind their values instead
 */
fun <T> SelectQuery<T, *>.observe() = compile().observe()

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
 * longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
 * `skip(1)` on the returned observable.
 *
 * One might want to explore the returned type methods for convenience query related operators.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
fun <T> CompiledSelect<T>.observe() = ListQueryObservable(notifications())

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
 * longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
 * `skip(1)` on the returned observable.
 *
 * One might want to explore the returned type methods for convenience query related operators.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
fun <T> CompiledFirst<T>.observe() = SingleItemQueryObservable(notifications())

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
 * longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
 * `skip(1)` on the returned observable.
 *
 * One might want to explore the returned type methods for convenience query related operators.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
fun CompiledCount.observe() = CountQueryObservable(notifications())

/**
 * Create an observable which will notify subscribers with a [Query] for execution. Subscribers are responsible for
 * **always** closing [Cursor] instance returned from the [Query].
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the queried tables changes through the SqliteMagic provided operations. Unsubscribe when you no
 * longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * Note: To skip the immediate notification and only receive subsequent notifications when data has changed call
 * `skip(1)` on the returned observable.
 *
 * One might want to explore the returned type methods for convenience query related operators.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
fun CompiledCursor<*>.observe() = SingleItemQueryObservable(notifications())

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawRows")
fun <T> CompiledRawSelect<T, DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits its result to
 * downstream only once.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to [scheduleRxQueriesOn], so calling
 * `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawRows")
fun <T> CompiledRawSelect<T, UndeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .runQueryOnce()

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawFirst")
fun <T> CompiledRawFirst<T, DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Create a [Maybe] that when subscribed to executes this query against a database and emits the first row to
 * downstream only once. It completes without a value if the query returns no rows or the read value is `null`.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to [scheduleRxQueriesOn], so calling
 * `subscribeOn` has no effect.
 *
 * @return Deferred [Maybe] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawFirst")
fun <T> CompiledRawFirst<T, UndeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .runQueryOnce()

/**
 * Create an observable which will notify subscribers with a [Query] for execution. Subscribers are responsible for
 * **always** closing [Cursor] instance returned from the [Query].
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawCursor")
fun CompiledRawCursor<DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits the [Cursor]
 * to downstream only once. Subscribers are responsible for **always** closing the emitted [Cursor].
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to [scheduleRxQueriesOn], so calling
 * `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawCursor")
fun CompiledRawCursor<UndeclaredTables>.observe(): Single<Cursor> = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .runQueryOnce()
  .toSingle()

/**
 * Create an observable which will notify subscribers with a [Query] for execution.
 *
 * Subscribers will receive an immediate notification for initial data as well as subsequent notifications for when
 * the data of the tables defined with `observes` changes through the SqliteMagic provided operations. Unsubscribe
 * when you no longer want updates to a query.
 *
 * Since database triggers are inherently asynchronous, items emitted from the returned observable use the
 * [Scheduler] supplied to [scheduleRxQueriesOn]. For consistency, the immediate notification sent on subscribe also
 * uses this scheduler. As such, calling [Observable.subscribeOn] on the returned observable has no effect.
 *
 * Changes made while a notification is being processed are combined into one further notification.
 *
 * **Warning:** this method does not perform the query! Only by subscribing to the returned [Observable]
 * will the operation occur.
 */
@CheckResult
@JvmName("observeDeclaredRawQuery")
fun <T> RawSelectQuery<T, DeclaredTables>.observe() = compile().observe()

/**
 * Create a [Single] that when subscribed to executes this query against a database and emits its result to
 * downstream only once.
 *
 * The query runs only once, since it does not define the tables it reads. Define them with `observes` to receive
 * updates.
 *
 * The query runs and its result is emitted on the [Scheduler] supplied to [scheduleRxQueriesOn], so calling
 * `subscribeOn` has no effect.
 *
 * @return Deferred [Single] that when subscribed to executes the query and emits its result to downstream
 */
@CheckResult
@JvmName("observeUndeclaredRawQuery")
fun <T> RawSelectQuery<T, UndeclaredTables>.observe() = compile().observe()

internal fun <T> SelectPlan<T>.notifications(): Observable<Query<T>> =
  RxJavaPlugins.onAssembly(QueryNotifications(ReactiveAdapterSupport.observation(this)))
