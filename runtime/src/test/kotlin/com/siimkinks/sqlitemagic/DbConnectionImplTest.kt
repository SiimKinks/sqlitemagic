package com.siimkinks.sqlitemagic

import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.connection.MEMORY_DATABASE
import com.siimkinks.sqlitemagic.connection.TrackingConnection
import com.siimkinks.sqlitemagic.connection.TrackingDriver
import com.siimkinks.sqlitemagic.connection.TrackingStatement
import com.siimkinks.sqlitemagic.connection.awaitTimedWaiting
import com.siimkinks.sqlitemagic.connection.databasePath
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mockStatic
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

internal class DbConnectionImplTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val connections = mutableListOf<DbConnectionImpl>()

  @After
  fun closeConnections() = connections.forEach { it.pool.close() }

  @Test
  fun `close completes observers and closes the pool exactly once`() {
    val driver = TrackingDriver()
    val connection = testDbConnection(driver = driver)
    connection.write { it.execute("SELECT 1") }
    val observer = connection.recordTriggers()

    mockStatic(Log::class.java)
      .use {
        connection.close()
        connection.close()
      }
    connection.sendTableTrigger("books")
    val failure = assertThrows(IllegalStateException::class.java) {
      connection.write {}
    }

    assertThat(observer.triggers).isEmpty()
    assertThat(observer.closeCalls).isEqualTo(1)
    assertThat(driver.connections.map(TrackingConnection::closed)).containsExactly(true)
    assertThat(
      driver.connections
        .single()
        .statements
        .map(TrackingStatement::closed)
    ).doesNotContain(false)
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("DB connection closed")
  }

  @Test
  fun `entity SQL caches stay distinct per module and table and stable per connection`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 2,
        submoduleTableCounts = mapOf("feature" to 1)
      )
    )
    val caches = listOf(null to 0, null to 1, "feature" to 0).map { (moduleName, tablePosition) ->
      connection.entitySql(
        moduleName = moduleName,
        tablePosition = tablePosition
      )
    }

    assertThat(caches.toSet()).hasSize(3)
    assertThat(
      connection.entitySql(
        moduleName = "feature",
        tablePosition = 0
      )
    ).isSameInstanceAs(caches[2])
    val failure = assertThrows(IllegalStateException::class.java) {
      connection.entitySql(
        moduleName = "missing",
        tablePosition = 0
      )
    }
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Submodule missing is not part of this database")
  }

  @Test
  fun `clear data runs on the writer and only publishes returned table names`() {
    for ((label, clearedTables) in mapOf(
      "no triggers" to MutableScatterSet(),
      "cleared tables" to MutableScatterSet<String>().apply { addAll(arrayOf("books", "authors")) }
    )) {
      val cleared = mutableListOf<SQLiteConnection>()
      val driver = TrackingDriver()
      val connection = testDbConnection(
        database = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
          override fun clearData(connection: SQLiteConnection) = clearedTables.also { cleared += connection }
        },
        driver = driver,
        fileName = folder.databasePath("$label.db")
      ).also(connections::add)
      val observer = connection.recordTriggers()

      connection.clearData()

      assertWithMessage(label)
        .that(cleared)
        .containsExactly(driver.connections.first())
      assertWithMessage(label)
        .that(observer.triggers)
        .isEqualTo(
          when {
            clearedTables.isEmpty() -> emptyList()
            else -> listOf(setOf("books", "authors"))
          }
        )
    }
  }

  @Test
  fun `clear data that fails inside a transaction rolls the whole transaction back`() {
    val connection = testDbConnection(
      database = object : GeneratedDatabase by TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf("CREATE TABLE books (title TEXT NOT NULL)", "INSERT INTO books VALUES ('Dune')")
      ) {
        override fun clearData(connection: SQLiteConnection): MutableScatterSet<String> {
          connection.execSQL("DELETE FROM books")
          connection.execSQL("DELETE FROM missing")
          return MutableScatterSet()
        }
      }
    ).also(connections::add)

    connection.newTransaction()
      .use { transaction ->
        runCatching(connection::clearData)
        transaction.markSuccessful()
      }

    assertThat(connection.bookTitles()).containsExactly("Dune")
  }

  @Test
  fun `multiple table triggers outside transactions publish distinct names immediately`() {
    val connection = testDbConnection()
    val observer = connection.recordTriggers()

    connection.sendTableTriggers(arrayOf("books", "authors", "books"))

    assertThat(observer.triggers).containsExactly(setOf("books", "authors"))
  }

  @Test
  fun `published transaction table storage remains an independent snapshot`() {
    val connection = testDbConnection()
    val published = mutableListOf<Set<String>>()
    connection.tableObservers.add(
      tables = null,
      observer = object : TableChangeObserver {
        override fun onTablesChanged(tables: Set<String>) {
          published += tables
        }

        override fun onClosed() = Unit
      }
    )
    val first = connection.newTransaction()
    connection.sendTableTrigger("books")
    first.markSuccessful()
    first.end()
    val firstPublished = published.single()

    val second = connection.newTransaction()
    connection.sendTableTriggers(arrayOf("authors", "books"))
    second.markSuccessful()
    second.end()

    assertThat(firstPublished).containsExactly("books")
    assertThat(published.last()).containsExactly("authors", "books")
  }

  @Test
  fun `transaction handles are shared and reject an absent transaction with one message`() {
    val connection = bookConnection()
    val transaction = connection.newTransaction()
    val nested = connection.newTransaction()

    assertThat(nested).isSameInstanceAs(transaction)
    connection.insertBook("Dune")
    nested.close()
    assertThat(connection.hasActiveTransaction).isTrue()
    transaction.end()
    assertThat(connection.hasActiveTransaction).isFalse()
    assertThat(connection.bookTitles()).isEmpty()

    val calls = mapOf(
      "end" to transaction::end,
      "close" to transaction::close,
      "markSuccessful" to transaction::markSuccessful
    )
    for ((label, call) in calls) {
      val failure = assertThrows(IllegalStateException::class.java, call)
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("Cannot perform this operation because there is no current transaction.")
    }
  }

  @Test
  fun `yielding commits for a waiting writer after the converted sleep and the transaction continues`() {
    val connection = bookConnection(fileName = folder.databasePath())
    val transaction = connection.newTransaction()
    connection.insertBook("Dune")
    val uncontended = listOf(
      transaction.yieldIfContendedSafely(),
      transaction.yieldIfContendedSafely(
        sleepAmount = 1L,
        sleepUnit = SECONDS
      )
    )
    val waiter = thread { connection.insertBook("Emma") }
    waiter.awaitTimedWaiting()
    val start = System.nanoTime()
    val yielded = transaction.yieldIfContendedSafely(
      sleepAmount = 200_000L,
      sleepUnit = MICROSECONDS
    )
    val yieldMillis = NANOSECONDS.toMillis(System.nanoTime() - start)
    waiter.join()
    connection.insertBook("Kernel")
    transaction.markSuccessful()
    transaction.end()

    assertThat(uncontended).containsExactly(false, false)
    assertThat(yielded).isTrue()
    assertThat(yieldMillis).isAtLeast(200L)
    assertThat(connection.bookTitles())
      .containsExactly("Dune", "Emma", "Kernel")
      .inOrder()
  }

  @Test
  fun `table triggers queued before a yielded commit publish when the transaction ends without success`() {
    val connection = bookConnection(fileName = folder.databasePath())
    val observer = connection.recordTriggers()
    val transaction = connection.newTransaction()
    connection.insertBook("Dune")
    connection.sendTableTrigger("books")
    val waiter = thread { connection.insertBook("Emma") }
    waiter.awaitTimedWaiting()
    val yielded = transaction.yieldIfContendedSafely()
    waiter.join()
    connection.insertBook("Kernel")
    val beforeEnd = observer.triggers.toList()
    transaction.end()

    assertThat(yielded).isTrue()
    assertThat(beforeEnd).isEmpty()
    assertThat(observer.triggers).containsExactly(setOf("books"))
    assertThat(connection.bookTitles())
      .containsExactly("Dune", "Emma")
      .inOrder()
  }

  @Test
  fun `table triggers queued before a yielded commit publish when the final commit fails`() {
    val connection = testDbConnection(
      database = object : GeneratedDatabase by TestGeneratedDatabase(
        tableCount = 1,
        schema = listOf(
          "CREATE TABLE books (title TEXT NOT NULL)",
          "CREATE TABLE authors (id INTEGER PRIMARY KEY)",
          "CREATE TABLE credits (author INTEGER REFERENCES authors (id) DEFERRABLE INITIALLY DEFERRED)"
        )
      ) {
        override fun applySchemaPragmas(connection: SQLiteConnection) = connection.execSQL("PRAGMA foreign_keys = ON")
      },
      fileName = folder.databasePath()
    ).also(connections::add)
    val observer = connection.recordTriggers()
    val transaction = connection.newTransaction()
    connection.insertBook("Dune")
    connection.sendTableTrigger("books")
    val waiter = thread { connection.insertBook("Emma") }
    waiter.awaitTimedWaiting()
    val yielded = transaction.yieldIfContendedSafely()
    waiter.join()
    connection.write { it.connection.execSQL("INSERT INTO credits VALUES (7)") }
    connection.sendTableTrigger("credits")
    transaction.markSuccessful()
    val failure = sqliteErrorMessage(transaction::end)

    assertThat(yielded).isTrue()
    assertThat(failure).contains("FOREIGN KEY constraint failed")
    assertThat(observer.triggers).containsExactly(setOf("books", "credits"))
    assertThat(connection.bookTitles())
      .containsExactly("Dune", "Emma")
      .inOrder()
  }

  @Test
  fun `subscription guard observes only transactions on the subscribing thread`() {
    val connection = testDbConnection()
    connection.ensureNotInTransaction()
    val transaction = connection.newTransaction()

    val failure = assertThrows(IllegalStateException::class.java) {
      connection.ensureNotInTransaction()
    }
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Cannot subscribe to observable query in a transaction.")
    withWorkers { workers ->
      val otherThreadActive = workers
        .submit<Boolean> {
          connection.ensureNotInTransaction()
          connection.hasActiveTransaction
        }
        .get(5L, SECONDS)
      assertThat(otherThreadActive).isFalse()
    }
    transaction.end()
    connection.ensureNotInTransaction()
  }

  @Test
  fun `committed notifications follow the commit and restore transaction state first`() {
    data class Notification(
      val tables: Set<String>,
      val inTransaction: Boolean,
      val committedTitles: List<String>
    )

    val connection = bookConnection(fileName = folder.databasePath())
    val notifications = mutableListOf<Notification>()
    connection.tableObservers.add(
      tables = null,
      observer = object : TableChangeObserver {
        override fun onTablesChanged(tables: Set<String>) {
          notifications += Notification(
            tables = tables.toSet(),
            inTransaction = connection.hasActiveTransaction,
            committedTitles = connection.bookTitles()
          )
        }

        override fun onClosed() = Unit
      }
    )
    val transaction = connection.newTransaction()
    connection.insertBook("Dune")
    connection.sendTableTrigger("books")
    connection.sendTableTriggers(arrayOf("authors", "books"))
    transaction.markSuccessful()
    val beforeEnd = notifications.toList()
    transaction.end()

    assertThat(beforeEnd).isEmpty()
    assertThat(notifications).containsExactly(
      Notification(
        tables = setOf("books", "authors"),
        inTransaction = false,
        committedTitles = listOf("Dune")
      )
    )
  }

  @Test
  fun `nested committed notifications merge into parent and rollback suppresses notifications`() {
    for ((label, committed) in mapOf("commit" to true, "rollback" to false)) {
      val connection = testDbConnection()
      val observer = connection.recordTriggers()
      val outer = connection.newTransaction()
      connection.sendTableTrigger("books")
      val inner = connection.newTransaction()
      connection.sendTableTriggers(arrayOf("authors", "books"))
      inner.markSuccessful()
      inner.end()

      assertWithMessage(label)
        .that(connection.hasActiveTransaction)
        .isTrue()
      assertWithMessage(label)
        .that(observer.triggers)
        .isEmpty()
      if (committed) {
        outer.markSuccessful()
      }
      outer.end()

      assertWithMessage(label)
        .that(observer.triggers)
        .isEqualTo(
          when {
            committed -> listOf(setOf("books", "authors"))
            else -> emptyList()
          }
        )
      assertWithMessage(label)
        .that(connection.hasActiveTransaction)
        .isFalse()
    }
  }

  @Test
  fun `reads of temporary objects and undeclared raw reads use the writer`() {
    data class Outcome(
      val result: Any?,
      val readerOpened: Boolean
    )

    data class Case(
      val label: String,
      val temporarySchema: List<String>,
      val query: (DbConnectionImpl) -> Any?,
      val expected: Outcome
    )

    val books = testTable<Any>(name = "books")
    val bookTitle = testStringKey(
      table = books,
      name = "title"
    )
    val scratch = testTable<Any>(
      name = "scratch",
      temporary = true
    )
    val scratchTitle = testStringKey(
      table = scratch,
      name = "title"
    )
    val scratchSchema = listOf("CREATE TEMP TABLE scratch (title TEXT)", "INSERT INTO scratch VALUES ('Draft')")
    val bookCount = SELECT
      .RAW("SELECT count(*) FROM books")
      .readAs(LONG)
    listOf(
      Case(
        label = "main table",
        temporarySchema = scratchSchema,
        query = { connection ->
          (SELECT COLUMN bookTitle FROM books)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf("Dune"),
          readerOpened = true
        )
      ),
      Case(
        label = "temporary table",
        temporarySchema = scratchSchema,
        query = { connection ->
          (SELECT COLUMN scratchTitle FROM scratch)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf("Draft"),
          readerOpened = false
        )
      ),
      Case(
        label = "temporary table outside the generated schema",
        temporarySchema = emptyList(),
        query = { connection ->
          connection.write { it.connection.execSQL(scratchSchema.first()) }
          (SELECT COLUMN scratchTitle FROM scratch)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = emptyList<String>(),
          readerOpened = false
        )
      ),
      Case(
        label = "undeclared raw query with temporary objects",
        temporarySchema = scratchSchema,
        query = { connection ->
          bookCount
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf(1L),
          readerOpened = false
        )
      ),
      Case(
        label = "declared raw query with temporary objects",
        temporarySchema = scratchSchema,
        query = { connection ->
          bookCount
            .observes(books)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf(1L),
          readerOpened = true
        )
      ),
      Case(
        label = "undeclared raw query without temporary objects",
        temporarySchema = emptyList(),
        query = { connection ->
          bookCount
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf(1L),
          readerOpened = false
        )
      ),
      Case(
        label = "undeclared raw query returning deleted rows",
        temporarySchema = emptyList(),
        query = { connection ->
          SELECT
            .RAW("DELETE FROM books RETURNING title")
            .readAs(STRING)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf("Dune"),
          readerOpened = false
        )
      ),
      Case(
        label = "undeclared raw query of the last inserted row id",
        temporarySchema = emptyList(),
        query = { connection ->
          connection.write { it.connection.execSQL("INSERT INTO books VALUES ('Emma')") }
          SELECT
            .RAW("SELECT last_insert_rowid()")
            .readAs(LONG)
            .usingConnection(connection)
            .execute()
        },
        expected = Outcome(
          result = listOf(2L),
          readerOpened = false
        )
      )
    ).forEachIndexed { index, case ->
      val driver = TrackingDriver()
      val connection = testDbConnection(
        database = TestGeneratedDatabase(
          tableCount = 1,
          schema = listOf("CREATE TABLE books (title TEXT)", "INSERT INTO books VALUES ('Dune')"),
          temporarySchema = case.temporarySchema
        ),
        driver = driver,
        fileName = folder.databasePath("routing-$index.db")
      ).also(connections::add)

      assertWithMessage(case.label)
        .that(
          Outcome(
            result = case.query(connection),
            readerOpened = driver.connections.size > 1
          )
        )
        .isEqualTo(case.expected)
    }
  }

  private fun bookConnection(fileName: String = MEMORY_DATABASE) = testDbConnection(
    database = TestGeneratedDatabase(
      tableCount = 1,
      schema = listOf("CREATE TABLE books (title TEXT NOT NULL)")
    ),
    fileName = fileName
  ).also(connections::add)

  private fun DbConnectionImpl.insertBook(title: String) = write {
    it.connection.execSQL("INSERT INTO books VALUES ('$title')")
  }

  private fun DbConnectionImpl.bookTitles() = read(requiresWriter = false) { pooled ->
    pooled.connection.query("SELECT title FROM books ORDER BY rowid") { it.getText(0) }
  }
}
