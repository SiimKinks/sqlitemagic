package com.siimkinks.sqlitemagic.connection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.sqlExceptionMessage
import com.siimkinks.sqlitemagic.withWorkers
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

internal class ConnectionPoolTransactionTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun `nested levels commit with the outermost and any failed level rolls back the outermost`() {
    data class Outcome(
      val inner: Boolean,
      val outer: Boolean,
      val balance: Long
    )

    val outcomes = listOf(true, false).associateWith { innerSuccessful ->
      pool(fileName = folder.databasePath("nested-$innerSuccessful.db")).use { pool ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 0
        )
        pool.beginTransaction()
        pool.setBalance(
          id = 2,
          balance = 0
        )
        if (innerSuccessful) {
          pool.setTransactionSuccessful()
        }
        val inner = pool.endTransaction()
        pool.setTransactionSuccessful()
        val outer = pool.endTransaction()
        Outcome(
          inner = inner,
          outer = outer,
          balance = pool.balance(id = 1) + pool.balance(id = 2)
        )
      }
    }

    assertThat(outcomes)
      .containsExactly(
        true,
        Outcome(
          inner = true,
          outer = true,
          balance = 0
        ),
        false,
        Outcome(
          inner = false,
          outer = false,
          balance = 200
        )
      )
  }

  @Test
  fun `a savepoint inside a transaction undoes only its own writes and leaves the transaction to commit`() {
    data class Outcome(
      val failure: String?,
      val committed: Boolean,
      val balances: List<Long>
    )

    val outcomes = listOf("kept", "not kept", "failed").associateWith { case ->
      pool(fileName = folder.databasePath(name = "savepoint-$case.db")).use { pool ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 0
        )
        val failure = runCatching {
          pool.savepoint(
            operation = {
              pool.setBalance(
                id = 2,
                balance = 7
              )
              check(case != "failed") { "operation failed" }
            },
            keep = { case == "kept" }
          )
        }
        pool.setTransactionSuccessful()
        Outcome(
          failure = failure.exceptionOrNull()?.message,
          committed = pool.endTransaction(),
          balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
        )
      }
    }

    assertThat(outcomes).containsExactly(
      "kept",
      Outcome(
        failure = null,
        committed = true,
        balances = listOf(0L, 7L)
      ),
      "not kept",
      Outcome(
        failure = null,
        committed = true,
        balances = listOf(0L, 100L)
      ),
      "failed",
      Outcome(
        failure = "operation failed",
        committed = true,
        balances = listOf(0L, 100L)
      )
    )
  }

  @Test
  fun `the writer is released when the outermost level ends`() {
    pool(fileName = folder.databasePath()).use { pool ->
      withWorkers { workers ->
        pool.beginTransaction()
        pool.beginTransaction()
        pool.endTransaction()
        val waiting = workers.submit {
          pool.setBalance(
            id = 1,
            balance = 5
          )
        }
        val writtenDuringTransaction = runCatching { waiting.get(100, MILLISECONDS) }
        pool.endTransaction()
        waiting.get(5, SECONDS)

        assertThat(writtenDuringTransaction.isFailure).isTrue()
        assertThat(pool.inTransaction).isFalse()
        assertThat(pool.balance(id = 1)).isEqualTo(5L)
      }
    }
  }

  @Test
  fun `transaction misuse fails like framework transactions`() {
    val cases = listOf<Pair<String, ConnectionPool.() -> Unit>>(
      "end without a transaction" to { endTransaction() },
      "mark without a transaction" to { setTransactionSuccessful() },
      "mark twice" to {
        beginTransaction()
        setTransactionSuccessful()
        setTransactionSuccessful()
      },
      "begin a nested level after marking" to {
        beginTransaction()
        setTransactionSuccessful()
        beginTransaction()
      },
      "yield after marking" to {
        beginTransaction()
        setTransactionSuccessful()
        yieldIfContended(sleepMillis = 0)
      },
      "yield a nested level" to {
        beginTransaction()
        beginTransaction()
        yieldIfContended(sleepMillis = 0)
      }
    )

    val messages = pool(fileName = folder.databasePath()).use { pool ->
      cases.map { (label, misuse) ->
        val failure = assertThrows(IllegalStateException::class.java) { pool.misuse() }
        while (pool.inTransaction) {
          pool.endTransaction()
        }
        label to failure.message
      }
    }

    val noTransaction = "Cannot perform this operation because there is no current transaction."
    val marked = "Cannot perform this operation because the transaction has already been marked successful. " +
        "The only thing you can do now is call end()."
    assertThat(messages)
      .containsExactly(
        "end without a transaction" to noTransaction,
        "mark without a transaction" to noTransaction,
        "mark twice" to marked,
        "begin a nested level after marking" to marked,
        "yield after marking" to marked,
        "yield a nested level" to "Cannot perform this operation because a nested transaction is in progress."
      )
      .inOrder()
  }

  @Test
  fun `yielding commits for a waiting writer and the transaction continues`() {
    pool(fileName = folder.databasePath()).use { pool ->
      withWorkers { workers ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 1
        )
        val uncontended = pool.yieldIfContended(sleepMillis = 0)
        val waiting = workers.submit {
          pool.setBalance(
            id = 2,
            balance = 2
          )
        }
        val yielded = pool.yieldToWaitingWriter()
        waiting.get(5, SECONDS)
        pool.setBalance(
          id = 1,
          balance = 99
        )
        val committed = pool.endTransaction()

        assertWithMessage("uncontended yield")
          .that(uncontended)
          .isFalse()
        assertWithMessage("contended yield")
          .that(yielded)
          .isTrue()
        assertWithMessage("a yielded segment counts as committed")
          .that(committed)
          .isTrue()
        assertThat(listOf(pool.balance(id = 1), pool.balance(id = 2)))
          .containsExactly(1L, 2L)
          .inOrder()
      }
    }
  }

  @Test
  fun `a failed nested level keeps the transaction from yielding and rolls it back at its end`() {
    pool(fileName = folder.databasePath()).use { pool ->
      pool.beginTransaction()
      pool.beginTransaction()
      pool.setBalance(
        id = 1,
        balance = 1
      )
      pool.endTransaction()
      val waiter = thread {
        pool.setBalance(
          id = 2,
          balance = 2
        )
      }
      waiter.awaitTimedWaiting()
      val yielded = pool.yieldIfContended(sleepMillis = 0)
      pool.setBalance(
        id = 1,
        balance = 3
      )
      pool.setTransactionSuccessful()
      val committed = pool.endTransaction()
      waiter.join()

      assertThat(listOf(yielded, committed))
        .containsExactly(false, false)
        .inOrder()
      assertThat(listOf(pool.balance(id = 1), pool.balance(id = 2)))
        .containsExactly(100L, 2L)
        .inOrder()
    }
  }

  @Test
  fun `a yield that cannot get the writer back fails later use and ends without touching the next holder's work`() {
    data class Outcome(
      val yieldFailure: String?,
      val secondYield: String?,
      val writeAfterYield: String?,
      val nestedBegin: String?,
      val committed: Boolean,
      val holderCommitted: Boolean?,
      val balances: List<Long>
    )

    val outcome = pool(
      fileName = folder.databasePath(),
      poolTimeout = 300.milliseconds
    ).use { pool ->
      pool.beginTransaction()
      pool.setBalance(
        id = 1,
        balance = 1
      )
      val release = CountDownLatch(1)
      var holderCommitted: Boolean? = null
      lateinit var holder: Thread
      val yieldFailure = sqlExceptionMessage {
        holder = thread(name = "next holder") {
          pool.beginTransaction()
          pool.setBalance(
            id = 2,
            balance = 2
          )
          release.await()
          pool.setTransactionSuccessful()
          holderCommitted = pool.endTransaction()
        }
        pool.yieldToWaitingWriter()
      }
      val secondYield = assertThrows(IllegalStateException::class.java) {
        pool.yieldIfContended(sleepMillis = 0)
      }
      val writeAfterYield = assertThrows(IllegalStateException::class.java) {
        pool.setBalance(
          id = 1,
          balance = 3
        )
      }
      val nestedBegin = assertThrows(IllegalStateException::class.java) {
        pool.beginTransaction()
      }
      val committed = pool.endTransaction()
      release.countDown()
      holder.join()
      Outcome(
        yieldFailure = yieldFailure,
        secondYield = secondYield.message,
        writeAfterYield = writeAfterYield.message,
        nestedBegin = nestedBegin.message,
        committed = committed,
        holderCommitted = holderCommitted,
        balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        yieldFailure = "Timed out after 300ms waiting for the writer connection, held by thread next holder",
        secondYield = DETACHED,
        writeAfterYield = DETACHED,
        nestedBegin = DETACHED,
        committed = true,
        holderCommitted = true,
        balances = listOf(1L, 2L)
      )
    )
  }

  @Test
  fun `a yield that cannot begin the transaction again releases the writer and fails later use`() {
    data class Outcome(
      val yieldFailure: String?,
      val writeAfterYield: String?,
      val readAfterYield: String?,
      val nestedBegin: String?,
      val committed: Boolean,
      val holderCommitted: Boolean?,
      val nextWriterBalance: Long?,
      val balances: List<Long>
    )

    val yielding = Thread.currentThread()
    var begins = 0
    val bundled = BundledSQLiteDriver()
    val driver = object : SQLiteDriver {
      override fun open(fileName: String): SQLiteConnection {
        val connection = bundled.open(fileName)
        return object : SQLiteConnection by connection {
          override fun prepare(sql: String): SQLiteStatement {
            val statement = connection.prepare(sql)
            return object : SQLiteStatement by statement {
              override fun step(): Boolean {
                if (sql == "BEGIN IMMEDIATE" && Thread.currentThread() === yielding && ++begins == 2) {
                  throw SQLiteException("database is locked")
                }
                return statement.step()
              }
            }
          }
        }
      }
    }
    val outcome = pool(
      fileName = folder.databasePath(),
      driver = driver,
      poolTimeout = 300.milliseconds
    ).use { pool ->
      pool.beginTransaction()
      pool.setBalance(
        id = 1,
        balance = 1
      )
      var holderCommitted: Boolean? = null
      val holder = thread(name = "next holder") {
        pool.beginTransaction()
        pool.setBalance(
          id = 2,
          balance = 2
        )
        pool.setTransactionSuccessful()
        holderCommitted = pool.endTransaction()
      }
      val yieldFailure = assertThrows(SQLiteException::class.java) {
        pool.yieldToWaitingWriter()
      }
      holder.join()
      val writeAfterYield = assertThrows(IllegalStateException::class.java) {
        pool.setBalance(
          id = 1,
          balance = 3
        )
      }
      val readAfterYield = assertThrows(IllegalStateException::class.java) {
        pool.balance(id = 1)
      }
      val nestedBegin = assertThrows(IllegalStateException::class.java) {
        pool.beginTransaction()
      }
      val committed = pool.endTransaction()
      var nextWriterBalance: Long? = null
      thread(name = "next writer") {
        pool.setBalance(
          id = 2,
          balance = 4
        )
        nextWriterBalance = pool.balance(id = 2)
      }.join()
      Outcome(
        yieldFailure = yieldFailure.message,
        writeAfterYield = writeAfterYield.message,
        readAfterYield = readAfterYield.message,
        nestedBegin = nestedBegin.message,
        committed = committed,
        holderCommitted = holderCommitted,
        nextWriterBalance = nextWriterBalance,
        balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
      )
    }

    assertThat(outcome).isEqualTo(
      Outcome(
        yieldFailure = "database is locked",
        writeAfterYield = DETACHED,
        readAfterYield = DETACHED,
        nestedBegin = DETACHED,
        committed = true,
        holderCommitted = true,
        nextWriterBalance = 4L,
        balances = listOf(1L, 4L)
      )
    )
  }

  @Test
  fun `a write inside a transaction fails after close and ending the transaction closes the writer`() {
    val driver = TrackingDriver()
    val pool = pool(
      fileName = folder.databasePath(),
      driver = driver
    )
    pool.beginTransaction()
    pool.setBalance(
      id = 1,
      balance = 1
    )
    pool.close()
    val failure = assertThrows(IllegalStateException::class.java) {
      pool.setBalance(
        id = 1,
        balance = 2
      )
    }
    val committed = pool.endTransaction()

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("DB connection closed")
    assertThat(committed).isFalse()
    assertThat(driver.connections.map(TrackingConnection::closed)).containsExactly(true)
  }

  @Test
  fun `ending after SQLite rolled the transaction back keeps the statement failure`() {
    pool(fileName = folder.databasePath()).use { pool ->
      pool.beginTransaction()
      pool.setBalance(
        id = 1,
        balance = 0
      )
      val failure = assertThrows(SQLiteException::class.java) {
        pool.write { it.connection.execSQL("INSERT OR ROLLBACK INTO account(id, balance) VALUES (2, 0)") }
      }
      val committed = pool.endTransaction()
      pool.setBalance(
        id = 2,
        balance = 5
      )

      assertThat(failure)
        .hasMessageThat()
        .contains("UNIQUE constraint failed: account.id")
      assertThat(listOf(committed, pool.inTransaction))
        .containsExactly(false, false)
        .inOrder()
      assertThat(listOf(pool.balance(id = 1), pool.balance(id = 2)))
        .containsExactly(100L, 5L)
        .inOrder()
    }
  }

  @Test
  fun `a statement that makes SQLite roll the transaction back fails later use of the transaction`() {
    data class Outcome(
      val failure: String?,
      val suppressed: Int,
      val writeAfterRollback: String?,
      val readAfterRollback: String?,
      val nestedBegin: String?,
      val yieldAfterRollback: String?,
      val committed: Boolean,
      val balances: List<Long>
    )

    val rollingBack = "INSERT OR ROLLBACK INTO account(id, balance) VALUES (2, 0)"
    listOf<Pair<String, (ConnectionPool) -> Unit>>(
      "savepoint" to { pool ->
        pool.savepoint(
          operation = { pool.heldWriter().connection.execSQL(rollingBack) },
          keep = { true }
        )
      },
      "write" to { pool -> pool.write { it.connection.execSQL(rollingBack) } },
      "read on the writer" to { pool -> pool.read(requiresWriter = true) { it.connection.execSQL(rollingBack) } }
    ).forEach { (label, statement) ->
      val outcome = pool(fileName = folder.databasePath(name = "$label.db")).use { pool ->
        pool.beginTransaction()
        pool.setBalance(
          id = 1,
          balance = 1
        )
        val failure = assertThrows(SQLiteException::class.java) { statement(pool) }
        val writeAfterRollback = assertThrows(IllegalStateException::class.java) {
          pool.setBalance(
            id = 2,
            balance = 3
          )
        }
        val readAfterRollback = assertThrows(IllegalStateException::class.java) {
          pool.balance(id = 1)
        }
        val nestedBegin = assertThrows(IllegalStateException::class.java) {
          pool.beginTransaction()
        }
        val yieldAfterRollback = assertThrows(IllegalStateException::class.java) {
          pool.yieldIfContended(sleepMillis = 0)
        }
        pool.setTransactionSuccessful()
        val committed = pool.endTransaction()
        Outcome(
          failure = failure.message,
          suppressed = failure.suppressed.size,
          writeAfterRollback = writeAfterRollback.message,
          readAfterRollback = readAfterRollback.message,
          nestedBegin = nestedBegin.message,
          yieldAfterRollback = yieldAfterRollback.message,
          committed = committed,
          balances = listOf(pool.balance(id = 1), pool.balance(id = 2))
        )
      }

      assertWithMessage(label)
        .that(outcome)
        .isEqualTo(
          Outcome(
            failure = "Error code: 1555, message: UNIQUE constraint failed: account.id",
            suppressed = 0,
            writeAfterRollback = ROLLED_BACK,
            readAfterRollback = ROLLED_BACK,
            nestedBegin = ROLLED_BACK,
            yieldAfterRollback = ROLLED_BACK,
            committed = false,
            balances = listOf(100L, 100L)
          )
        )
    }
  }

  @Test
  fun `a commit that fails on a deferred foreign key rolls the transaction back`() {
    pool(
      fileName = folder.databasePath(),
      configureConnection = { it.execSQL("PRAGMA foreign_keys = ON") }
    ).use { pool ->
      pool.write { pooled ->
        pooled.connection.execSQL(
          "CREATE TABLE entry(account_id INTEGER REFERENCES account(id) DEFERRABLE INITIALLY DEFERRED)"
        )
      }
      pool.beginTransaction()
      pool.setBalance(
        id = 1,
        balance = 0
      )
      pool.write { it.connection.execSQL("INSERT INTO entry VALUES (99)") }
      pool.setTransactionSuccessful()
      val failure = assertThrows(SQLiteException::class.java, pool::endTransaction)
      pool.setBalance(
        id = 2,
        balance = 5
      )
      val entries = pool.read(requiresWriter = false) { it.connection.long("SELECT count(*) FROM entry") }

      assertThat(failure)
        .hasMessageThat()
        .contains("FOREIGN KEY constraint failed")
      assertThat(listOf(pool.balance(id = 1), pool.balance(id = 2), entries))
        .containsExactly(100L, 5L, 0L)
        .inOrder()
    }
  }

  private fun ConnectionPool.yieldToWaitingWriter(): Boolean {
    val deadline = System.nanoTime() + SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      if (yieldIfContended(sleepMillis = 0)) {
        return true
      }
    }
    return false
  }
}

private const val DETACHED = "Cannot use this transaction because yieldIfContended could not resume it. End the " +
    "transaction; it keeps only the parts committed before the yield."
private const val ROLLED_BACK = "Cannot use this transaction because SQLite rolled it back when a statement " +
    "failed. End the transaction."
