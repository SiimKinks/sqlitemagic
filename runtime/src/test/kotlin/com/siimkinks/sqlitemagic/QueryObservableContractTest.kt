package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.plugins.RxJavaPlugins
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
      .assertFailure(failure::equals)
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
    val mapper = mock<Query.Mapper<String?>>()
    val query = listQuery(
      cursor = cursor,
      mapper = mapper,
      beforeReturn = observer::dispose
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .subscribe(observer)

    observer.assertEmpty()
    verify(cursor).close()
    verify(cursor, never()).moveToNext()
    verify(mapper, never()).apply(cursor)
  }

  @Test
  fun `scalar queries skip null results or emit the supplied default`() {
    val source = SingleItemQueryObservable(
      Observable.just(
        scalarQuery { null },
        scalarQuery { "row" },
        scalarQuery { null }
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
  fun `failure after scalar mapping disposes its observer is reported to RxJava plugins`() {
    val failures = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(failures::add)
    try {
      val observer = TestObserver<String>()
      val failure = RuntimeException("mapping failed after disposal")
      val query = scalarQuery<String> {
        observer.dispose()
        throw failure
      }

      SingleItemQueryObservable(Observable.just(query))
        .runQuery()
        .subscribe(observer)

      observer.assertEmpty()
      assertThat(failures.map(Throwable::cause)).containsExactly(failure)
    } finally {
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `fatal list mapper failures escape and still close the cursor`() {
    val cursor = mock<Cursor>()
    whenever(cursor.count).thenReturn(1)
    whenever(cursor.moveToNext()).thenReturn(true)
    val failure = LinkageError("fatal mapper failure")
    val query = listQuery(
      cursor = cursor,
      mapper = { throw failure }
    )

    val thrown = assertThrows(LinkageError::class.java) {
      ListQueryObservable(Observable.just(query))
        .runQuery()
        .test()
    }

    assertThat(thrown).isSameInstanceAs(failure)
    verify(cursor).close()
  }

  @Test
  fun `one shot scalar queries finish after the first trigger even when its result is null`() {
    for (firstResult in listOf(null, "first")) {
      var laterExecutions = 0
      val source = SingleItemQueryObservable(
        Observable.just(
          scalarQuery { firstResult },
          scalarQuery {
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
  fun `count predicates and inherited scalar terminal observe zero and positive counts`() {
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
    source.runQueryOnce()
      .test()
      .assertResult(0L)
  }

  private fun listQuery(
    cursor: Cursor,
    mapper: Query.Mapper<String?>,
    beforeReturn: () -> Unit = {}
  ): Query<List<String?>> = object : Query.DatabaseQuery<List<String?>, String?>(
    dbConnection = mock(),
    mapper = mapper
  ) {
    override fun rawQuery(
      inStream: Boolean,
      dbConnection: DbConnectionImpl
    ): Cursor {
      beforeReturn()
      return cursor
    }

    override fun map(
      cursor: Cursor?,
      dbConnection: DbConnectionImpl
    ): List<String?> = error("List operators must use the row mapper")
  }

  private fun <T> scalarQuery(result: () -> T?): Query<T> = object : Query<T>(mock()) {
    override fun rawQuery(
      inStream: Boolean,
      dbConnection: DbConnectionImpl
    ): Cursor? = null

    override fun map(
      cursor: Cursor?,
      dbConnection: DbConnectionImpl
    ) = result()
  }
}
