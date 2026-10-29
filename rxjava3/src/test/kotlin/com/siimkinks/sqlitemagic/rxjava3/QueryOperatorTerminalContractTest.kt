package com.siimkinks.sqlitemagic.rxjava3

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.Query
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(Parameterized::class)
internal class QueryOperatorTerminalContractTest(private val terminal: Terminal) {
  @Test
  fun `natural completion and error forward before upstream finally like map`() {
    for (complete in listOf(true, false)) {
      val events = mutableListOf<String>()
      val source = PublishSubject.create<Query<Any>>()
      val observer = source
        .doFinally { events.add("upstream finally") }
        .executeQuery()
        .record(events)
      val failure = IllegalArgumentException("upstream failed")
      when {
        complete -> {
          source.onComplete()
          observer.assertResult()
        }
        else -> {
          source.onError(failure)
          observer
            .assertNoValues()
            .assertError(failure)
            .assertNotComplete()
        }
      }
      assertThat(events)
        .containsExactly(
          when {
            complete -> "downstream complete"
            else -> "downstream error"
          },
          "upstream finally"
        )
        .inOrder()
    }
  }

  @Test
  fun `mapping failure disposes upstream before error like map`() {
    val failure = IllegalArgumentException("mapping failed")
    val cursor = mock<Cursor>()
    val query = object : Query<Any>(mock()) {
      override fun rawQuery(dbConnection: DbConnectionImpl) = cursor

      override fun map(cursor: Cursor, isDisposed: () -> Boolean): Any = cursor.use { throw failure }
    }
    val events = mutableListOf<String>()
    val source = PublishSubject.create<Query<Any>>()
    val observer = source
      .doFinally { events.add("upstream finally") }
      .executeQuery()
      .record(events)
    source.onNext(query)
    observer
      .assertNoValues()
      .assertError(failure)
      .assertNotComplete()
    assertThat(source.hasObservers()).isFalse()
    source.onNext(query)
    source.onComplete()
    observer
      .assertNoValues()
      .assertError(failure)
      .assertNotComplete()
    verify(cursor).close()
    assertThat(events)
      .containsExactly("upstream finally", "downstream error")
      .inOrder()
  }

  @Test
  fun `lifted operator ignores duplicate signals and reports late errors`() {
    val pluginErrors = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(pluginErrors::add)
    try {
      for (complete in listOf(true, false)) {
        val source = ManualSource()
        val ignoredQuery = mock<Query<Any>>()
        val observer = source.executeQuery()
          .doOnComplete { source.observer.onNext(ignoredQuery) }
          .doOnError { source.observer.onNext(ignoredQuery) }
          .test()
        val failure = IllegalArgumentException("initial failure")
        when {
          complete -> source.observer.onComplete()
          else -> source.observer.onError(failure)
        }
        source.observer.onNext(ignoredQuery)
        source.observer.onComplete()
        val lateFailure = IllegalArgumentException("late failure")
        source.observer.onError(lateFailure)
        when {
          complete -> observer.assertResult()
          else -> observer
            .assertNoValues()
            .assertError(failure)
            .assertNotComplete()
        }
        verifyNoInteractions(ignoredQuery)
        assertThat(source.upstream.isDisposed).isFalse()
        assertThat(pluginErrors.last()).isSameInstanceAs(lateFailure)
      }
      assertThat(pluginErrors).hasSize(2)
    } finally {
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `explicit cancellation detaches upstream and suppresses signals`() {
    val source = PublishSubject.create<Query<Any>>()
    val events = mutableListOf<String>()
    val observer = source
      .doFinally { events.add("upstream finally") }
      .executeQuery()
      .record(events)
    assertThat(source.hasObservers()).isTrue()
    observer.dispose()
    observer.dispose()
    assertThat(source.hasObservers()).isFalse()
    val ignoredQuery = mock<Query<Any>>()
    source.onNext(ignoredQuery)
    source.onComplete()
    observer.assertEmpty()
    verifyNoInteractions(ignoredQuery)
    assertThat(events).containsExactly("upstream finally")
  }

  @Suppress("UNCHECKED_CAST")
  private fun Observable<Query<Any>>.executeQuery() = when (terminal) {
    Terminal.SCALAR -> lift(OperatorRunSingleItemQuery())
    Terminal.LIST -> (this as Observable<Query<List<Any>>>).lift(runListQuery())
    Terminal.COUNT -> (this as Observable<Query<Long>>).lift(OperatorCountZeroOrNot.countZero)
  }

  private fun Observable<*>.record(events: MutableList<String>) =
    doOnComplete { events.add("downstream complete") }
      .doOnError { events.add("downstream error") }
      .test()

  private class ManualSource : Observable<Query<Any>>() {
    lateinit var observer: Observer<in Query<Any>>
    val upstream = Disposable.empty()

    override fun subscribeActual(observer: Observer<in Query<Any>>) {
      this.observer = observer
      observer.onSubscribe(upstream)
    }
  }

  internal enum class Terminal {
    SCALAR,
    LIST,
    COUNT
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun terminals() = Terminal.entries
  }
}
