package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledCursor
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledRawCursor
import com.siimkinks.sqlitemagic.CompiledRawFirst
import com.siimkinks.sqlitemagic.CompiledRawSelect
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DeclaredTables
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.RawSelectQuery
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.UndeclaredTables
import com.siimkinks.sqlitemagic.internal.QueryObservation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow.DROP_LATEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the queried tables
 * changes through the SqliteMagic provided operations. Collecting starts listening for those changes before the
 * initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 *
 * @throws IllegalArgumentException if the query has parameters. Use [SelectQuery.compile] and bind their values instead
 */
@CheckResult
fun <T> SelectQuery<T, *>.observe() = compile().observe()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the queried tables
 * changes through the SqliteMagic provided operations. Collecting starts listening for those changes before the
 * initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
@CheckResult
fun <T> CompiledSelect<T>.observe(): Flow<Query<List<T>>> = changes()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the queried tables
 * changes through the SqliteMagic provided operations. Collecting starts listening for those changes before the
 * initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
@CheckResult
fun <T> CompiledFirst<T>.observe(): Flow<Query<T?>> = changes()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the queried tables
 * changes through the SqliteMagic provided operations. Collecting starts listening for those changes before the
 * initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
@CheckResult
fun CompiledCount.observe(): Flow<Query<Long>> = changes()

/**
 * Create a cold [Flow] of a [Query] for execution. Collectors are responsible for **always** closing the [Cursor]
 * that a delivered query result is.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the queried tables
 * changes through the SqliteMagic provided operations. Collecting starts listening for those changes before the
 * initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 *
 * @throws IllegalArgumentException if the query has parameters without bound values
 */
@CheckResult
fun CompiledCursor<*>.observe(): Flow<Query<Cursor>> = changes()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the tables defined
 * with `observes` changes through the SqliteMagic provided operations. Collecting starts listening for those changes
 * before the initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 */
@CheckResult
fun <T> CompiledRawSelect<T, DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Execute this query against a database and return its rows.
 *
 * The query runs once, since it does not define the tables it reads. Define them with `observes` to observe it. It
 * runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction
 * @throws NoSuchElementException if the connection is closed
 */
suspend fun <T> CompiledRawSelect<T, UndeclaredTables>.await() = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .runQueryOnce()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the tables defined
 * with `observes` changes through the SqliteMagic provided operations. Collecting starts listening for those changes
 * before the initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 */
@CheckResult
fun <T> CompiledRawFirst<T, DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Execute this query against a database and return its first row, or `null` if the query returns no rows, the read
 * value is `null` or the connection is closed.
 *
 * The query runs once, since it does not define the tables it reads. Define them with `observes` to observe it. It
 * runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction
 */
suspend fun <T> CompiledRawFirst<T, UndeclaredTables>.await() = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .runQueryOnce()

/**
 * Create a cold [Flow] of a [Query] for execution. Collectors are responsible for **always** closing the [Cursor]
 * that a delivered query result is.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the tables defined
 * with `observes` changes through the SqliteMagic provided operations. Collecting starts listening for those changes
 * before the initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 */
@CheckResult
fun CompiledRawCursor<DeclaredTables>.observe() = ReactiveAdapterSupport
  .plan(this)
  .observe()

/**
 * Execute this query against a database and return its [Cursor], which the caller must **always** close. If the
 * calling coroutine is cancelled before the cursor is returned, the cursor is closed instead.
 *
 * The query runs once, since it does not define the tables it reads. Define them with `observes` to observe it. It
 * runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction
 * @throws NoSuchElementException if the connection is closed
 */
suspend fun CompiledRawCursor<UndeclaredTables>.await() = ReactiveAdapterSupport
  .plan(this)
  .observe()
  .first()
  .awaitResult()

/**
 * Create a cold [Flow] of a [Query] for execution.
 *
 * A collector receives the query immediately for the initial data and again whenever the data of the tables defined
 * with `observes` changes through the SqliteMagic provided operations. Collecting starts listening for those changes
 * before the initial emission and stops when the collection ends. The flow completes when the connection is closed.
 *
 * Changes made while an emission is being processed are combined into one further emission.
 *
 * The flow emits in the collector's context. Use the `runQuery` operators to run each query on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * Collecting fails with [IllegalStateException] on a thread that has an active transaction.
 */
@CheckResult
fun <T> RawSelectQuery<T, DeclaredTables>.observe() = compile().observe()

/**
 * Compile and execute this query against a database and return its rows.
 *
 * The query runs once, since it does not define the tables it reads. Define them with `observes` to observe it. It
 * runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction
 * @throws NoSuchElementException if the connection is closed
 */
suspend fun <T> RawSelectQuery<T, UndeclaredTables>.await() = compile().await()

private fun <T> SelectPlan<T>.changes(): Flow<Query<T>> = QueryChanges(ReactiveAdapterSupport.observation(this))

internal class QueryChanges<T>(
  val observation: QueryObservation<T>
) : Flow<Query<T>> by observation.asFlow()

private fun <T> QueryObservation<T>.asFlow() = flow {
  listen(connection = connection()) {
    it.repeatOnChange { emit(query) }
  }
}

internal inline fun QueryObservation<*>.listen(
  connection: DbConnection,
  block: (TableChanges) -> Unit
) {
  val changes = TableChanges(observesChanges)
  val registration = register(
    connection = connection,
    observer = changes
  )
  try {
    if (!changes.isClosed) {
      block(changes)
    }
  } finally {
    registration.close()
  }
}

internal class TableChanges(
  val observesChanges: Boolean
) : TableChangeObserver {
  private val signals = Channel<Unit>(
    capacity = 1,
    onBufferOverflow = DROP_LATEST
  )

  @Volatile
  var isClosed = false
    private set

  override fun onTablesChanged(tables: Set<String>) {
    signals.trySend(Unit)
  }

  override fun onClosed() {
    isClosed = true
    signals.close()
  }

  suspend inline fun repeatOnChange(action: () -> Unit) {
    do {
      action()
    } while (observesChanges && awaitChange())
  }

  suspend fun awaitChange() = signals.receiveCatching().isSuccess && !isClosed
}
