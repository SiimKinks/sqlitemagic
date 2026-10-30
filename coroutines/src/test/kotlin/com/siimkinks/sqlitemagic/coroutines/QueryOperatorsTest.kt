package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.ExecutionReader
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderable
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import com.siimkinks.sqlitemagic.internal.sql.prepareQuery
import com.siimkinks.sqlitemagic.libraryDatabase
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class QueryOperatorsTest {
  @Test
  fun `single-row operators skip, replace or return a missing row`() = runBlocking {
    libraryDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = dispatcherSettings())
      val mary = SELECT
        .COLUMN(AUTHOR.NAME)
        .FROM(AUTHOR)
        .WHERE(AUTHOR.NAME IS "Mary")
        .usingConnection(connection)
        .takeFirst()
      val skipping = collectUnconfined(
        mary
          .observe()
          .runQuery()
      )
      val defaulting = collectUnconfined(
        mary
          .observe()
          .runQueryOrDefault("none")
      )
      val once = mary
        .observe()
        .runQueryOnce()
      val onceOrDefault = mary
        .observe()
        .runQueryOnceOrDefault("none")

      UPDATE
        .RAW("UPDATE author SET name = ? WHERE id = ?", "Mary", 2L)
        .notifies(AUTHOR)
        .usingConnection(connection)
        .execute()
      skipping.cancel()
      defaulting.cancel()

      assertThat(listOf(skipping.signals, defaulting.signals, FlowSignals(listOf(once, onceOrDefault)))).isEqualTo(
        listOf(
          FlowSignals(values = listOf("Mary")),
          FlowSignals(values = listOf("none", "Mary")),
          FlowSignals(values = listOf(null, "none"))
        )
      )
    }
  }

  @Test
  fun `count operators emit the count and whether it is zero`() = runBlocking {
    libraryDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = dispatcherSettings())
      val marys = SELECT
        .FROM(AUTHOR)
        .WHERE(AUTHOR.NAME IS "Mary")
        .usingConnection(connection)
        .count()
      val streams = listOf(
        marys
          .observe()
          .runQuery(),
        marys
          .observe()
          .runQueryOrDefault(-1L),
        marys
          .observe()
          .isZero(),
        marys
          .observe()
          .isNotZero()
      ).map(::collectUnconfined)
      val once = marys
        .observe()
        .runQueryOnce()
      val onceOrDefault = marys
        .observe()
        .runQueryOnceOrDefault(-1L)

      UPDATE
        .RAW("UPDATE author SET name = ? WHERE id = ?", "Mary", 2L)
        .notifies(AUTHOR)
        .usingConnection(connection)
        .execute()
      streams.forEach(FlowCollection<*>::cancel)

      assertThat(streams.map(FlowCollection<*>::signals) + FlowSignals(listOf(once, onceOrDefault))).isEqualTo(
        listOf(
          FlowSignals(values = listOf(0L, 1L)),
          FlowSignals(values = listOf(0L, 1L)),
          FlowSignals(values = listOf(true, false)),
          FlowSignals(values = listOf(false, true)),
          FlowSignals(values = listOf(0L, 0L))
        )
      )
    }
  }

  @Test
  fun `one-shot operators on a closed connection return null or fail with NoSuchElementException`() =
    runBlocking {
      val connection = closedConnection(adapterSettings = dispatcherSettings())
      val names = SELECT
        .COLUMN(AUTHOR.NAME)
        .FROM(AUTHOR)
        .usingConnection(connection)
      val failures = listOf<suspend () -> Any?>(
        {
          names
            .observe()
            .runQueryOnce()
        },
        {
          names
            .takeFirst()
            .observe()
            .runQueryOnceOrDefault("none")
        },
        {
          names
            .count()
            .observe()
            .runQueryOnceOrDefault(0L)
        }
      ).map { call -> runCatching { call() }.exceptionOrNull()?.javaClass }
      val missing = listOf(
        names
          .takeFirst()
          .observe()
          .runQueryOnce(),
        names
          .count()
          .observe()
          .runQueryOnce()
      )

      assertThat(failures to missing).isEqualTo(
        List(3) { NoSuchElementException::class.java } to listOf(null, null)
      )
    }

  @Test
  fun `a mapping failure fails the flow after closing the cursor`() {
    for (failure in listOf(IllegalArgumentException("cannot map row"), LinkageError("fatal mapper failure"))) {
      val cursor = rowCursor()
      val query = listQuery(
        cursor = cursor,
        mapper = { throw failure }
      )

      val thrown = assertThrows(Throwable::class.java) {
        runBlocking {
          flowOf(query)
            .runQuery()
            .collect()
        }
      }

      assertWithMessage(failure.javaClass.simpleName)
        .that(thrown.javaClass to thrown.message)
        .isEqualTo(failure.javaClass to failure.message)
      verify(cursor).close()
    }
  }

  @Test
  fun `cancellation while mapping stops reading rows, closes the cursor and delivers nothing`() {
    val cursor = rowCursor(count = 2)
    val caller = Caller()
    var mappedRows = 0
    val query = listQuery(
      cursor = cursor,
      mapper = {
        mappedRows++
        caller.cancel()
        "first"
      }
    )

    val delivered = caller.run { flowOf(query).runQuery() }

    assertThat(delivered to mappedRows).isEqualTo(emptyList<List<String?>>() to 1)
    verify(cursor).close()
  }

  @Test
  fun `cancellation while opening the cursor closes it without mapping`() {
    val cursor = rowCursor()
    val caller = Caller()
    var mappedRows = 0
    val query = listQuery(
      cursor = cursor,
      mapper = {
        mappedRows++
        null
      },
      beforeReturn = caller::cancel
    )

    val delivered = caller.run { flowOf(query).runQuery() }

    assertThat(delivered to mappedRows).isEqualTo(emptyList<List<String?>>() to 0)
    verify(cursor).close()
    verify(cursor, never()).moveToNext()
  }

  @Test
  fun `a cursor result is closed when cancellation wins before delivery and stays open once delivered`() {
    for ((label, terminal) in mapOf<String, (Query<Cursor>) -> Flow<Cursor>>(
      "run" to { query -> flow { emit(query.run()) } },
      "runQuery" to { query -> flowOf(query).runQuery() },
      "runQueryOnce" to { query -> flow { emit(checkNotNull(flowOf(query).runQueryOnce())) } }
    )) {
      val cancelledCursor = mock<Cursor>()
      val caller = Caller()
      val cancelled = cursorQuery(cancelledCursor) { caller.cancel() }
      val deliveredCursor = mock<Cursor>()
      val delivered = cursorQuery(deliveredCursor) {}

      val outcome = caller.run { terminal(cancelled) } to runBlocking { terminal(delivered).toList() }

      assertWithMessage(label)
        .that(outcome)
        .isEqualTo(emptyList<Cursor>() to listOf(deliveredCursor))
      verify(cancelledCursor).close()
      verifyNoMoreInteractions(cancelledCursor)
      verifyNoInteractions(deliveredCursor)
    }
  }

  @Test
  fun `cancellation before a default is delivered keeps the external default cursor open`() {
    val defaultCursor = mock<Cursor>()
    val caller = Caller()
    val query = object : Query<Cursor?>(mockConnection()) {
      override fun rawQuery(dbConnection: DbConnectionImpl) = mock<Cursor>()

      override fun map(cursor: Cursor, isDisposed: () -> Boolean): Cursor? {
        caller.cancel()
        return null
      }
    }

    val delivered = caller.run { flowOf(query).runQueryOrDefault(defaultCursor) }

    assertThat(delivered).isEmpty()
    verifyNoInteractions(defaultCursor)
  }

  @Test
  fun `running a query on a thread with an active transaction fails before opening a cursor on any dispatcher`() {
    for ((label, dispatcher) in mapOf(
      "calling thread" to Dispatchers.Unconfined,
      "another thread" to Dispatchers.IO
    )) {
      val connection = mockConnection(dispatcher)
      val query = object : Query<Cursor>(connection) {
        override fun rawQuery(dbConnection: DbConnectionImpl): Cursor =
          error("Rejected query must not acquire a cursor")

        override fun map(cursor: Cursor, isDisposed: () -> Boolean): Cursor =
          error("Rejected query must not map a cursor")
      }
      val transaction = connection.newTransaction()
      val failure = try {
        assertThrows(IllegalStateException::class.java) { runBlocking { query.run() } }
      } finally {
        transaction.end()
      }

      assertWithMessage(label)
        .that(failure.message)
        .isEqualTo("Cannot execute observable query in a transaction.")
    }
  }

  private class Caller {
    private lateinit var job: Job

    fun cancel() = job.cancel()

    fun <T> run(flow: () -> Flow<T>) = mutableListOf<T>().also { delivered ->
      runBlocking {
        job = launch(start = LAZY) { flow().collect(delivered::add) }
        job.start()
        job.join()
      }
    }
  }

  private fun cursorQuery(
    cursor: Cursor,
    onOpen: () -> Unit
  ): Query<Cursor> {
    val database = mock<SupportSQLiteDatabase>()
    whenever(database.query(any<SupportSQLiteQuery>())).thenAnswer {
      onOpen()
      cursor
    }
    return SELECT
      .FROM(PERSON)
      .usingConnection(
        testDbConnection(
          database = database,
          adapterSettings = dispatcherSettings()
        )
      )
      .toCursor()
      .execution()
  }

  private fun listQuery(
    cursor: Cursor,
    mapper: (Cursor) -> String?,
    beforeReturn: () -> Unit = {}
  ): Query<List<String?>> {
    val plan = CompiledSelect(
      prepared = prepareQuery(
        renderable = object : SqlRenderable {
          override fun render(context: SqlRenderingContext) = Unit
        },
        connection = null
      ),
      reader = ExecutionReader { { rows, _ -> mapper(rows) } }
    )
    return object : Query<List<String?>>(mockConnection()) {
      override fun rawQuery(dbConnection: DbConnectionImpl): Cursor {
        beforeReturn()
        return cursor
      }

      override fun map(cursor: Cursor, isDisposed: () -> Boolean) = plan.mapRows(
        cursor = cursor,
        isDisposed = isDisposed
      )
    }
  }

  private fun rowCursor(count: Int = 1) = mock<Cursor>().also { cursor ->
    whenever(cursor.count).thenReturn(count)
    whenever(cursor.moveToNext()).thenReturn(true)
  }

}
