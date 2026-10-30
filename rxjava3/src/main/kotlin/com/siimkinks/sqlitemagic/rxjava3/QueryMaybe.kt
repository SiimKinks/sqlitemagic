package com.siimkinks.sqlitemagic.rxjava3

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.MaybeObserver
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.exceptions.Exceptions
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates [Maybe] that when subscribed to executes the query against a database
 * and emits query result to downstream.
 *
 * The resulting stream will be empty if query result is `null`.
 *
 * A [Cursor] result that is emitted must be closed by the subscriber. If the subscription is disposed before the
 * result is emitted, the [Cursor] is closed instead.
 *
 * **Scheduler:** `run` does not operate by default on a particular [Scheduler].
 *
 * @return Deferred [Maybe] that when subscribed to executes the query and emits its result to downstream
 * @see [Query.runBlocking]
 */
@CheckResult
fun <T> Query<T>.run(): Maybe<T & Any> = RxJavaPlugins.onAssembly(QueryMaybe(this))

private class QueryMaybe<T>(private val query: Query<T>) : Maybe<T & Any>() {
  override fun subscribeActual(observer: MaybeObserver<in T & Any>) {
    val subscription = QueryDisposable()
    observer.onSubscribe(subscription)
    if (subscription.isDisposed) return
    try {
      val cursor = ReactiveAdapterSupport.openCursor(query)
      if (subscription.isDisposed) {
        cursor.close()
        return
      }
      val result = ReactiveAdapterSupport.map(
        query = query,
        cursor = cursor
      )
      when {
        subscription.compareAndSet(false, true) -> when {
          result != null -> observer.onSuccess(result)
          else -> observer.onComplete()
        }
        result != null -> ReactiveAdapterSupport.discardResult(
          query = query,
          result = result
        )
      }
    } catch (failure: Throwable) {
      Exceptions.throwIfFatal(failure)
      when {
        subscription.compareAndSet(false, true) -> observer.onError(failure)
        else -> RxJavaPlugins.onError(failure)
      }
    }
  }

  private class QueryDisposable : AtomicBoolean(), Disposable {
    override fun dispose() = set(true)

    override fun isDisposed() = get()
  }
}
