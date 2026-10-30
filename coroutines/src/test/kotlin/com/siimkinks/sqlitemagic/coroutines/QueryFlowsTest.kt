package com.siimkinks.sqlitemagic.coroutines

import android.util.Log
import androidx.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.BOOK
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.closedConnection
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.libraryDatabase
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

internal class QueryFlowsTest {
  @Test
  fun `collecting emits the rows and again after each write to a queried table but not to other tables`() =
    runBlocking {
      libraryDatabase().use { database ->
        val connection = database.dbConnection(adapterSettings = dispatcherSettings())
        val names = collectUnconfined(
          authorNames(connection)
            .observe()
            .runQuery()
        )

        UPDATE
          .RAW("UPDATE author SET name = ? WHERE id = ?", "Lovelace", 1L)
          .notifies(AUTHOR)
          .usingConnection(connection)
          .execute()
        UPDATE
          .RAW("UPDATE book SET title = ? WHERE id = ?", "Persuasion", 2L)
          .notifies(BOOK)
          .usingConnection(connection)
          .execute()
        names.cancel()

        assertThat(names.signals).isEqualTo(
          FlowSignals(
            values = listOf(
              listOf("Ada", "Grace", "Linus"),
              listOf("Lovelace", "Grace", "Linus")
            )
          )
        )
      }
    }

  @Test
  fun `cancelling the collection stops the emissions`() = runBlocking {
    val connection = mockConnection()
    val queries = collectUnconfined(
      authorNames(connection)
        .observe()
    )

    connection.sendTableTrigger("author")
    queries.cancel()
    connection.sendTableTrigger("author")

    assertThat(queries.signals.values).hasSize(2)
  }

  @Test
  fun `observing does no database work until collected and every collection runs the query again`() =
    runBlocking {
      val database = mock<SupportSQLiteDatabase>()
      @Suppress("UnusedFlow")
      authorNames(testDbConnection(database))
        .observe()
        .runQuery()
      verifyNoInteractions(database)

      libraryDatabase().use { library ->
        val names = authorNames(library.dbConnection(adapterSettings = dispatcherSettings()))
          .observe()
          .runQuery()
        val first = names.first()
        library.execSQL("UPDATE author SET name = 'Lovelace' WHERE id = 1")

        assertThat(listOf(first, names.first())).isEqualTo(
          listOf(
            listOf("Ada", "Grace", "Linus"),
            listOf("Lovelace", "Grace", "Linus")
          )
        )
      }
    }

  @Test
  fun `a query that reads no table emits once and completes`() = runBlocking<Unit> {
    libraryDatabase().use { database ->
      val values = SELECT
        .COLUMN(8L.asValue())
        .usingConnection(database.dbConnection(adapterSettings = dispatcherSettings()))
        .observe()
        .runQuery()
        .toList()

      assertThat(values).containsExactly(listOf(8L))
    }
  }

  @Test
  fun `writes in a transaction emit once after the outermost commit and never after a rollback`() =
    runBlocking {
      libraryDatabase().use { database ->
        val connection = database.dbConnection(adapterSettings = dispatcherSettings())
        val names = collectUnconfined(
          authorNames(connection)
            .observe()
            .runQuery()
        )
        fun rename(id: Long, name: String) = UPDATE
          .RAW("UPDATE author SET name = ? WHERE id = ?", name, id)
          .notifies(AUTHOR)
          .usingConnection(connection)
          .execute()

        val outer = connection.newTransaction()
        rename(id = 1L, name = "Lovelace")
        val inner = connection.newTransaction()
        rename(id = 2L, name = "Hopper")
        inner.markSuccessful()
        inner.end()
        val emittedInsideTransaction = names.signals.values.size
        outer.markSuccessful()
        outer.end()
        val rolledBack = connection.newTransaction()
        rename(id = 3L, name = "Torvalds")
        rolledBack.end()
        names.cancel()

        assertThat(emittedInsideTransaction to names.signals).isEqualTo(
          1 to FlowSignals(
            values = listOf(
              listOf("Ada", "Grace", "Linus"),
              listOf("Lovelace", "Hopper", "Linus")
            )
          )
        )
      }
    }

  @Test
  fun `collecting on a thread with an active transaction fails with the transaction guard`() {
    libraryDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = dispatcherSettings())
      val transaction = connection.newTransaction()
      val failure = try {
        assertThrows(IllegalStateException::class.java) {
          runBlocking {
            authorNames(connection)
              .observe()
              .first()
          }
        }
      } finally {
        transaction.end()
      }

      assertThat(failure.message).isEqualTo("Cannot subscribe to observable query in a transaction.")
    }
  }

  @Test
  fun `a failing query fails the collection`() {
    libraryDatabase().use { database ->
      val failure = assertThrows(SQLiteException::class.java) {
        runBlocking {
          SELECT
            .FROM(AUTHOR)
            .WHERE(
              "EXISTS (SELECT 1 FROM missing)".expr(BOOLEAN)
                .observes(AUTHOR)
            )
            .usingConnection(database.dbConnection(adapterSettings = dispatcherSettings()))
            .observe()
            .runQuery()
            .first()
        }
      }

      assertThat(failure)
        .hasMessageThat()
        .endsWith("message: no such table: missing")
    }
  }

  @Test
  fun `closing the connection completes the collection without a pending emission`() = runBlocking {
    val connection = mockConnection()
    val entered = CountDownLatch(1)
    val release = CompletableDeferred<Unit>()
    val emissions = AtomicInteger()
    val collection = launch(Dispatchers.Default) {
      authorNames(connection)
        .observe()
        .collect {
          emissions.incrementAndGet()
          entered.countDown()
          release.await()
        }
    }
    check(entered.await(5, SECONDS))

    connection.sendTableTrigger("author")
    mockStatic(Log::class.java).use {
      connection.close()
    }
    release.complete(Unit)
    withTimeout(5.seconds) { collection.join() }

    assertThat(emissions.get()).isEqualTo(1)
  }

  @Test
  fun `observing a closed connection completes without emitting`() = runBlocking {
    for ((label, observe) in mapOf<String, suspend (DbConnectionImpl) -> List<*>>(
      "table query" to { connection ->
        authorNames(connection)
          .observe()
          .toList()
      },
      "source-free query" to { connection ->
        SELECT
          .COLUMN(8L.asValue())
          .usingConnection(connection)
          .observe()
          .toList()
      }
    )) {
      assertWithMessage(label)
        .that(observe(closedConnection(adapterSettings = dispatcherSettings())))
        .isEmpty()
    }
  }

  @Test
  fun `changes during an emission collapse into one further emission that starts after the last change`() =
    runBlocking {
      val connection = mockConnection()
      val version = AtomicInteger()
      val entered = CountDownLatch(1)
      val release = CompletableDeferred<Unit>()
      val seenVersions = async(Dispatchers.Default) {
        authorNames(connection)
          .observe()
          .map { version.get() }
          .onEach { seen ->
            if (seen == 0) {
              entered.countDown()
              release.await()
            }
          }
          .take(2)
          .toList()
      }
      check(entered.await(5, SECONDS))

      repeat(100) {
        version.incrementAndGet()
        connection.sendTableTrigger("author")
      }
      release.complete(Unit)

      assertThat(withTimeout(5.seconds) { seenVersions.await() })
        .containsExactly(0, 100)
        .inOrder()
    }

  @Test
  fun `a write made during an emission emits again after that emission returns`() = runBlocking {
    val connection = mockConnection()
    val events = mutableListOf<String>()
    var emissions = 0

    authorNames(connection)
      .observe()
      .take(2)
      .collect {
        val emission = ++emissions
        events += "start $emission"
        if (emission == 1) {
          connection.sendTableTrigger("author")
        }
        events += "end $emission"
      }

    assertThat(events)
      .containsExactly("start 1", "end 1", "start 2", "end 2")
      .inOrder()
  }

  @Test
  fun `observing a query with an unbound parameter fails at the observe call`() {
    val parameter = true.asValue()
      .asParameter("x")
    val plan = SELECT.COLUMN(parameter)
      .WHERE(parameter)
      .compile()

    val failure = assertThrows(IllegalArgumentException::class.java, plan::observe)

    assertThat(failure.message).isEqualTo("Missing parameter 'x'")
  }

  private fun authorNames(connection: DbConnectionImpl) = SELECT
    .COLUMN(AUTHOR.NAME)
    .FROM(AUTHOR)
    .ORDER_BY(AUTHOR.ID.ASC())
    .usingConnection(connection)
}
