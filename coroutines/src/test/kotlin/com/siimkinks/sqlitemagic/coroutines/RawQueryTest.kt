package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.personDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

internal class RawQueryTest {
  @Test
  fun `declared queries stream while undeclared ones run once`() = runBlocking {
    personDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = dispatcherSettings())
      val names = SELECT
        .RAW("SELECT name FROM person WHERE age < 18 ORDER BY id")
        .readAs(PERSON.NAME)
        .usingConnection(connection)
      val streamed = collectUnconfined(
        names
          .observes(PERSON)
          .observe()
          .runQuery()
      )
      val firstStreamed = collectUnconfined(
        names
          .observes(PERSON)
          .takeFirst()
          .observe()
          .runQuery()
      )
      val once = names.await()
      val emptyFirst = SELECT
        .RAW("SELECT name FROM person WHERE id < 0")
        .readAs(STRING)
        .usingConnection(connection)
        .takeFirst()
        .await()
      val columnCount = SELECT
        .RAW("PRAGMA table_info('person')")
        .usingConnection(connection)
        .toCursor()
        .await()
        .use(Cursor::getCount)
      UPDATE
        .RAW("UPDATE person SET name = ? WHERE id = ?", "Kid", 3L)
        .notifies(PERSON)
        .usingConnection(connection)
        .execute()
      streamed.cancel()
      firstStreamed.cancel()

      assertThat(listOf(streamed.signals, firstStreamed.signals, FlowSignals(listOf(once, emptyFirst, columnCount))))
        .isEqualTo(
          listOf(
            FlowSignals(values = listOf(listOf(null, "Linus"), listOf("Kid", "Linus"))),
            FlowSignals(values = listOf("Kid")),
            FlowSignals(values = listOf(listOf(null, "Linus"), null, 6))
          )
        )
    }
  }

  @Test
  fun `undeclared queries reject a call on a thread with an active transaction`() {
    personDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = dispatcherSettings())
      val raw = SELECT
        .RAW("SELECT 1")
        .readAs(LONG)
        .usingConnection(connection)
      val transaction = connection.newTransaction()
      val messages = try {
        listOf<suspend () -> Any?>(
          raw::await,
          raw.takeFirst()::await,
          raw.toCursor()::await
        ).map { call ->
          assertThrows(IllegalStateException::class.java) { runBlocking { call() } }.message
        }
      } finally {
        transaction.end()
      }

      assertThat(messages).isEqualTo(List(3) { "Cannot subscribe to observable query in a transaction." })
    }
  }

  @Test
  fun `undeclared row and cursor queries of a closed connection fail with NoSuchElementException`() {
    val raw = SELECT
      .RAW("SELECT 1")
      .readAs(LONG)
      .usingConnection(closedConnection(adapterSettings = dispatcherSettings()))

    val failures = listOf<suspend () -> Any?>(
      raw::await,
      raw.toCursor()::await
    ).map { call -> runCatching { runBlocking { call() } }.exceptionOrNull()?.javaClass }

    assertThat(failures).isEqualTo(List(2) { NoSuchElementException::class.java })
  }
}
