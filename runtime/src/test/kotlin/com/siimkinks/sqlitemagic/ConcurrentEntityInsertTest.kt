package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.connection.databasePath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.Thread.State.TIMED_WAITING
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

internal class ConcurrentEntityInsertTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `an insert of the same entity type waits for another thread's transaction instead of deadlocking`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf("CREATE TABLE books (id TEXT PRIMARY KEY, key TEXT NOT NULL UNIQUE, name TEXT)")
      ),
      fileName = folder.databasePath()
    )
    val adapter = TestAdapter()
    val concurrentThread = AtomicReference<Thread>()
    fun insert(number: Int) = adapter
      .insert(
        TestEntity(
          id = "book-$number",
          key = "key-$number",
          name = "Book $number"
        )
      )
      .usingConnection(connection)
      .execute()

    try {
      withWorkers { workers ->
        val transactional = workers.submit<Future<*>> {
          val transaction = connection.newTransaction()
          try {
            insert(1)
            val concurrent = workers.submit {
              concurrentThread.set(Thread.currentThread())
              insert(TRANSACTION_INSERTS + 1)
            }
            while (!concurrent.isDone && concurrentThread.get()?.state != TIMED_WAITING) {
              Thread.yield()
            }
            (2..TRANSACTION_INSERTS).forEach(::insert)
            transaction.markSuccessful()
            concurrent
          } finally {
            transaction.end()
          }
        }

        transactional
          .get(10, SECONDS)
          .get(10, SECONDS)
      }

      assertThat(
        SELECT
          .RAW("SELECT id FROM books ORDER BY rowid")
          .readAs(STRING)
          .usingConnection(connection)
          .execute()
      ).isEqualTo((1..TRANSACTION_INSERTS + 1).map { "book-$it" })
    } finally {
      connection.pool.close()
    }
  }
}

private const val TRANSACTION_INSERTS = 20
