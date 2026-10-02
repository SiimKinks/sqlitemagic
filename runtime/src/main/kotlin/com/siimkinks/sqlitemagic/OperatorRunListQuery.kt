package com.siimkinks.sqlitemagic

import io.reactivex.ObservableOperator
import io.reactivex.Observer
import io.reactivex.exceptions.Exceptions
import io.reactivex.observers.DisposableObserver
import io.reactivex.plugins.RxJavaPlugins

internal class OperatorRunListQuery<T> private constructor() : ObservableOperator<List<T>, Query<List<T>>> {
  override fun apply(observer: Observer<in List<T>>): Observer<in Query<List<T>>> = MappingObserver(observer)

  private class MappingObserver<T>(
    private val downstream: Observer<in List<T>>
  ) : DisposableObserver<Query<List<T>>>() {
    override fun onStart() = downstream.onSubscribe(this)

    override fun onNext(query: Query<List<T>>) {
      try {
        val cursor = query.rawQuery(inStream = true) ?: return
        val items = cursor.use {
          if (isDisposed) return
          when (val rowCount = cursor.count) {
            0 -> emptyList()
            else -> {
              @Suppress("UNCHECKED_CAST")
              val mapper = checkNotNull((query as Query.DatabaseQuery<List<T>, T>).mapper) {
                "Query needs a mapper -- this is a SqliteMagic bug"
              }
              buildList(rowCount) {
                while (cursor.moveToNext() && !isDisposed) {
                  add(mapper.apply(cursor))
                }
              }
            }
          }
        }
        if (!isDisposed) {
          downstream.onNext(items)
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
    private val instance = OperatorRunListQuery<Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> instance(): OperatorRunListQuery<T> = instance as OperatorRunListQuery<T>
  }
}
