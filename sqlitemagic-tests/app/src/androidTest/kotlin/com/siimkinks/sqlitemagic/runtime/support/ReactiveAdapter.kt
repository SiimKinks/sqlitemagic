package com.siimkinks.sqlitemagic.runtime.support

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.Unique
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
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal const val UNBOUNDED = Long.MAX_VALUE

internal val reactiveAdapters = listOf<ReactiveAdapter>(RxJava2Adapter, RxJava3Adapter)

internal fun withReactiveAdapters(cases: List<*>) = cases.flatMap { case ->
  reactiveAdapters.map { adapter -> arrayOf(case, adapter) }
}

interface ReactiveAdapter {
  fun <T> runQuery(query: CompiledSelect<T>): ReactiveStream<List<T>>

  fun <T> runQuery(query: SelectQuery<T, *>) = runQuery(query.compile())

  fun <T : Any> runQuery(query: CompiledFirst<T>): ReactiveStream<T>

  fun <T : Any> runQueryOrDefault(
    query: CompiledFirst<T>,
    defaultValue: T
  ): ReactiveStream<T>

  fun <T> runQueryOnce(query: CompiledSelect<T>): ReactiveStream<List<T>>

  fun <T> runQueryOnce(query: SelectQuery<T, *>) = runQueryOnce(query.compile())

  fun <T : Any> runQueryOnce(query: CompiledFirst<T>): ReactiveStream<T>

  fun runQueryOnce(query: CompiledCount): ReactiveStream<Long>

  fun <T : Any> runQueryOnceOrDefault(
    query: CompiledFirst<T>,
    defaultValue: T
  ): ReactiveStream<T>

  fun isZero(query: CompiledCount): ReactiveStream<Boolean>

  fun isNotZero(query: CompiledCount): ReactiveStream<Boolean>

  fun <T> observe(query: CompiledSelect<T>): ReactiveStream<Query<List<T>>>

  fun <T> observe(query: SelectQuery<T, *>) = observe(query.compile())

  fun observe(builder: EntityInsertBuilder): ReactiveOperation<EntityInsertResult>

  fun observe(builder: EntityBulkInsertBuilder): ReactiveCompletion

  fun observe(builder: EntityUpdateBuilder): ReactiveCompletion

  fun <P> observe(
    builder: EntityUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveCompletion

  fun observe(builder: EntityBulkUpdateBuilder): ReactiveCompletion

  fun <P> observe(
    builder: EntityBulkUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveCompletion

  fun observe(builder: EntityPersistBuilder): ReactiveOperation<EntityPersistResult>

  fun <P> observe(
    builder: EntityPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveOperation<EntityPersistResult>

  fun observe(builder: EntityBulkPersistBuilder): ReactiveCompletion

  fun <P> observe(
    builder: EntityBulkPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveCompletion

  fun observe(builder: EntityDeleteBuilder): ReactiveOperation<Int>

  fun <P> observe(
    builder: EntityDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveOperation<Int>

  fun observe(builder: EntityBulkDeleteBuilder): ReactiveOperation<Int>

  fun <P> observe(
    builder: EntityBulkDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ): ReactiveOperation<Int>

  fun observe(builder: EntityDeleteTableBuilder): ReactiveOperation<Int>
}

interface ReactiveStream<T : Any> {
  fun <R : Any> map(transform: (T) -> R): ReactiveStream<R>

  fun filter(predicate: (T) -> Boolean): ReactiveStream<T>

  fun take(count: Long): ReactiveStream<T>

  fun onEach(action: (T) -> Unit): ReactiveStream<T>

  fun subscribe(
    recording: Recording<T>,
    initialDemand: Long = UNBOUNDED
  )
}

internal fun <T : Any> ReactiveStream<T>.record(initialDemand: Long = UNBOUNDED) = Recording<T>().also {
  subscribe(
    recording = it,
    initialDemand = initialDemand
  )
}

interface ReactiveOperation<R> {
  fun subscribe(recording: Recording<R>)

  fun await(): R
}

internal fun <R> ReactiveOperation<R>.record() = Recording<R>().also(::subscribe)

interface ReactiveCompletion : ReactiveOperation<Unit> {
  fun andThen(next: ReactiveCompletion): ReactiveCompletion
}

interface RecordedSubscription {
  fun request(count: Long)

  fun cancel()
}

class Recording<T> {
  private val recordedValues = CopyOnWriteArrayList<T>()

  private val failures = CopyOnWriteArrayList<Throwable>()

  private val subscriptions = AtomicInteger()

  private val completions = AtomicInteger()

  @Volatile
  private var subscription: RecordedSubscription? = null

  @Volatile
  private var disposed = false

  private var tag: String? = null

  val values: List<T> get() = recordedValues

  fun onSubscribe(subscription: RecordedSubscription) {
    subscriptions.incrementAndGet()
    this.subscription = subscription
    if (disposed) {
      subscription.cancel()
    }
  }

  fun onNext(value: T) {
    recordedValues += value
  }

  fun onError(error: Throwable) {
    failures += error
  }

  fun onComplete() {
    completions.incrementAndGet()
  }

  fun request(count: Long) = checkNotNull(subscription).request(count)

  fun dispose() {
    disposed = true
    subscription?.cancel()
  }

  fun withTag(tag: String) = apply { this.tag = tag }

  fun assertValuesOnly(vararg expected: T) = assertSignals(
    values = expected.asList(),
    completions = 0
  )

  fun assertResult(vararg expected: T) = assertSignals(
    values = expected.asList(),
    completions = 1
  )

  fun assertEmpty() = assertValuesOnly()

  fun assertValueCount(count: Int) = apply {
    assertWithTag()
      .that(values)
      .hasSize(count)
  }

  fun assertNoErrors() = apply {
    assertWithTag()
      .that(failures)
      .isEmpty()
  }

  fun assertComplete() = apply {
    assertWithTag()
      .that(completions.get())
      .isEqualTo(1)
  }

  fun assertFailure(matches: (Throwable) -> Boolean) = apply {
    val failure = failures.singleOrNull()
    assertWithTag()
      .that(signals())
      .isEqualTo(
        Signals(
          subscriptions = 1,
          values = emptyList<T>(),
          failures = listOfNotNull(failure),
          completions = 0
        )
      )
    assertWithTag()
      .that(failure?.let(matches))
      .isTrue()
  }

  private fun assertSignals(
    values: List<T>,
    completions: Int
  ) = apply {
    assertWithTag()
      .that(signals())
      .isEqualTo(
        Signals(
          subscriptions = 1,
          values = values,
          failures = emptyList(),
          completions = completions
        )
      )
  }

  private fun signals() = Signals(
    subscriptions = subscriptions.get(),
    values = values,
    failures = failures,
    completions = completions.get()
  )

  private fun assertWithTag() = when (val tag = tag) {
    null -> assertWithMessage("recorded signals")
    else -> assertWithMessage(tag)
  }

  private data class Signals(
    val subscriptions: Int,
    val values: List<*>,
    val failures: List<Throwable>,
    val completions: Int
  )
}
