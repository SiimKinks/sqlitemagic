package com.siimkinks.sqlitemagic.runtime.support

import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
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
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.rxjava3.observe
import io.reactivex.rxjava3.core.BackpressureStrategy.LATEST
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableObserver
import io.reactivex.rxjava3.core.FlowableSubscriber
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import org.reactivestreams.Subscription

internal object RxJava3Adapter : ReactiveAdapter {
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
    .runQueryOnce()
    .toObservable()
    .stream()

  override fun <T : Any> runQueryOnce(query: CompiledFirst<T>) = query
    .observe()
    .runQueryOnce()
    .toObservable()
    .stream()

  override fun runQueryOnce(query: CompiledCount) = query
    .observe()
    .runQueryOnce()
    .toObservable()
    .stream()

  override fun <T : Any> runQueryOnceOrDefault(
    query: CompiledFirst<T>,
    defaultValue: T
  ) = query
    .observe()
    .runQueryOnceOrDefault(defaultValue)
    .toObservable()
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

  override fun observe(builder: EntityInsertBuilder) = builder
    .observe()
    .operation()

  override fun observe(builder: EntityBulkInsertBuilder) = builder
    .observe()
    .completion()

  override fun observe(builder: EntityUpdateBuilder) = builder
    .observe()
    .completion()

  override fun <P> observe(
    builder: EntityUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .completion()

  override fun observe(builder: EntityBulkUpdateBuilder) = builder
    .observe()
    .completion()

  override fun <P> observe(
    builder: EntityBulkUpdateByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .completion()

  override fun observe(builder: EntityPersistBuilder) = builder
    .observe()
    .operation()

  override fun <P> observe(
    builder: EntityPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .operation()

  override fun observe(builder: EntityBulkPersistBuilder) = builder
    .observe()
    .completion()

  override fun <P> observe(
    builder: EntityBulkPersistByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .completion()

  override fun observe(builder: EntityDeleteBuilder) = builder
    .observe()
    .operation()

  override fun <P> observe(
    builder: EntityDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .operation()

  override fun observe(builder: EntityBulkDeleteBuilder) = builder
    .observe()
    .operation()

  override fun <P> observe(
    builder: EntityBulkDeleteByColumnBuilder<P>,
    byColumn: Unique<P>
  ) = builder
    .observe(byColumn = byColumn)
    .operation()

  override fun observe(builder: EntityDeleteTableBuilder) = builder
    .observe()
    .operation()

  override fun toString() = "rxjava3"
}

private fun <T : Any> Observable<T>.stream(): ReactiveStream<T> = RxJava3Stream(this)

private fun <R : Any> Single<R>.operation(): ReactiveOperation<R> = RxJava3Operation(this)

private fun Completable.completion(): ReactiveCompletion = RxJava3Completion(this)

private class RxJava3Stream<T : Any>(private val source: Observable<T>) : ReactiveStream<T> {
  override fun <R : Any> map(transform: (T) -> R) = source
    .map(transform)
    .stream()

  override fun filter(predicate: (T) -> Boolean) = source
    .filter(predicate)
    .stream()

  override fun take(count: Long) = source
    .take(count)
    .stream()

  override fun onEach(action: (T) -> Unit) = source
    .doOnNext(action)
    .stream()

  override fun subscribe(
    recording: Recording<T>,
    initialDemand: Long
  ) = when (initialDemand) {
    UNBOUNDED -> source.subscribe(RxJava3Recorder(recording))
    else -> source
      .toFlowable(LATEST)
      .subscribe(
        RxJava3PacedRecorder(
          recording = recording,
          initialDemand = initialDemand
        )
      )
  }
}

private class RxJava3Operation<R : Any>(private val source: Single<R>) : ReactiveOperation<R> {
  override fun subscribe(recording: Recording<R>) = source.subscribe(RxJava3Recorder(recording))

  override fun await(): R = source.blockingGet()
}

private class RxJava3Completion(private val source: Completable) : ReactiveCompletion {
  override fun subscribe(recording: Recording<Unit>) = source.subscribe(RxJava3Recorder(recording))

  override fun await() = source.blockingAwait()

  override fun andThen(next: ReactiveCompletion) = source
    .andThen((next as RxJava3Completion).source)
    .completion()
}

private class RxJava3Recorder<T : Any>(
  private val recording: Recording<T>
) : Observer<T>, SingleObserver<T>, CompletableObserver {
  override fun onSubscribe(d: Disposable) = recording.onSubscribe(
    object : RecordedSubscription {
      override fun request(count: Long) = Unit

      override fun cancel() = d.dispose()
    }
  )

  override fun onNext(t: T) = recording.onNext(t)

  override fun onSuccess(t: T) {
    recording.onNext(t)
    recording.onComplete()
  }

  override fun onError(e: Throwable) = recording.onError(e)

  override fun onComplete() = recording.onComplete()
}

private class RxJava3PacedRecorder<T : Any>(
  private val recording: Recording<T>,
  private val initialDemand: Long
) : FlowableSubscriber<T> {
  override fun onSubscribe(s: Subscription) {
    recording.onSubscribe(
      object : RecordedSubscription {
        override fun request(count: Long) = s.request(count)

        override fun cancel() = s.cancel()
      }
    )
    if (initialDemand > 0) {
      s.request(initialDemand)
    }
  }

  override fun onNext(t: T) = recording.onNext(t)

  override fun onError(t: Throwable) = recording.onError(t)

  override fun onComplete() = recording.onComplete()
}
