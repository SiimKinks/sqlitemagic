package com.siimkinks.sqlitemagic.coroutines

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.QueryObservation
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Run each emitted [Query] and emit its rows as a [List].
 *
 * Be careful using this operator as it will always read the entire result and create objects for each row, every time
 * the upstream emits a new query. On tables whose queries update frequently or very large result sets this can result
 * in the creation of many objects.
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
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow inside [withTransaction] of its connection, it runs in the transaction and reads its changes.
 *
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
@JvmName("runListQueryOnce")
suspend fun <T> Flow<Query<List<T>>>.runQueryOnce(): List<T> = runFirst { throw NoSuchElementException(NO_QUERY) }

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
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow inside [withTransaction] of its connection, it runs in the transaction and reads its changes.
 *
 * @return Query result, or `null` if it is empty, the selected scalar value is `null` or the upstream completes
 *     without emitting, as it does on a closed connection
 */
@JvmName("runSingleItemQueryOnce")
suspend fun <T : Any> Flow<Query<T?>>.runQueryOnce(): T? = runFirst { null }

/**
 * Run only the first emitted [Query] which returns a single row and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow inside [withTransaction] of its connection, it runs in the transaction and reads its changes.
 *
 * @param defaultValue Value returned if the [Query] result is empty or the selected scalar value is `null`
 * @return Query result
 * @throws NoSuchElementException if the upstream completes without emitting, as it does on a closed connection
 */
suspend fun <T : Any> Flow<Query<T?>>.runQueryOnceOrDefault(defaultValue: T): T =
  runFirst { throw NoSuchElementException(NO_QUERY) } ?: defaultValue

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
 * Run only the first emitted count [Query] and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Applied directly to an
 * `observe()` flow inside [withTransaction] of its connection, it runs in the transaction and reads its changes.
 *
 * @return Query result, or `null` if the upstream completes without emitting, as it does on a closed connection
 */
@JvmName("runCountQueryOnce")
suspend fun Flow<Query<Long>>.runQueryOnce(): Long? = runFirst { null }

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
 * Execute this query against a database and return its result.
 *
 * The query runs on the [CoroutineDispatcher] supplied to [dispatchCoroutineQueriesOn]. Inside [withTransaction] of its
 * connection, it runs in the transaction and reads its changes.
 *
 * @return Query result
 * @throws IllegalStateException if called on a thread that has an active transaction outside [withTransaction] of its
 *     connection
 * @see [Query.runBlocking]
 */
suspend fun <T> Query<T>.run(): T = runOnce { awaitResult(it) }

internal suspend fun <T> Query<T>.awaitResult(
  connection: DbConnection = ReactiveAdapterSupport.connection(this)
) = connection.withQueryDispatcher {
  ReactiveAdapterSupport.run(
    query = this@awaitResult,
    isDisposed = { !isActive }
  )
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
            collector.emitResult(
              execute(
                connection = connection,
                context = context,
                isDisposed = isDisposed
              )
            )
          }
        }
        else -> coroutineScope {
          val results = Channel<T>()
          launch(dispatcher) {
            val isDisposed = { !isActive }
            changes.repeatOnChange {
              results.send(
                execute(
                  connection = connection,
                  context = coroutineContext,
                  isDisposed = isDisposed
                )
              )
            }
            results.close()
          }
          results.consumeEach { collector.emitResult(it) }
        }
      }
    }
  }

  private suspend inline fun execute(
    connection: DbConnection,
    context: CoroutineContext,
    noinline isDisposed: () -> Boolean
  ) = connection.waitingForWriter {
    connection.withOwnerOf(context) {
      ReactiveAdapterSupport.run(
        query = observation.query,
        isDisposed = isDisposed
      )
    }
  }

  private suspend inline fun FlowCollector<R>.emitResult(result: T) {
    currentCoroutineContext().ensureActive()
    transform(result)?.let { emit(it) }
  }
}

private fun <T> identity(value: T) = value

private const val NO_QUERY = "Expected at least one element"

private suspend inline fun <T> Flow<Query<out T>>.runFirst(absent: () -> T): T = when (this) {
  is QueryChanges -> observation.query.runOnce { connection ->
    when {
      ReactiveAdapterSupport.isClosed(connection) -> absent()
      else -> awaitResult(connection)
    }
  }
  else -> when (val query = firstOrNull()) {
    null -> absent()
    else -> query.awaitResult()
  }
}

private suspend inline fun <T> Query<out T>.runOnce(outsideTransaction: Query<out T>.(DbConnection) -> T): T {
  val context = currentCoroutineContext()
  val connection = ReactiveAdapterSupport.connection(this)
  return when {
    context.inTransactionOf(connection) -> awaitInTransaction(connection)
    else -> outsideTransaction(connection.withOwnerOf(context) { ReactiveAdapterSupport.executionConnection(this) })
  }
}

private suspend fun <T> Query<out T>.awaitInTransaction(connection: DbConnection): T = connection
  .withQueryDispatcher {
    ReactiveAdapterSupport.runInTransaction(
      query = this@awaitInTransaction,
      isDisposed = { !isActive }
    )
  }
