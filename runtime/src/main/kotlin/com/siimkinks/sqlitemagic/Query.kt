package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import io.reactivex.Maybe
import io.reactivex.Scheduler
import io.reactivex.functions.Function

/** An executable query. */
abstract class Query<T> internal constructor(
  internal val dbConnection: DbConnectionImpl
) {
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
  fun runBlocking(): T? = map(rawQuery(inStream = true))

  /**
   * Creates [Maybe] that when subscribed to executes the query against a database
   * and emits query result to downstream.
   *
   * The resulting stream will be empty if query result is `null`.
   *
   * **Scheduler:** `run` does not operate by default on a particular [Scheduler].
   *
   * @return Deferred [Maybe] that when subscribed to executes the query and emits
   * its result to downstream
   * @see [runBlocking]
   */
  @CheckResult
  fun run(): Maybe<T> = Maybe.create { emitter ->
    val cursor = rawQuery(inStream = true)
    if (emitter.isDisposed) {
      cursor?.close()
      return@create
    }
    val result = map(cursor)
    when {
      result != null -> emitter.onSuccess(result)
      else -> emitter.onComplete()
    }
  }

  /**
   * Executes this query against a database.
   *
   * @param inStream Whether query is executed in observable stream or synchronously
   * @return Query result, maybe `null`
  */
  @CallSuper
  protected open fun rawQuery(inStream: Boolean): Cursor? {
    if (inStream && dbConnection.transactions.get() != null) {
      throw IllegalStateException("Cannot execute observable query in a transaction.")
    }
    return null
  }

  protected abstract fun map(cursor: Cursor?): T?

  /** A query implementation that can be used as an RxJava trigger mapper. */
  internal abstract class DatabaseQuery<T, E> internal constructor(
    dbConnection: DbConnectionImpl,
    @JvmField
    internal val mapper: Mapper<E>?
  ) : Query<T>(dbConnection), Function<Set<String>, Query<T>> {
    override fun apply(ignored: Set<String>): Query<T> = this
  }

  fun interface Mapper<R> {
    fun apply(cursor: Cursor): R
  }
}
