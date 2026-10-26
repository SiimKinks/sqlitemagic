package com.siimkinks.sqlitemagic

import io.reactivex.ObservableOperator
import io.reactivex.Observer
import io.reactivex.exceptions.Exceptions
import io.reactivex.observers.DisposableObserver
import io.reactivex.plugins.RxJavaPlugins

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
    private var done = false

    override fun onStart() = downstream.onSubscribe(this)

    override fun onNext(query: Query<T>) {
      if (done || isDisposed) return
      try {
        val result = query.runBlocking()
        if (isDisposed) {
          if (result != null) query.discardResult(result)
          return
        }
        val item = result ?: defaultValue
        if (item != null) {
          downstream.onNext(item)
        }
      } catch (error: Throwable) {
        Exceptions.throwIfFatal(error)
        when {
          done || isDisposed -> RxJavaPlugins.onError(error)
          else -> {
            done = true
            dispose()
            downstream.onError(error)
          }
        }
      }
    }

    override fun onComplete() {
      if (!done && !isDisposed) {
        done = true
        downstream.onComplete()
      }
    }

    override fun onError(error: Throwable) = when {
      done || isDisposed -> RxJavaPlugins.onError(error)
      else -> {
        done = true
        downstream.onError(error)
      }
    }
  }

  companion object {
    private val withoutDefault = OperatorRunSingleItemQuery<Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> emitNotNull(): OperatorRunSingleItemQuery<T> = withoutDefault as OperatorRunSingleItemQuery<T>
  }
}
