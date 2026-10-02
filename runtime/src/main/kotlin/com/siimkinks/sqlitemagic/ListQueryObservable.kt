package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Single

/**
 * An [Observable] of [Query] which offers list query specific convenience operators.
 *
 * @param R Mapped query result list element type
 */
open class ListQueryObservable<R>(
  private val upstream: Observable<Query<List<R>>>
) : Observable<Query<List<R>>>() {
  override fun subscribeActual(observer: Observer<in Query<List<R>>>) = upstream.subscribe(observer)

  /**
   * Transform each emitted [Query] to a `List<R>`.
   *
   * Be careful using this operator as it will always consume the entire cursor and create objects
   * for each row, every time this observable emits a new query. On tables whose queries update
   * frequently or very large result sets this can result in the creation of many objects.
   */
  @CheckResult
  fun runQuery(): Observable<List<R>> = lift(OperatorRunListQuery.instance())

  /** Transform only the first emitted [Query] to a `List<R>`. */
  @CheckResult
  fun runQueryOnce(): Single<List<R>> =
    take(1)
      .lift(OperatorRunListQuery.instance())
      .firstOrError()
}
