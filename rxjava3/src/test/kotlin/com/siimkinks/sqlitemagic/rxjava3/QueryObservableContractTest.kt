package com.siimkinks.sqlitemagic.rxjava3

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.CompiledCount
import com.siimkinks.sqlitemagic.CompiledFirst
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.emptyPrepared
import com.siimkinks.sqlitemagic.listQuery
import com.siimkinks.sqlitemagic.planQuery
import com.siimkinks.sqlitemagic.rowStatement
import com.siimkinks.sqlitemagic.withWorkers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit.SECONDS

internal class QueryObservableContractTest {
  @Test
  fun `list query preserves empty results and nullable elements`() {
    for ((label, rows) in listOf(
      "empty" to emptyList(),
      "nullable scalar rows" to listOf("first", null, "last")
    )) {
      val statement = mock<SQLiteStatement>()
      var index = -1
      whenever(statement.step()).thenAnswer { ++index < rows.size }
      val query = listQuery(
        connection = mock(),
        statement = statement,
        mapper = { rows[index] }
      )

      ListQueryObservable(Observable.just(query))
        .runQuery()
        .test()
        .withTag(label)
        .assertResult(rows)
    }
  }

  @Test
  fun `list mapper failure is delivered as the error`() {
    val failure = IllegalArgumentException("cannot map row")
    val query = listQuery(
      connection = mock(),
      statement = rowStatement(),
      mapper = { throw failure }
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .test()
      .assertNoValues()
      .assertError(failure)
      .assertNotComplete()
  }

  @Test
  fun `disposal while mapping stops reading rows and suppresses partial list`() {
    val statement = rowStatement()
    val observer = TestObserver<List<String?>>()
    var mappedRows = 0
    val query = listQuery(
      connection = mock(),
      statement = statement,
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
    verify(
      mock = statement,
      mode = times(1)
    ).step()
  }

  @Test
  fun `disposal before reading rows maps none`() {
    val statement = mock<SQLiteStatement>()
    val observer = TestObserver<List<String?>>()
    var mappedRows = 0
    val query = listQuery(
      connection = mock(),
      statement = statement,
      mapper = {
        mappedRows++
        null
      },
      beforeRead = observer::dispose
    )

    ListQueryObservable(Observable.just(query))
      .runQuery()
      .subscribe(observer)

    observer.assertEmpty()
    verify(
      mock = statement,
      mode = never()
    ).step()
    assertThat(mappedRows).isEqualTo(0)
  }

  @Test
  fun `disposal while a first-row or count query runs delivers nothing and releases its connection`() {
    data class Case(
      val hasRow: Boolean,
      val observable: () -> Observable<out Any>
    )

    var observer = TestObserver<Any>()
    var hasRow = false
    val connection = queryingConnection(answers = { "SELECT ?" in it }) {
      mock<SQLiteStatement> {
        on { step() } doAnswer {
          observer.dispose()
          hasRow
        }
        on { getLong(0) } doReturn 8L
      }
    }
    val value = SELECT
      .COLUMN(8L.asValue())
      .usingConnection(connection)

    mapOf(
      "first row" to Case(hasRow = true) {
        value
          .takeFirst()
          .observe()
          .runQuery()
      },
      "first row or default" to Case(hasRow = false) {
        value
          .takeFirst()
          .observe()
          .runQueryOrDefault(7L)
      },
      "count" to Case(hasRow = true) {
        value
          .count()
          .observe()
          .runQuery()
      },
      "count is zero" to Case(hasRow = true) {
        value
          .count()
          .observe()
          .isZero()
      }
    ).forEach { (label, case) ->
      hasRow = case.hasRow
      observer = TestObserver()
      case
        .observable()
        .subscribe(observer)
      observer
        .withTag(label)
        .assertEmpty()
    }
    withWorkers { workers ->
      val laterRead = workers
        .submit<List<Long>> {
          (SELECT RAW "SELECT 1")
            .readAs(LONG)
            .usingConnection(connection)
            .execute()
        }
        .get(5L, SECONDS)
      assertThat(laterRead).isEqualTo(listOf(1L))
    }
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
            connection = mock(),
            statement = rowStatement(),
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
  fun `fatal mapping failures escape`() {
    val failure = LinkageError("fatal mapper failure")
    for ((label, subscribe) in mapOf<String, (SQLiteStatement) -> Unit>(
      "list" to { statement ->
        val query = listQuery(
          connection = mock(),
          statement = statement,
          mapper = { throw failure }
        )
        ListQueryObservable(Observable.just(query))
          .runQuery()
          .test()
      },
      "single item" to { statement ->
        val query = planQuery(
          connection = mock(),
          plan = CompiledFirst<String?>(
            prepared = emptyPrepared(),
            reader = ExecutionReader { { _, _ -> throw failure } }
          ),
          statement = statement
        )
        SingleItemQueryObservable(Observable.just(query))
          .runQuery()
          .test()
      },
      "count" to { statement ->
        whenever(statement.getLong(0)).thenThrow(failure)
        val query = planQuery(
          connection = mock(),
          plan = CompiledCount(emptyPrepared()),
          statement = statement
        )
        CountQueryObservable(Observable.just(query))
          .isZero()
          .test()
      }
    )) {
      val thrown = assertThrows(LinkageError::class.java) { subscribe(rowStatement()) }

      assertWithMessage(label)
        .that(thrown)
        .isSameInstanceAs(failure)
    }
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
  fun `one shot list query does not run later triggers`() {
    var laterExecutions = 0
    val source = ListQueryObservable(
      Observable.just(
        listQuery(
          connection = mock(),
          statement = mock(),
          mapper = { "unused" }
        ),
        listQuery(
          connection = mock(),
          statement = mock(),
          mapper = { "unused" },
          beforeRead = { laterExecutions++ }
        )
      )
    )

    source
      .runQueryOnce()
      .test()
      .assertResult(emptyList())

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

  private fun <T> scalarQuery(result: () -> T): Query<T> = object : Query<T>(mock()) {
    override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean) = result()
  }
}
