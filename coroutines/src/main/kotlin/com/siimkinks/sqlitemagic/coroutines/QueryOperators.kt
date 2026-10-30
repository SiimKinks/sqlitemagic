package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.QueryObservation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor

/**
 * Run each emitted [Query] and emit its rows as a [List].
 *
 * Be careful using this operator as it will always consume the entire cursor and create objects for each row, every
 * time the upstream emits a new query. On tables whose queries update frequently or very large result sets this can
 * result in the creation of many objects.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Cancellation stops reading the
 * rows. Applied directly to an `observe()` flow, the next query can start on that dispatcher while the collector still
 * processes the previous result.
 */
@CheckResult
@JvmName("runListQuery")
fun <T> Flow<Query<List<T>>>.runQuery(): Flow<List<T>> = runEach(::identity)

/**
 * Run only the first emitted [Query] and return its rows as a [List].
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
@JvmName("runListQueryOnce")
suspend fun <T> Flow<Query<List<T>>>.runQueryOnce(): List<T> = first().awaitResult()

/**
 * Run each emitted [Query] which returns a single row and emit its result.
 *
 * This operator ignores queries with an empty result or a `null` scalar result. Each query runs on the
 * [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an `observe()` flow, the next
 * query can start on that dispatcher while the collector still processes the previous result.
 */
@CheckResult
@JvmName("runSingleItemQuery")
fun <T : Any> Flow<Query<T?>>.runQuery(): Flow<T> = runEach(::identity)

/**
 * Run each emitted [Query] which returns a single row and emit its result.
 *
 * This operator emits [defaultValue] if the query result is empty or the selected scalar value is `null`. Each query
 * runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an `observe()` flow,
 * the next query can start on that dispatcher while the collector still processes the previous result.
 *
 * @param defaultValue Value emitted if the [Query] result is empty or the selected scalar value is `null`
 */
@CheckResult
fun <T : Any> Flow<Query<T?>>.runQueryOrDefault(defaultValue: T): Flow<T> = runEach { it ?: defaultValue }

/**
 * Run only the first emitted [Query] which returns a single row and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result, or `null` if it is empty, the selected scalar value is `null` or the upstream completes
 *     without emitting, as it does on a closed connection
 */
@JvmName("runSingleItemQueryOnce")
suspend fun <T : Any> Flow<Query<T?>>.runQueryOnce(): T? = runFirstSingleItemQuery()

/**
 * Run only the first emitted [Query] which returns a single row and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param defaultValue Value returned if the [Query] result is empty or the selected scalar value is `null`
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
suspend fun <T : Any> Flow<Query<T?>>.runQueryOnceOrDefault(defaultValue: T): T =
  runFirstSingleItemQueryOrDefault(defaultValue)

/**
 * Run each emitted count [Query] and emit its result.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow, the next query can start on that dispatcher while the collector still processes the previous
 * result.
 */
@CheckResult
@JvmName("runCountQuery")
fun Flow<Query<Long>>.runQuery(): Flow<Long> = runEach(::identity)

/**
 * Run each emitted count [Query] and emit its result.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow, the next query can start on that dispatcher while the collector still processes the previous
 * result.
 *
 * @param defaultValue Value emitted if the [Query] result is `null`
 */
@CheckResult
fun Flow<Query<Long>>.runQueryOrDefault(defaultValue: Long): Flow<Long> = runEach { count: Long? ->
  count ?: defaultValue
}

/**
 * Run only the first emitted count [Query] and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @return Query result, or `null` if the upstream completes without emitting, as it does on a closed connection
 */
@JvmName("runCountQueryOnce")
suspend fun Flow<Query<Long>>.runQueryOnce(): Long? = runFirstSingleItemQuery()

/**
 * Run only the first emitted count [Query] and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn].
 *
 * @param defaultValue Value returned if the [Query] result is `null`
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
suspend fun Flow<Query<Long>>.runQueryOnceOrDefault(defaultValue: Long): Long =
  runFirstSingleItemQueryOrDefault(defaultValue)

/**
 * Run each emitted count [Query] and emit whether the count is zero.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow, the next query can start on that dispatcher while the collector still processes the previous
 * result.
 */
@CheckResult
fun Flow<Query<Long>>.isZero(): Flow<Boolean> = runEach { it == 0L }

/**
 * Run each emitted count [Query] and emit whether the count is not zero.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow, the next query can start on that dispatcher while the collector still processes the previous
 * result.
 */
@CheckResult
fun Flow<Query<Long>>.isNotZero(): Flow<Boolean> = runEach { it != 0L }

/**
 * Run each emitted cursor [Query] and emit its [Cursor], which the collector must **always** close.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. If the collection is cancelled
 * before a cursor is emitted, the cursor is closed instead. Applied directly to an `observe()` flow, the next query can
 * start on that dispatcher while the collector still processes the previous result.
 */
@CheckResult
@JvmName("runCursorQuery")
fun Flow<Query<Cursor>>.runQuery(): Flow<Cursor> = runEach(::identity)

/**
 * Run each emitted cursor [Query] and emit its [Cursor], which the collector must **always** close.
 *
 * Each query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. If the collection is cancelled
 * before a cursor is emitted, the cursor is closed instead. [defaultValue] is never closed. Applied directly to an
 * `observe()` flow, the next query can start on that dispatcher while the collector still processes the previous
 * result.
 *
 * @param defaultValue Value emitted if the [Query] result is `null`
 */
@CheckResult
fun Flow<Query<Cursor>>.runQueryOrDefault(defaultValue: Cursor): Flow<Cursor> = runEach { cursor: Cursor? ->
  cursor ?: defaultValue
}

/**
 * Run only the first emitted cursor [Query] and return its [Cursor], which the caller must **always** close.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. If the calling coroutine is
 * cancelled before the cursor is returned, the cursor is closed instead.
 *
 * @return Query result, or `null` if the upstream completes without emitting, as it does on a closed connection
 */
@JvmName("runCursorQueryOnce")
suspend fun Flow<Query<Cursor>>.runQueryOnce(): Cursor? = runFirstSingleItemQuery()

/**
 * Run only the first emitted cursor [Query] and return its [Cursor], which the caller must **always** close.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. If the calling coroutine is
 * cancelled before the cursor is returned, the cursor is closed instead. [defaultValue] is never closed.
 *
 * @param defaultValue Value returned if the [Query] result is `null`
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
suspend fun Flow<Query<Cursor>>.runQueryOnceOrDefault(defaultValue: Cursor): Cursor =
  runFirstSingleItemQueryOrDefault(defaultValue)

/**
 * Execute this query against a database and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. If the calling coroutine is
 * cancelled before a [Cursor] result is returned, the cursor is closed instead.
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction
 * @see [Query.runBlocking]
 */
suspend fun <T> Query<T>.run() = awaitResult(ReactiveAdapterSupport.executionConnection(this))

internal suspend fun <T> Query<T>.awaitResult(connection: DbConnection = ReactiveAdapterSupport.connection(this)): T {
  var mapped: T? = null
  return try {
    connection.withQueryDispatcher {
      ReactiveAdapterSupport
        .map(
          query = this@awaitResult,
          cursor = ReactiveAdapterSupport.openCursor(this@awaitResult),
          isDisposed = { !isActive }
        )
        .also { mapped = it }
    }
  } catch (cancellation: CancellationException) {
    mapped?.let {
      ReactiveAdapterSupport.discardResult(
        query = this,
        result = it
      )
    }
    throw cancellation
  }
}

private fun <T, R : Any> Flow<Query<T>>.runEach(transform: (T) -> R?): Flow<R> = when (this) {
  is QueryChanges -> QueryResults(
    observation = observation,
    transform = transform
  )
  else -> mapNotNull { transform(it.awaitResult()) }
}

private class QueryResults<T, R : Any>(
  private val observation: QueryObservation<T>,
  private val transform: (T) -> R?
) : Flow<R> {
  override suspend fun collect(collector: FlowCollector<R>) {
    val connection = observation.connection()
    val dispatcher = connection.queryDispatcher()
    val context = currentCoroutineContext()
    observation.listen(connection = connection) { changes ->
      when {
        context[ContinuationInterceptor] == dispatcher || !dispatcher.isDispatchNeeded(context) -> {
          val isDisposed = { !context.isActive }
          changes.repeatOnChange {
            context.ensureActive()
            collector.emitResult(execute(isDisposed))
          }
        }
        else -> coroutineScope {
          val results = Channel(onUndeliveredElement = ::discard)
          launch(dispatcher) {
            val isDisposed = { !isActive }
            changes.repeatOnChange { results.send(execute(isDisposed)) }
            results.close()
          }
          results.consumeEach { collector.emitResult(it) }
        }
      }
    }
  }

  private fun execute(isDisposed: () -> Boolean) = ReactiveAdapterSupport.map(
    query = observation.query,
    cursor = ReactiveAdapterSupport.openCursor(observation.query),
    isDisposed = isDisposed
  )

  private fun discard(result: T) = ReactiveAdapterSupport.discardResult(
    query = observation.query,
    result = result
  )

  private suspend inline fun FlowCollector<R>.emitResult(result: T) {
    val context = currentCoroutineContext()
    if (!context.isActive) {
      discard(result)
      context.ensureActive()
    }
    transform(result)?.let { emit(it) }
  }
}

private fun <T> identity(value: T) = value

private suspend fun <T : Any> Flow<Query<out T?>>.runFirstSingleItemQuery() = firstOrNull()?.awaitResult()

private suspend fun <T : Any> Flow<Query<out T?>>.runFirstSingleItemQueryOrDefault(defaultValue: T) = first()
  .awaitResult() ?: defaultValue
