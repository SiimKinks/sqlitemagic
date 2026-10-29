package com.siimkinks.sqlitemagic.rxjava3

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SelectPlan
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderable
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import com.siimkinks.sqlitemagic.internal.sql.prepareQuery
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class QueryObservableContractTest {
  @Test
  fun `list query closes cursor and preserves empty results and nullable elements`() {
    val cases = listOf(
      "empty" to emptyList(),
      "nullable scalar rows" to listOf("first", null, "last")
    )
    for ((label, rows) in cases) {
      val cursor = mock<Cursor>()
      whenever(cursor.count).thenReturn(rows.size)
      var index = -1
      whenever(cursor.moveToNext()).thenAnswer { ++index < rows.size }
      val query = listQuery(
        cursor = cursor,
        mapper = { rows[index] }
      )

      ListQueryObservable(Observable.just(query))
        .runQuery()
        .test()
        .withTag(label)
        .assertResult(rows)
      verify(cursor).close()
    }
  }

  @Test
  fun `list mapper failure closes cursor before delivering error`() {
    val cursor = mock<Cursor>()
    whenever(cursor.count).thenReturn(1)
    whenever(cursor.moveToNext()).thenReturn(true)
    val failure = IllegalArgumentException("cannot map row")
    val query = listQuery(
      cursor = cursor,
      mapper = { throw failure }
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .doOnError { verify(cursor).close() }
      .test()
      .assertNoValues()
      .assertError(failure)
      .assertNotComplete()
  }

  @Test
  fun `disposal while mapping closes cursor and suppresses partial list`() {
    val cursor = mock<Cursor>()
    whenever(cursor.count).thenReturn(2)
    whenever(cursor.moveToNext()).thenReturn(true)
    val observer = TestObserver<List<String?>>()
    var mappedRows = 0
    val query = listQuery(
      cursor = cursor,
      mapper = {
        mappedRows++
        observer.dispose()
        "first"
      }
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .subscribe(observer)

    observer.assertEmpty()
    assertThat(mappedRows).isEqualTo(1)
    verify(cursor).close()
  }

  @Test
  fun `disposal while opening cursor closes it without mapping`() {
    val cursor = mock<Cursor>()
    val observer = TestObserver<List<String?>>()
    var mappedRows = 0
    val query = listQuery(
      cursor = cursor,
      mapper = {
        mappedRows++
        null
      },
      beforeReturn = observer::dispose
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .subscribe(observer)

    observer.assertEmpty()
    verify(cursor).close()
    verify(cursor, never()).moveToNext()
    assertThat(mappedRows).isEqualTo(0)
  }

  @Test
  fun `scalar queries skip null results or emit the supplied default`() {
    val source = SingleItemQueryObservable(
      Observable.just(
        scalarQuery<String?> { null },
        scalarQuery<String?> { "row" },
        scalarQuery<String?> { null }
      )
    )

    source
      .runQuery()
      .test()
      .assertResult("row")
    source
      .runQueryOrDefault("fallback")
      .test()
      .assertResult("fallback", "row", "fallback")
  }

  @Test
  fun `nullable scalar query accepts a null default and continues suppressing null emissions`() {
    val source = SingleItemQueryObservable(Observable.just(scalarQuery<String?> { null }))

    source.runQueryOrDefault(null)
      .test()
      .assertResult()
    source.runQueryOnceOrDefault(null)
      .test()
      .assertFailure(NoSuchElementException::class.java)
  }

  @Test
  fun `mapping failures after the observer is disposed are reported to RxJava plugins`() {
    val failures = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(failures::add)
    try {
      for ((label, subscribe) in mapOf<String, (TestObserver<Any>, RuntimeException) -> Unit>(
        "single item" to { observer, failure ->
          val query = scalarQuery<String> {
            observer.dispose()
            throw failure
          }
          SingleItemQueryObservable(Observable.just(query))
            .runQuery()
            .subscribe(observer)
        },
        "list" to { observer, failure ->
          val query = listQuery(
            cursor = rowCursor(),
            mapper = {
              observer.dispose()
              throw failure
            }
          )
          ListQueryObservable(Observable.just(query))
            .runQuery()
            .subscribe(observer)
        },
        "count" to { observer, failure ->
          val query = scalarQuery<Long> {
            observer.dispose()
            throw failure
          }
          CountQueryObservable(Observable.just(query))
            .isZero()
            .subscribe(observer)
        }
      )) {
        failures.clear()
        val observer = TestObserver<Any>()
        val failure = RuntimeException("$label mapping failed after disposal")

        subscribe(observer, failure)

        observer
          .withTag(label)
          .assertEmpty()
        assertWithMessage(label)
          .that(failures.map(Throwable::cause))
          .containsExactly(failure)
      }
    } finally {
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `fatal mapping failures escape and still close the cursor`() {
    val failure = LinkageError("fatal mapper failure")
    for ((label, subscribe) in mapOf<String, (Cursor) -> Unit>(
      "list" to { cursor ->
        val query = listQuery(
          cursor = cursor,
          mapper = { throw failure }
        )
        ListQueryObservable(Observable.just(query))
          .runQuery()
          .test()
      },
      "single item" to { cursor ->
        val query = firstQuery(
          cursor = cursor,
          mapper = { throw failure }
        )
        SingleItemQueryObservable(Observable.just(query))
          .runQuery()
          .test()
      },
      "count" to { cursor ->
        whenever(cursor.getLong(0)).thenThrow(failure)
        val query = planQuery(
          plan = CompiledCount(emptyPrepared()),
          cursor = cursor
        )
        CountQueryObservable(Observable.just(query))
          .isZero()
          .test()
      }
    )) {
      val cursor = rowCursor()

      val thrown = assertThrows(LinkageError::class.java) { subscribe(cursor) }

      assertWithMessage(label)
        .that(thrown)
        .isSameInstanceAs(failure)
      verify(cursor).close()
    }
  }

  @Test
  fun `one shot scalar queries finish after the first trigger even when its result is null`() {
    for (firstResult in listOf(null, "first")) {
      var laterExecutions = 0
      val source = SingleItemQueryObservable(
        Observable.just(
          scalarQuery { firstResult },
          scalarQuery<String?> {
            laterExecutions++
            "later"
          }
        )
      )

      source
        .runQueryOnce()
        .test()
        .withTag("first result: $firstResult")
        .assertResult(*listOfNotNull(firstResult).toTypedArray())
      source
        .runQueryOnceOrDefault("fallback")
        .test()
        .assertResult(firstResult ?: "fallback")
      assertThat(laterExecutions).isEqualTo(0)
    }
  }

  @Test
  fun `one shot list query closes its cursor and does not run later triggers`() {
    val cursor = mock<Cursor>()
    whenever(cursor.count).thenReturn(0)
    var laterExecutions = 0
    val source = ListQueryObservable(
      Observable.just(
        listQuery(
          cursor = cursor,
          mapper = { "unused" }
        ),
        listQuery(
          cursor = mock(),
          mapper = { "unused" },
          beforeReturn = { laterExecutions++ }
        )
      )
    )

    source
      .runQueryOnce()
      .test()
      .assertResult(emptyList())

    verify(cursor).close()
    assertThat(laterExecutions).isEqualTo(0)
  }

  @Test
  fun `count predicates observe zero and positive counts`() {
    val source = CountQueryObservable(
      Observable.just(
        scalarQuery { 0L },
        scalarQuery { 2L }
      )
    )

    source.isZero()
      .test()
      .assertResult(true, false)
    source.isNotZero()
      .test()
      .assertResult(false, true)
  }

  private fun listQuery(
    cursor: Cursor,
    mapper: (Cursor) -> String?,
    beforeReturn: () -> Unit = {}
  ) = planQuery(
    plan = CompiledSelect(
      prepared = emptyPrepared(),
      reader = ExecutionReader { { rows, _ -> mapper(rows) } }
    ),
    cursor = cursor,
    beforeReturn = beforeReturn
  )

  private fun firstQuery(
    cursor: Cursor,
    mapper: (Cursor) -> String?
  ) = planQuery(
    plan = CompiledFirst(
      prepared = emptyPrepared(),
      reader = ExecutionReader { { rows, _ -> mapper(rows) } }
    ),
    cursor = cursor
  )

  private fun <T> planQuery(
    plan: SelectPlan<T>,
    cursor: Cursor,
    beforeReturn: () -> Unit = {}
  ): Query<T> = object : Query<T>(mock()) {
    override fun rawQuery(dbConnection: DbConnectionImpl): Cursor {
      beforeReturn()
      return cursor
    }

    override fun map(cursor: Cursor, isDisposed: () -> Boolean) = plan.mapRows(
      cursor = cursor,
      isDisposed = isDisposed
    )
  }

  private fun emptyPrepared() = prepareQuery(
    renderable = object : SqlRenderable {
      override fun render(context: SqlRenderingContext) = Unit
    },
    connection = null
  )

  private fun rowCursor() = mock<Cursor>().also { cursor ->
    whenever(cursor.count).thenReturn(1)
    whenever(cursor.moveToNext()).thenReturn(true)
  }

  private fun <T> scalarQuery(result: () -> T): Query<T> = object : Query<T>(mock()) {
    override fun rawQuery(dbConnection: DbConnectionImpl) = mock<Cursor>()

    override fun map(cursor: Cursor, isDisposed: () -> Boolean) = result()
  }
}
