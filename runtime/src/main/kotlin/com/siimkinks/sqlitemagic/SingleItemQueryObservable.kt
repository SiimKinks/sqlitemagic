package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.OperatorRunSingleItemQuery.Companion.emitNotNull
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Single

/**
 * An [Observable] of [Query] which offers single item query specific convenience operators.
 *
 * @param R Mapped query return value type
 */
open class SingleItemQueryObservable<R>(
  private val upstream: Observable<Query<R>>
) : Observable<Query<R>>() {
  override fun subscribeActual(observer: Observer<in Query<R>>) = upstream.subscribe(observer)

  /**
   * Transform each emitted [Query] which returns a single row to `R`.
   *
   * This operator ignores queries with empty result or a `null` scalar result.
   */
  @CheckResult
  fun runQuery(): Observable<R & Any> = lift(emitNotNull())

  /**
   * Transform each emitted [Query] which returns a single row to `R`.
   *
   * This operator emits [defaultValue] if query result is empty or the selected scalar value is `null`.
   * If [defaultValue] is `null`, those query results are skipped.
   *
   * @param defaultValue Value emitted if [Query] result is empty or the selected scalar value is `null`
   */
  @CheckResult
  fun runQueryOrDefault(defaultValue: R): Observable<R & Any> = lift(OperatorRunSingleItemQuery(defaultValue))

  /**
   * Transform only the first emitted [Query] which returns a single row to `R`.
   *
   * This operator ignores query with an empty result or a `null` scalar result, resulting in an empty stream.
   */
  @CheckResult
  fun runQueryOnce(): Maybe<R & Any> =
    take(1)
      .lift(emitNotNull())
      .elementAt(0)

  /**
   * Transform only the first emitted [Query] which returns a single row to `R`.
   *
   * This operator emits [defaultValue] if query result is empty or the selected scalar value is `null`.
   * If [defaultValue] is `null`, an empty or null first query result produces a [NoSuchElementException].
   *
   * @param defaultValue Value emitted if [Query] result is empty or the selected scalar value is `null`
   */
  @CheckResult
  fun runQueryOnceOrDefault(defaultValue: R): Single<R & Any> =
    take(1)
      .lift(OperatorRunSingleItemQuery(defaultValue))
      .firstOrError()
}
