package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import io.reactivex.Maybe
import io.reactivex.MaybeObserver
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.Exceptions
import io.reactivex.functions.Function
import io.reactivex.plugins.RxJavaPlugins
import java.util.concurrent.atomic.AtomicBoolean

/** An executable query. */
abstract class Query<T> internal constructor(
  internal val dbConnection: DbConnectionImpl?
) {
  internal fun resolveConnection() = dbConnection ?: SqliteMagic.getDefaultDbConnection()

  /**
   * Execute this query against a database and return the resulting data.
   * This method runs synchronously in the calling thread.
   *
   * Depending on the query type and result data type structure this method
   * can return `null`.
   * Rule of thumb is: If the result type is [java.util.List] it will
   * never be `null` otherwise it might be `null`.
   *
   * @return This query result
   */
  @CheckResult
  @WorkerThread
  fun runBlocking(): T? = resolveConnection()
    .let { dbConnection ->
      map(
        cursor = rawQuery(
          inStream = true,
          dbConnection = dbConnection
        ),
        dbConnection = dbConnection
      )
    }

  /**
   * Creates [Maybe] that when subscribed to executes the query against a database
   * and emits query result to downstream.
   *
   * The resulting stream will be empty if query result is `null`.
   *
   * A cursor result transfers to the subscriber when delivery wins against cancellation.
   * Cancellation cooperates with acquisition and mapping; it does not close a cursor being
   * mapped or one already delivered. A terminal callback that wins may overlap cancellation.
   *
   * **Scheduler:** `run` does not operate by default on a particular [Scheduler].
   *
   * @return Deferred [Maybe] that when subscribed to executes the query and emits
   * its result to downstream
   * @see [runBlocking]
   */
  @CheckResult
  fun run(): Maybe<T> = RxJavaPlugins.onAssembly(QueryMaybe(this))

  /**
   * Executes this query against a database.
   *
   * @param inStream Whether query is executed in observable stream or synchronously
   * @return Query result, maybe `null`
   */
  @CallSuper
  internal open fun rawQuery(
    inStream: Boolean,
    dbConnection: DbConnectionImpl = resolveConnection()
  ): Cursor? = when {
    inStream && dbConnection.hasActiveTransaction -> error("Cannot execute observable query in a transaction.")
    else -> null
  }

  /** Releases a result whose ownership could not be transferred to its subscriber. */
  internal open fun discardResult(result: T) = Unit

  internal abstract fun map(
    cursor: Cursor?,
    dbConnection: DbConnectionImpl
  ): T?

  /** A query implementation that can be used as an RxJava trigger mapper. */
  internal abstract class DatabaseQuery<T, E> internal constructor(
    dbConnection: DbConnectionImpl?,
    internal val mapper: Mapper<E>?
  ) : Query<T>(dbConnection), Function<Set<String>, Query<T>> {
    override fun apply(ignored: Set<String>): Query<T> = this
  }

  fun interface Mapper<R> {
    fun apply(cursor: Cursor): R
  }
}

private class QueryMaybe<T>(private val query: Query<T>) : Maybe<T>() {
  override fun subscribeActual(observer: MaybeObserver<in T>) {
    val subscription = QueryDisposable()
    observer.onSubscribe(subscription)
    if (subscription.isDisposed) return
    try {
      val dbConnection = query.resolveConnection()
      val cursor = query.rawQuery(
        inStream = true,
        dbConnection = dbConnection
      )
      if (subscription.isDisposed) {
        cursor?.close()
        return
      }
      val result = query.map(
        cursor = cursor,
        dbConnection = dbConnection
      )
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
