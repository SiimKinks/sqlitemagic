package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.QueryObservation
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock

internal class QueryObservationTest {
  @Test
  fun `registration delivers changes of the tables the query reads until it is closed`() {
    val connection = testDbConnection(mock<SupportSQLiteDatabase>())
    val observation = authorNames(connection)
    val recorder = TriggerRecorder()
    val registration = observation.register(
      connection = observation.connection(),
      observer = recorder
    )

    connection.sendTableTrigger("author")
    connection.sendTableTrigger("book")
    registration.close()
    connection.sendTableTrigger("author")

    assertThat(recorder.triggers).containsExactly(setOf("author"))
  }

  @Test
  fun `registering on a thread with an active transaction fails without registering`() {
    val connection = newConnection().connection
    val observation = authorNames(connection)
    val recorder = TriggerRecorder()
    val transaction = connection.newTransaction()
    val failure = try {
      assertThrows(IllegalStateException::class.java) {
        observation.register(
          connection = connection,
          observer = recorder
        )
      }
    } finally {
      transaction.end()
    }
    connection.sendTableTrigger("author")

    assertThat(failure.message to recorder.triggers)
      .isEqualTo("Cannot subscribe to observable query in a transaction." to emptyList<Set<String>>())
  }

  @Test
  fun `registering on a closed connection reports closed before returning`() {
    for ((label, observation) in mapOf<String, (DbConnectionImpl) -> QueryObservation<*>>(
      "table query" to ::authorNames,
      "source-free query" to ::sourceFree
    )) {
      val connection = closedConnection()
      val recorder = TriggerRecorder()

      observation(connection)
        .register(
          connection = connection,
          observer = recorder
        )
        .close()

      assertWithMessage(label)
        .that(recorder.closeCalls)
        .isEqualTo(1)
    }
  }

  @Test
  fun `only queries that read tables observe changes`() {
    val connection = testDbConnection(mock<SupportSQLiteDatabase>())

    assertThat(listOf(authorNames(connection), sourceFree(connection)).map(QueryObservation<*>::observesChanges))
      .containsExactly(true, false)
      .inOrder()
  }

  @Test
  fun `connection resolves the configured connection or else the current default connection`() {
    val previousConnection = SqliteMagic.defaultConnection
    try {
      val configured = testDbConnection(mock<SupportSQLiteDatabase>())
      val defaults = List(2) { testDbConnection(mock<SupportSQLiteDatabase>()) }
      val observations = listOf(
        authorNames(configured),
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .compile()
          .observation()
      )

      val resolved = defaults.map { default ->
        SqliteMagic.defaultConnection = default
        observations.map(QueryObservation<*>::connection)
      }

      assertThat(resolved).isEqualTo(defaults.map { default -> listOf(configured, default) })
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }

  private fun authorNames(connection: DbConnectionImpl) = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(AUTHOR)
    .usingConnection(connection)
    .compile()
    .observation()

  private fun sourceFree(connection: DbConnectionImpl) = SELECT
    .COLUMN(8L.asValue())
    .usingConnection(connection)
    .compile()
    .observation()
}
