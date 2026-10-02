package com.siimkinks.sqlitemagic

import io.reactivex.ObservableOperator
import io.reactivex.Observer
import io.reactivex.exceptions.Exceptions
import io.reactivex.observers.DisposableObserver
import io.reactivex.plugins.RxJavaPlugins

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
    override fun onStart() = downstream.onSubscribe(this)

    override fun onNext(query: Query<Long>) {
      try {
        val count = checkNotNull(query.runBlocking()) {
          "Count query cannot emit null. Check your code!"
        }
        if (!isDisposed) {
          downstream.onNext((count > 0) xor countZero)
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
    val countZero = OperatorCountZeroOrNot(countZero = true)
    val countNotZero = OperatorCountZeroOrNot(countZero = false)
  }
}
