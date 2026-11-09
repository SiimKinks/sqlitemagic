package com.siimkinks.sqlitemagic.coroutines

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.libraryDatabase
import com.siimkinks.sqlitemagic.listQuery
import com.siimkinks.sqlitemagic.rowStatement
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

internal class QueryOperatorsTest {
  @Test
  fun `single-row operators skip, replace or return a missing row`() = runBlocking {
    libraryDatabase(adapterSettings = dispatcherSettings()).use { database ->
      val connection = database.connection
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
    libraryDatabase(adapterSettings = dispatcherSettings()).use { database ->
      val connection = database.connection
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
          .isZero(),
        marys
          .observe()
          .isNotZero()
      ).map(::collectUnconfined)
      val once = marys
        .observe()
        .runQueryOnce()

      UPDATE
        .RAW("UPDATE author SET name = ? WHERE id = ?", "Mary", 2L)
        .notifies(AUTHOR)
        .usingConnection(connection)
        .execute()
      streams.forEach(FlowCollection<*>::cancel)

      assertThat(streams.map(FlowCollection<*>::signals) + FlowSignals(listOf(once))).isEqualTo(
        listOf(
          FlowSignals(values = listOf(0L, 1L)),
          FlowSignals(values = listOf(true, false)),
          FlowSignals(values = listOf(false, true)),
          FlowSignals(values = listOf(0L))
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
            .runQueryOnce()
        }
      ).map { call -> runCatching { call() }.exceptionOrNull()?.javaClass }
      val missing = names
        .takeFirst()
        .observe()
        .runQueryOnce()

      assertThat(failures to missing).isEqualTo(List(3) { NoSuchElementException::class.java } to null)
    }

  @Test
  fun `a mapping failure fails the flow`() {
    for (failure in listOf(IllegalArgumentException("cannot map row"), LinkageError("fatal mapper failure"))) {
      val query = listQuery(
        connection = testConnection(),
        statement = rowStatement(),
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
    }
  }

  @Test
  fun `cancellation while mapping stops reading rows and delivers nothing`() {
    val statement = rowStatement()
    val caller = Caller()
    var mappedRows = 0
    val query = listQuery(
      connection = testConnection(),
      statement = statement,
      mapper = {
        mappedRows++
        caller.cancel()
        "first"
      }
    )

    val delivered = caller.run { flowOf(query).runQuery() }

    assertThat(delivered to mappedRows).isEqualTo(emptyList<List<String?>>() to 1)
    verify(statement).step()
  }

  @Test
  fun `cancellation before reading rows maps none`() {
    val statement = rowStatement()
    val caller = Caller()
    var mappedRows = 0
    val query = listQuery(
      connection = testConnection(),
      statement = statement,
      mapper = {
        mappedRows++
        null
      },
      beforeRead = caller::cancel
    )

    val delivered = caller.run { flowOf(query).runQuery() }

    assertThat(delivered to mappedRows).isEqualTo(emptyList<List<String?>>() to 0)
    verify(
      mock = statement,
      mode = never()
    ).step()
  }

  @Test
  fun `running a query on a thread with an active transaction fails before it runs on any dispatcher`() {
    for ((label, dispatcher) in mapOf(
      "calling thread" to Dispatchers.Unconfined,
      "another thread" to Dispatchers.IO
    )) {
      val connection = testConnection(dispatcher)
      val query = object : Query<String>(connection) {
        override fun run(dbConnection: DbConnectionImpl, isDisposed: () -> Boolean): String =
          error("Rejected query must not run")
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

  @Test
  fun `one-shot operators run their query without listening for table changes`() = runBlocking {
    libraryDatabase(adapterSettings = dispatcherSettings()).use { database ->
      val connection = database.connection
      val names = SELECT
        .COLUMN(AUTHOR.NAME)
        .FROM(AUTHOR)
        .usingConnection(connection)
      val authors = SELECT
        .FROM(AUTHOR)
        .usingConnection(connection)
        .count()
      val expected = listOf(
        names.execute(),
        names
          .takeFirst()
          .execute(),
        names
          .takeFirst()
          .execute(),
        authors.execute()
      )

      val outcome = mockConstruction(TableChanges::class.java).use { construction ->
        listOf(
          names
            .observe()
            .runQueryOnce(),
          names
            .takeFirst()
            .observe()
            .runQueryOnce(),
          names
            .takeFirst()
            .observe()
            .runQueryOnceOrDefault("none"),
          authors
            .observe()
            .runQueryOnce()
        ) to construction.constructed().size
      }

      assertThat(outcome).isEqualTo(expected to 0)
    }
  }
}
