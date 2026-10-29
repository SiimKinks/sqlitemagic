package com.siimkinks.sqlitemagic.rxjava2

import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.ObservableOperator
import io.reactivex.Observer
import io.reactivex.exceptions.Exceptions
import io.reactivex.observers.DisposableObserver
import io.reactivex.plugins.RxJavaPlugins

internal fun <T> runListQuery() = ObservableOperator<List<T>, Query<List<T>>>(::MappingObserver)

private class MappingObserver<T>(
  private val downstream: Observer<in List<T>>
) : DisposableObserver<Query<List<T>>>() {
  private var done = false
  private val checkDisposed: () -> Boolean = ::isDisposed

  override fun onStart() = downstream.onSubscribe(this)

  override fun onNext(query: Query<List<T>>) {
    if (done || isDisposed) return
    try {
      val cursor = ReactiveAdapterSupport.openCursor(query)
      if (isDisposed) {
        cursor.close()
        return
      }
      val items = ReactiveAdapterSupport.map(
        query = query,
        cursor = cursor,
        isDisposed = checkDisposed
      )
      if (!isDisposed) {
        downstream.onNext(items)
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
