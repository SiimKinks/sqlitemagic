package com.siimkinks.sqlitemagic.rxjava2

import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.internal.QueryObservation
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.Exceptions
import java.util.concurrent.atomic.AtomicInteger

internal class QueryNotifications<T>(
  private val observation: QueryObservation<T>
) : Observable<Query<T>>() {
  override fun subscribeActual(observer: Observer<in Query<T>>) {
    val connection = try {
      observation.connection()
    } catch (failure: Throwable) {
      Exceptions.throwIfFatal(failure)
      observer.onSubscribe(Disposed)
      observer.onError(failure)
      return
    }
    val subscription = ConflatedSubscription(
      downstream = observer,
      query = observation.query,
      worker = connection
        .queryScheduler()
        .createWorker(),
      observesChanges = observation.observesChanges
    )
    observer.onSubscribe(subscription)
    val registration = try {
      observation.register(
        connection = connection,
        observer = subscription
      )
    } catch (failure: IllegalStateException) {
      subscription.dispose()
      observer.onError(failure)
      return
    }
    subscription.listen(registration)
    subscription.signal()
  }
}

private class ConflatedSubscription<T>(
  private val downstream: Observer<in Query<T>>,
  private val query: Query<T>,
  private val worker: Scheduler.Worker,
  private val observesChanges: Boolean
) : Disposable, Runnable, TableChangeObserver {
  private val pending = AtomicInteger()

  @Volatile
  private var disposed = false

  @Volatile
  private var closed = false

  @Volatile
  private var registration: AutoCloseable? = null

  fun listen(registration: AutoCloseable) {
    this.registration = registration
    if (disposed) {
      registration.close()
    }
  }

  override fun onTablesChanged(tables: Set<String>) = signal()

  override fun onClosed() {
    closed = true
    signal()
  }

  fun signal() {
    if (pending.getAndIncrement() == 0) {
      worker.schedule(this)
    }
  }

  override fun run() {
    while (!disposed) {
      if (closed) {
        complete()
        return
      }
      val missed = pending.get()
      downstream.onNext(query)
      if (!observesChanges) {
        complete()
        return
      }
      if (pending.addAndGet(-missed) == 0) {
        return
      }
    }
  }

  private fun complete() {
    disposed = true
    downstream.onComplete()
    worker.dispose()
  }

  override fun dispose() {
    if (!disposed) {
      disposed = true
      registration?.close()
      worker.dispose()
    }
  }

  override fun isDisposed() = disposed
}

private object Disposed : Disposable {
  override fun dispose() = Unit

  override fun isDisposed() = true
}
