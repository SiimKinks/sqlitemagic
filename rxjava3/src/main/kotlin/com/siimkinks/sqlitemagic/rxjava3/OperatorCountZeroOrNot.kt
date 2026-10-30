package com.siimkinks.sqlitemagic.rxjava3

import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.rxjava3.core.ObservableOperator
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.exceptions.Exceptions
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins

internal class OperatorCountZeroOrNot private constructor(
  private val countZero: Boolean
) : ObservableOperator<Boolean, Query<Long>> {
  override fun apply(observer: Observer<in Boolean>): Observer<in Query<Long>> = MappingObserver(
    downstream = observer,
    countZero = countZero
  )

  private class MappingObserver(
    private val downstream: Observer<in Boolean>,
    private val countZero: Boolean
  ) : DisposableObserver<Query<Long>>() {
    private var done = false

    override fun onStart() = downstream.onSubscribe(this)

    override fun onNext(query: Query<Long>) {
      if (done || isDisposed) return
      try {
        val count = ReactiveAdapterSupport.map(
          query = query,
          cursor = ReactiveAdapterSupport.openCursor(query)
        )
        if (!isDisposed) {
          downstream.onNext((count > 0) xor countZero)
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
    val countZero = OperatorCountZeroOrNot(countZero = true)
    val countNotZero = OperatorCountZeroOrNot(countZero = false)
  }
}
