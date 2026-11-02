package com.siimkinks.sqlitemagic

import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.connection.awaitTimedWaiting
import com.siimkinks.sqlitemagic.connection.databasePath
import com.siimkinks.sqlitemagic.internal.LeaseOwner
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class TransactionOwnerHooksTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val connection = testDbConnection(
    database = TestGeneratedDatabase(
      tableCount = 1,
      schema = listOf("CREATE TABLE books (title TEXT NOT NULL)")
    )
  )

  @After
  fun closeConnection() = connection.pool.close()

  @Test
  fun `blocking calls on any thread of a coroutine transaction join it and publish their changes after it commits`() {
    data class Outcome(
      val publishedBeforeCommit: List<Set<String>>,
      val published: List<Set<String>>,
      val titles: List<String>
    )

    val recorder = connection.recordTriggers()
    val owner = ReactiveAdapterSupport.transactionOwner(connection)
    val transaction = on(owner) { connection.newTransaction() }
    on(owner) {
      connection.inTransaction {
        connection.insertBook("Dune")
        connection.sendTableTrigger("books")
      }
    }
    on(owner) {
      connection.insertBook("Emma")
      connection.sendTableTriggers(arrayOf("books", "authors"))
    }
    val publishedBeforeCommit = recorder.triggers.toList()
    on(owner) {
      transaction.markSuccessful()
      transaction.end()
    }
    ReactiveAdapterSupport.releaseTransactionOwner(owner)

    assertThat(
      Outcome(
        publishedBeforeCommit = publishedBeforeCommit,
        published = recorder.triggers,
        titles = connection.bookTitles()
      )
    ).isEqualTo(
      Outcome(
        publishedBeforeCommit = emptyList(),
        published = listOf(setOf("books", "authors")),
        titles = listOf("Dune", "Emma")
      )
    )
  }

  @Test
  fun `a coroutine transaction yields through the hooks and its later segment rolls back on failure`() {
    data class Outcome(
      val yielded: Boolean,
      val titles: List<String>
    )

    val fileConnection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf("CREATE TABLE books (title TEXT NOT NULL)")
      ),
      fileName = folder.databasePath()
    )
    val outcome = try {
      val owner = ReactiveAdapterSupport.transactionOwner(fileConnection)
      val transaction = on(owner) {
        fileConnection
          .newTransaction()
          .also { fileConnection.insertBook("Dune") }
      }
      val waiter = thread { fileConnection.insertBook("Emma") }
      waiter.awaitTimedWaiting()
      val yielded = ReactiveAdapterSupport.yieldTransaction(owner)
      waiter.join()
      ReactiveAdapterSupport.resumeTransaction(owner)
      on(owner) { fileConnection.insertBook("Ulysses") }
      on(owner) { transaction.end() }
      ReactiveAdapterSupport.releaseTransactionOwner(owner)
      Outcome(
        yielded = yielded,
        titles = fileConnection.bookTitles()
      )
    } finally {
      fileConnection.pool.close()
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        yielded = true,
        titles = listOf("Dune", "Emma")
      )
    )
  }

  private fun <T> on(owner: LeaseOwner, block: () -> T): T {
    val result = AtomicReference<Result<T>>()
    thread {
      val previous = ReactiveAdapterSupport.installLeaseOwner(owner)
      try {
        result.set(runCatching(block))
      } finally {
        ReactiveAdapterSupport.restoreLeaseOwner(
          owner = owner,
          previous = previous
        )
      }
    }.join()
    return result
      .get()
      .getOrThrow()
  }

  private fun DbConnectionImpl.insertBook(title: String) = write {
    it.connection.execSQL("INSERT INTO books VALUES ('$title')")
  }

  private fun DbConnectionImpl.bookTitles() = read(requiresWriter = false) { pooled ->
    pooled.connection.query("SELECT title FROM books ORDER BY rowid") { it.getText(0) }
  }
}
