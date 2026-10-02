package com.siimkinks.sqlitemagic

import io.reactivex.ObservableOperator
import io.reactivex.Observer
import io.reactivex.exceptions.Exceptions
import io.reactivex.observers.DisposableObserver
import io.reactivex.plugins.RxJavaPlugins

/** A null [defaultValue] suppresses empty query results and null scalar values. */
internal class OperatorRunSingleItemQuery<T>(
  private val defaultValue: T? = null
) : ObservableOperator<T & Any, Query<T>> {
  override fun apply(observer: Observer<in T & Any>): Observer<in Query<T>> = MappingObserver(
    downstream = observer,
    defaultValue = defaultValue
  )

  private class MappingObserver<T>(
    private val downstream: Observer<in T & Any>,
    private val defaultValue: T?
  ) : DisposableObserver<Query<T>>() {
    override fun onStart() = downstream.onSubscribe(this)

    override fun onNext(query: Query<T>) {
      try {
        val item = query.runBlocking() ?: defaultValue
        if (!isDisposed && item != null) {
          downstream.onNext(item)
        }
      } catch (error: Throwable) {
        Exceptions.throwIfFatal(error)
        onError(error)
      }
    }

    override fun onComplete() {
      if (!isDisposed) {
        downstream.onComplete()
      }
    }

    override fun onError(error: Throwable) = when {
      isDisposed -> RxJavaPlugins.onError(error)
      else -> downstream.onError(error)
    }
  }

  companion object {
    private val withoutDefault = OperatorRunSingleItemQuery<Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> emitNotNull(): OperatorRunSingleItemQuery<T> = withoutDefault as OperatorRunSingleItemQuery<T>
  }
}
