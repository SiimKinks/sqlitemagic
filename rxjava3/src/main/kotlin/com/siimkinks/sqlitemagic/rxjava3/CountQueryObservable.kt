package com.siimkinks.sqlitemagic.rxjava3

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.rxjava3.OperatorCountZeroOrNot.Companion.countNotZero
import com.siimkinks.sqlitemagic.rxjava3.OperatorCountZeroOrNot.Companion.countZero
import com.siimkinks.sqlitemagic.rxjava3.OperatorRunSingleItemQuery.Companion.emitNotNull
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Single

/** An [Observable] of [Query] which offers count query specific convenience operators. */
open class CountQueryObservable internal constructor(
  private val upstream: Observable<Query<Long>>
) : Observable<Query<Long>>() {
  override fun subscribeActual(observer: Observer<in Query<Long>>) = upstream.subscribe(observer)

  /** Runs each emitted [Query] and propagates its count to downstream. */
  @CheckResult
  fun runQuery(): Observable<Long> = lift(emitNotNull())

  /**
   * Runs only the first emitted [Query] and propagates its count to downstream.
   *
   * The result fails with [NoSuchElementException] if the upstream completes without emitting, as it does on a closed
   * connection.
   */
  @CheckResult
  fun runQueryOnce(): Single<Long> =
    firstQuery(upstream)
      .lift(emitNotNull())
      .firstOrError()

  /**
   * Runs each emitted [Query] and propagates `true` to downstream if
   * [Query] result is equal to zero; `false` otherwise.
   */
  @CheckResult
  fun isZero(): Observable<Boolean> = lift(countZero)

  /**
   * Runs each emitted [Query] and propagates `true` to downstream if
   * [Query] result is not equal to zero; `false` otherwise.
   */
  @CheckResult
  fun isNotZero(): Observable<Boolean> = lift(countNotZero)
}
