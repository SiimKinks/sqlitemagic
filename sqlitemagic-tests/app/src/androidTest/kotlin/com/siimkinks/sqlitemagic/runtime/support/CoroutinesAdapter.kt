package com.siimkinks.sqlitemagic.runtime.support

import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.isNotZero
import com.siimkinks.sqlitemagic.coroutines.isZero
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQuery
import com.siimkinks.sqlitemagic.coroutines.runQueryOnce
import com.siimkinks.sqlitemagic.coroutines.runQueryOnceOrDefault
import com.siimkinks.sqlitemagic.coroutines.runQueryOrDefault
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal object CoroutinesAdapter : ReactiveAdapter {
  override fun <T> runQuery(query: CompiledSelect<T>) = query
    .observe()
    .runQuery()
    .stream()

  override fun <T : Any> runQuery(query: CompiledFirst<T>) = query
    .observe()
    .runQuery()
    .stream()

  override fun <T : Any> runQueryOrDefault(
    query: CompiledFirst<T>,
    defaultValue: T
  ) = query
    .observe()
    .runQueryOrDefault(defaultValue)
    .stream()

  override fun <T> runQueryOnce(query: CompiledSelect<T>) = query
    .observe()
    .let { queries -> flow { emit(queries.runQueryOnce()) } }
    .stream()

  override fun <T : Any> runQueryOnce(query: CompiledFirst<T>) = query
    .observe()
    .let { queries -> flow { queries.runQueryOnce()?.let { emit(it) } } }
    .stream()

  override fun runQueryOnce(query: CompiledCount) = query
    .observe()
    .let { queries -> flow { queries.runQueryOnce()?.let { emit(it) } } }
    .stream()

  override fun <T : Any> runQueryOnceOrDefault(
    query: CompiledFirst<T>,
    defaultValue: T
  ) = query
    .observe()
    .let { queries -> flow { emit(queries.runQueryOnceOrDefault(defaultValue)) } }
    .stream()

  override fun isZero(query: CompiledCount) = query
    .observe()
    .isZero()
    .stream()

  override fun isNotZero(query: CompiledCount) = query
    .observe()
    .isNotZero()
    .stream()

  override fun <T> observe(query: CompiledSelect<T>) = query
    .observe()
    .stream()

  override fun observe(builder: EntityInsertBuilder) = operation { builder.await() }

  override fun observe(builder: EntityBulkInsertBuilder) = completion { builder.await() }

  override fun observe(builder: EntityUpdateBuilder) = completion { builder.await() }

  override fun <P> observe(
    builder: EntityUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = completion { builder.await(byColumn) }

  override fun observe(builder: EntityBulkUpdateBuilder) = completion { builder.await() }

  override fun <P> observe(
    builder: EntityBulkUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = completion { builder.await(byColumn) }

  override fun observe(builder: EntityPersistBuilder) = operation { builder.await() }

  override fun <P> observe(
    builder: EntityPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = operation { builder.await(byColumn) }

  override fun observe(builder: EntityBulkPersistBuilder) = completion { builder.await() }

  override fun <P> observe(
    builder: EntityBulkPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = completion { builder.await(byColumn) }

  override fun observe(builder: EntityDeleteBuilder) = operation { builder.await() }

  override fun <P> observe(
    builder: EntityDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = operation { builder.await(byColumn) }

  override fun observe(builder: EntityBulkDeleteBuilder) = operation { builder.await() }

  override fun <P> observe(
    builder: EntityBulkDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = operation { builder.await(byColumn) }

  override fun observe(builder: EntityDeleteTableBuilder) = operation { builder.await() }

  override fun toString() = "coroutines"
}

private fun <T : Any> Flow<T>.stream(): ReactiveStream<T> = CoroutinesStream(this)

private fun <R> operation(call: suspend () -> R): ReactiveOperation<R> = CoroutinesOperation(call)

private fun completion(call: suspend () -> Boolean): ReactiveCompletion = CoroutinesCompletion { call() }

private class CoroutinesStream<T : Any>(private val source: Flow<T>) : ReactiveStream<T> {
  override fun <R : Any> map(transform: (T) -> R) = source
    .map(transform)
    .stream()

  override fun filter(predicate: (T) -> Boolean) = source
    .filter(predicate)
    .stream()

  override fun take(count: Long) = source
    .take(count.toInt())
    .stream()

  override fun onEach(action: (T) -> Unit) = source
    .onEach(action)
    .stream()

  override fun subscribe(
    recording: Recording<T>,
    initialDemand: Long
  ) {
    val demand = when (initialDemand) {
      UNBOUNDED -> null
      else -> Channel<Unit>(UNLIMITED).also { permits -> permits.grant(initialDemand) }
    }
    record(
      recording = recording,
      request = { count -> demand?.grant(count) }
    ) {
      source.collect { value ->
        demand?.receive()
        recording.onNext(value)
      }
    }
  }
}

private class CoroutinesOperation<R>(private val call: suspend () -> R) : ReactiveOperation<R> {
  override fun subscribe(recording: Recording<R>) = record(recording) { recording.onNext(call()) }

  override fun await() = runBlocking { call() }
}

private class CoroutinesCompletion(private val call: suspend () -> Unit) : ReactiveCompletion {
  override fun subscribe(recording: Recording<Unit>) = record(
    recording = recording,
    block = call
  )

  override fun await() = runBlocking { call() }

  override fun andThen(next: ReactiveCompletion) = CoroutinesCompletion {
    call()
    (next as CoroutinesCompletion).call()
  }
}

private fun Channel<Unit>.grant(count: Long) = repeat(count.toInt()) { trySend(Unit) }

private fun <T> record(
  recording: Recording<T>,
  request: (Long) -> Unit = {},
  block: suspend () -> Unit
) {
  val collection = CoroutineScope(Dispatchers.Unconfined).launch(start = LAZY) {
    try {
      block()
      recording.onComplete()
    } catch (failure: Throwable) {
      if (isActive) {
        recording.onError(failure)
      }
    }
  }
  recording.onSubscribe(
    object : RecordedSubscription {
      override fun request(count: Long) = request(count)

      override fun cancel() = collection.cancel()
    }
  )
  collection.start()
}
