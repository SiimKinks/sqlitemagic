package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import io.reactivex.Maybe
import io.reactivex.MaybeObserver
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.Exceptions
import io.reactivex.plugins.RxJavaPlugins
import java.util.concurrent.atomic.AtomicBoolean

/** An executable query. */
abstract class Query<T> internal constructor(
  internal val dbConnection: DbConnectionImpl?
) {
  internal fun resolveConnection() = dbConnection.orDefault()

  /**
   * Execute this query against a database and return the resulting data.
   * This method runs synchronously in the calling thread.
   *
   * Depending on the query type and result data type structure this method
   * can return `null`.
   * Rule of thumb is: If the result type is [List] it will
   * never be `null` otherwise it might be `null`.
   *
   * @return This query result
   * @throws IllegalStateException if called inside a transaction
   */
  @CheckResult
  @WorkerThread
  fun runBlocking(): T? = map(openCursor())

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
   * @see [runBlocking]
   */
  @CheckResult
  fun run(): Maybe<T> = RxJavaPlugins.onAssembly(QueryMaybe(this))

  internal fun openCursor() = resolveConnection().let { connection ->
    check(!connection.hasActiveTransaction) { "Cannot execute observable query in a transaction." }
    rawQuery(connection)
  }

  internal abstract fun rawQuery(dbConnection: DbConnectionImpl): Cursor

  internal open fun discardResult(result: T) = Unit

  internal abstract fun map(cursor: Cursor, isDisposed: () -> Boolean = NOT_DISPOSED): T
}

internal val NOT_DISPOSED: () -> Boolean = { false }

private class QueryMaybe<T>(private val query: Query<T>) : Maybe<T>() {
  override fun subscribeActual(observer: MaybeObserver<in T>) {
    val subscription = QueryDisposable()
    observer.onSubscribe(subscription)
    if (subscription.isDisposed) return
    try {
      val cursor = query.openCursor()
      if (subscription.isDisposed) {
        cursor.close()
        return
      }
      val result = query.map(cursor)
      when {
        subscription.compareAndSet(false, true) -> when {
          result != null -> observer.onSuccess(result)
          else -> observer.onComplete()
        }
        result != null -> query.discardResult(result)
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
