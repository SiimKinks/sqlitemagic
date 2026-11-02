package com.siimkinks.sqlitemagic.coroutines

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import com.siimkinks.sqlitemagic.internal.WriterWait
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

internal val QUERY_DISPATCHER = AdapterSettingKey<CoroutineDispatcher>()

/**
 * Define the dispatcher where coroutine database calls run: the `runQuery` operators, `runQueryOnce`, `Query.run()`,
 * every `await()` and the blocks of [withTransaction].
 *
 * Defaults to [Dispatchers.IO].
 *
 * Applied directly to an `observe()` flow, the `runQuery`, `runQueryOrDefault`, `isZero` and `isNotZero` operators also
 * wait for table changes on this dispatcher, so a change resumes the collector only to deliver the result.
 *
 * A coroutine call that waits for the writer connection, such as a write while another writer or a [withTransaction]
 * transaction holds it, suspends in turn with the blocking writers instead of holding a thread of this dispatcher.
 *
 * A transaction started with [withTransaction] follows its coroutines to this dispatcher. A transaction started with
 * `newTransaction()` or `inTransaction {}` belongs to the thread that started it, so a call that this dispatcher runs
 * on another thread does not take part in it.
 *
 * Whether a coroutine call takes part in a [withTransaction] transaction depends on the coroutine, not the thread. A
 * coroutine that a transaction's coroutine resumes on its own thread without dispatching, as [Dispatchers.Unconfined]
 * does, runs its calls outside that transaction, on [Dispatchers.IO] when this dispatcher would run them on that
 * thread.
 *
 * @param dispatcher The [CoroutineDispatcher] that runs the database work of coroutine calls
 * @return Database connection configuration builder
 */
@CheckResult
fun DatabaseSetupBuilder.dispatchCoroutineQueriesOn(dispatcher: CoroutineDispatcher) =
  ReactiveAdapterSupport.adapterSetting(
    builder = this,
    key = QUERY_DISPATCHER,
    value = dispatcher
  )

internal fun DbConnection.queryDispatcher() = ReactiveAdapterSupport.adapterSetting(
  connection = this,
  key = QUERY_DISPATCHER
) ?: Dispatchers.IO

internal fun DbConnection.queryDispatcherFor(context: CoroutineContext) = when {
  runsInsideOtherTransaction(context) -> Dispatchers.IO
  else -> queryDispatcher()
}

internal suspend inline fun <T> DbConnection.withQueryDispatcher(
  crossinline block: CoroutineScope.() -> T
) = withContext(queryDispatcherFor(currentCoroutineContext())) {
  waitingForWriter { withOwnerOf(coroutineContext) { block() } }
}

internal suspend inline fun <T> DbConnection.waitingForWriter(block: () -> T): T {
  var grant: WriterWait? = null
  while (true) {
    val call = ReactiveAdapterSupport.beginWriterCall(
      connection = this,
      grant = grant
    )
    val wait = try {
      return block()
    } catch (wait: WriterWait) {
      wait
    } finally {
      ReactiveAdapterSupport.endWriterCall(call)
    }
    wait.await()
    grant = wait
  }
}

internal suspend fun WriterWait.await() {
  val granted = withTimeoutOrNull(ReactiveAdapterSupport.writerWaitTimeout(this)) {
    suspendCancellableCoroutine { continuation ->
      val cancel = { _: Throwable, _: Unit, _: CoroutineContext -> ReactiveAdapterSupport.cancelWriterWait(this@await) }
      continuation.invokeOnCancellation { ReactiveAdapterSupport.cancelWriterWait(this@await) }
      val alreadyGranted = ReactiveAdapterSupport.awaitWriter(this@await) {
        val dispatcher = continuation.context[ContinuationInterceptor] as? CoroutineDispatcher
        when {
          dispatcher != null && dispatcher.isDispatchNeeded(continuation.context) -> continuation.resume(Unit, cancel)
          else -> Dispatchers.IO.dispatch(continuation.context) { continuation.resume(Unit, cancel) }
        }
      }
      if (alreadyGranted) {
        continuation.resume(Unit, cancel)
      }
    }
  }
  if (granted == null) {
    ReactiveAdapterSupport.cancelWriterWait(this)
    throw ReactiveAdapterSupport.writerTimeout(this)
  }
}
