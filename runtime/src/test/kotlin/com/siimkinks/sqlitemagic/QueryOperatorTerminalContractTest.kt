package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposables
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.subjects.PublishSubject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
internal class QueryOperatorTerminalContractTest(private val terminal: String) {
  @Test
  fun `natural completion and error forward before upstream finally like map`() {
    for (complete in listOf(true, false)) {
      val traces = mutableListOf<List<String>>()
      for (reference in listOf(true, false)) {
        val events = mutableListOf<String>()
        val source = PublishSubject.create<Query<Any>>()
        val observer = source
          .doFinally { events.add("upstream finally") }
          .execute(reference)
          .record(events)
        val failure = IllegalArgumentException("upstream failed")
        when {
          complete -> {
            source.onComplete()
            observer.assertResult()
          }
          else -> {
            source.onError(failure)
            observer.assertFailure(failure::equals)
          }
        }
        traces.add(events.toList())
      }
      val expected = listOf(
        when {
          complete -> "downstream complete"
          else -> "downstream error"
        },
        "upstream finally"
      )
      assertThat(traces).containsExactly(expected, expected)
    }
  }

  @Test
  fun `mapping failure disposes upstream before error like map`() {
    val traces = mutableListOf<List<String>>()
    for (reference in listOf(true, false)) {
      val failure = IllegalArgumentException("mapping failed")
      val cursor = mock<Cursor>()
      whenever(cursor.count).thenReturn(1)
      whenever(cursor.moveToNext()).thenReturn(true)
      val query = object : Query.DatabaseQuery<Any, Any>(
        dbConnection = mock(),
        mapper = { throw failure }
      ) {
        override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl): Cursor = cursor

        override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Any =
          checkNotNull(cursor).use { throw failure }
      }
      val events = mutableListOf<String>()
      val source = PublishSubject.create<Query<Any>>()
      val observer = source
        .doFinally { events.add("upstream finally") }
        .execute(reference)
        .record(events)
      source.onNext(query)
      observer.assertFailure(failure::equals)
      assertThat(source.hasObservers()).isFalse()
      source.onNext(query)
      source.onComplete()
      observer.assertFailure(failure::equals)
      verify(cursor).close()
      traces.add(events.toList())
    }
    val expected = listOf("upstream finally", "downstream error")
    assertThat(traces).containsExactly(expected, expected)
  }

  @Test
  fun `lifted operator ignores duplicate signals and reports late errors`() {
    val pluginErrors = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(pluginErrors::add)
    try {
      for (complete in listOf(true, false)) {
        val source = ManualSource()
        val ignoredQuery = mock<Query<Any>>()
        val observer = source.execute(reference = false)
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
          else -> observer.assertFailure(failure::equals)
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
      .execute(reference = false)
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

  private fun Observable<Query<Any>>.execute(reference: Boolean) = when {
    reference -> map { checkNotNull(it.runBlocking()) }
    else -> executeQuery()
  }

  @Suppress("UNCHECKED_CAST")
  private fun Observable<Query<Any>>.executeQuery() = when (terminal) {
    "scalar" -> lift(OperatorRunSingleItemQuery())
    "list" -> (this as Observable<Query<List<Any>>>).lift(OperatorRunListQuery.instance())
    "count zero" -> (this as Observable<Query<Long>>).lift(OperatorCountZeroOrNot.countZero)
    else -> (this as Observable<Query<Long>>).lift(OperatorCountZeroOrNot.countNotZero)
  }

  private fun Observable<*>.record(events: MutableList<String>) =
    doOnComplete { events.add("downstream complete") }
      .doOnError { events.add("downstream error") }
      .test()

  private class ManualSource : Observable<Query<Any>>() {
    lateinit var observer: Observer<in Query<Any>>
    val upstream = Disposables.empty()

    override fun subscribeActual(observer: Observer<in Query<Any>>) {
      this.observer = observer
      observer.onSubscribe(upstream)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun terminals() = listOf("scalar", "list", "count zero", "count not zero")
  }
}
