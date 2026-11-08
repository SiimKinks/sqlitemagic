@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.driver

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.connection.ConnectionOptions
import com.siimkinks.sqlitemagic.connection.ConnectionPool
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.runtime.support.freshDatabasePath
import com.siimkinks.sqlitemagic.runtime.support.long
import com.siimkinks.sqlitemagic.runtime.support.text
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

internal class FrameworkConnectionPoolTest {
  private val path = freshDatabasePath("framework-pool.db")

  @Test
  fun walSurvivesReadersOpeningAndReopening() {
    val journalModes = List(2) {
      frameworkPool().use { pool ->
        val holding = CountDownLatch(pool.maxReaders)
        val release = CountDownLatch(1)
        val readerModes = List(pool.maxReaders) { mutableListOf<String>() }
        val readers = readerModes.map { modes ->
          thread {
            pool.read(requiresWriter = false) { pooled ->
              modes += pooled.connection.text("PRAGMA journal_mode")
              holding.countDown()
              release.await()
            }
          }
        }
        holding.await()
        release.countDown()
        readers.forEach(Thread::join)
        readerModes.flatten() + pool.write { it.connection.text("PRAGMA journal_mode") }
      }
    }

    assertThat(journalModes.flatten()).containsExactlyElementsIn(List(10) { "wal" })
  }

  @Test
  fun connectionsUseTheLibraryDefaults() {
    val settings = frameworkPool().use { pool ->
      listOf(
        pool.write { it.connection.settings() },
        pool.read(requiresWriter = false) { it.connection.settings() }
      )
    }

    assertThat(settings)
      .containsExactly(
        listOf(3000L, 1L, 524288L, 1000L, 0L),
        listOf(3000L, 1L, 524288L, 1000L, 1L)
      )
      .inOrder()
  }

  @Test
  fun readersSeeTheLastCommittedStateDuringAWriterTransaction() {
    val workers = Executors.newSingleThreadExecutor()
    try {
      frameworkPool().use { pool ->
        pool.beginTransaction()
        pool.write { it.connection.execSQL("UPDATE account SET balance = 0") }
        val otherThread = workers
          .submit<Long> { pool.balance() }
          .get(5, SECONDS)
        val transactionThread = pool.balance()
        pool.setTransactionSuccessful()
        pool.endTransaction()
        val afterCommit = workers
          .submit<Long> { pool.balance() }
          .get(5, SECONDS)

        assertThat(listOf(otherThread, transactionThread, afterCommit))
          .containsExactly(100L, 0L, 0L)
          .inOrder()
      }
    } finally {
      workers.shutdownNow()
    }
  }

  @Test
  fun readersOpenWithoutWritingWhileTheWriterHoldsATransactionAfterTheLocaleChanges() {
    val defaultLocale = Locale.getDefault()
    val workers = Executors.newSingleThreadExecutor()
    try {
      frameworkPool().use { pool ->
        pool.write {}
        Locale.setDefault(
          when (defaultLocale.language) {
            Locale.GERMAN.language -> Locale.FRANCE
            else -> Locale.GERMANY
          }
        )
        pool.beginTransaction()
        pool.write { it.connection.execSQL("UPDATE account SET balance = 0") }
        val read = workers
          .submit<List<Long>> {
            pool.read(requiresWriter = false) { pooled ->
              listOf(
                pooled.connection.long("SELECT balance FROM account WHERE id = 1"),
                pooled.connection.long("SELECT count(*) FROM account ORDER BY 'a' COLLATE LOCALIZED"),
                pooled.connection.long("SELECT count(*) FROM account ORDER BY 'a' COLLATE UNICODE")
              )
            }
          }
          .get(10, SECONDS)
        pool.endTransaction()

        assertThat(read)
          .containsExactly(100L, 1L, 1L)
          .inOrder()
      }
    } finally {
      Locale.setDefault(defaultLocale)
      workers.shutdownNow()
    }
  }

  @Test
  fun temporaryTablesAreReadThroughTheWriter() {
    frameworkPool().use { pool ->
      pool.write { it.connection.execSQL("CREATE TEMP TABLE scratch AS SELECT 1 AS x") }
      pool.balance()

      val throughWriter = pool.read(requiresWriter = true) { it.connection.long("SELECT count(*) FROM scratch") }
      val throughReader = runCatching {
        pool.read(requiresWriter = false) { it.connection.long("SELECT count(*) FROM scratch") }
      }

      assertThat(throughWriter).isEqualTo(1L)
      assertThat(throughReader.exceptionOrNull())
        .hasMessageThat()
        .contains("no such table: scratch")
    }
  }

  @Test
  fun writesUseNativeMutationResultsAndKeepFrameworkExceptions() {
    frameworkPool().use { pool ->
      val (native, rowId) = pool.write { pooled ->
        pooled.withStatement("INSERT INTO account(balance) VALUES (5)") { statement ->
          (pooled.mutations === pooled.connection) to pooled.mutations.executeInsert(statement)
        }
      }
      val failure = assertThrows(SQLiteConstraintException::class.java) {
        pool.write { pooled ->
          pooled.withStatement(
            sql = "INSERT INTO account(id, balance) VALUES (1, 0)",
            block = pooled.mutations::executeInsert
          )
        }
      }

      assertThat(native to rowId).isEqualTo(true to 2L)
      assertThat(failure)
        .hasMessageThat()
        .contains("UNIQUE constraint failed: account.id")
    }
  }

  @Test
  fun aCommitFailingOnADeferredForeignKeyRollsTheTransactionBack() {
    frameworkPool(configureConnection = { it.execSQL("PRAGMA foreign_keys = ON") }).use { pool ->
      pool.write {
        it.connection.execSQL(
          "CREATE TABLE entry(account_id INTEGER REFERENCES account(id) DEFERRABLE INITIALLY DEFERRED)"
        )
      }
      pool.beginTransaction()
      pool.write { it.connection.execSQL("UPDATE account SET balance = 0") }
      pool.write { it.connection.execSQL("INSERT INTO entry VALUES (99)") }
      pool.setTransactionSuccessful()
      val failure = assertThrows(SQLiteConstraintException::class.java) { pool.endTransaction() }
      pool.write { it.connection.execSQL("UPDATE account SET balance = 5") }
      pool.beginTransaction()
      pool.endTransaction()
      val entries = pool.read(requiresWriter = false) { it.connection.long("SELECT count(*) FROM entry") }

      assertThat(failure)
        .hasMessageThat()
        .contains("FOREIGN KEY constraint failed")
      assertThat(listOf(pool.balance(), entries))
        .containsExactly(5L, 0L)
        .inOrder()
    }
  }

  @Test
  fun endingAfterSQLiteRolledTheTransactionBackKeepsTheStatementFailure() {
    frameworkPool().use { pool ->
      pool.beginTransaction()
      pool.write { it.connection.execSQL("UPDATE account SET balance = 0") }
      val failure = assertThrows(SQLiteConstraintException::class.java) {
        pool.write { it.connection.execSQL("INSERT OR ROLLBACK INTO account(id, balance) VALUES (1, 0)") }
      }
      val writeAfterRollback = assertThrows(IllegalStateException::class.java) {
        pool.write { it.connection.execSQL("UPDATE account SET balance = 5") }
      }
      val committed = pool.endTransaction()

      assertThat(failure)
        .hasMessageThat()
        .contains("UNIQUE constraint failed: account.id")
      assertThat(writeAfterRollback)
        .hasMessageThat()
        .isEqualTo(
          "Cannot use this transaction because SQLite rolled it back when a statement failed. End the transaction."
        )
      assertThat(listOf(committed, pool.inTransaction))
        .containsExactly(false, false)
        .inOrder()
      assertThat(pool.balance()).isEqualTo(100L)
    }
  }

  private fun frameworkPool(configureConnection: ((SQLiteConnection) -> Unit)? = null) = ConnectionPool(
    driver = FrameworkSQLiteDriver(),
    fileName = path,
    options = ConnectionOptions(configureConnection = configureConnection),
    schema = AccountSchema
  )

  private fun ConnectionPool.balance() = read(requiresWriter = false) {
    it.connection.long("SELECT balance FROM account WHERE id = 1")
  }

  private fun SQLiteConnection.settings() = listOf(
    long("PRAGMA busy_timeout"),
    long("PRAGMA synchronous"),
    long("PRAGMA journal_size_limit"),
    long("PRAGMA wal_autocheckpoint"),
    long("PRAGMA query_only")
  )
}
