package com.siimkinks.sqlitemagic

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.exceptions.Exceptions
import java.util.concurrent.atomic.AtomicInteger

internal class QueryNotifications<T>(
  private val plan: SelectPlan<T>
) : Observable<Query<T>>() {
  override fun subscribeActual(observer: Observer<in Query<T>>) {
    val query = plan.execution()
    val connection = try {
      query.resolveConnection()
    } catch (failure: Throwable) {
      Exceptions.throwIfFatal(failure)
      observer.onSubscribe(Disposables.disposed())
      observer.onError(failure)
      return
    }
    val observedTables = plan.prepared.dependencies.observedTables
    val subscription = ConflatedSubscription(
      downstream = observer,
      query = query,
      worker = connection.queryScheduler.createWorker(),
      observesChanges = observedTables.isNotEmpty()
    )
    observer.onSubscribe(subscription)
    try {
      connection.ensureNotInTransaction()
    } catch (failure: IllegalStateException) {
      subscription.dispose()
      observer.onError(failure)
      return
    }
    when {
      observedTables.isNotEmpty() -> subscription.listen(
        observers = connection.tableObservers,
        tables = observedTables
      )
      connection.isClosed -> subscription.onClosed()
    }
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

  fun listen(
    observers: TableObservers,
    tables: Array<String>
  ) {
    val registration = observers.add(
      tables = tables,
      observer = this
    )
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
