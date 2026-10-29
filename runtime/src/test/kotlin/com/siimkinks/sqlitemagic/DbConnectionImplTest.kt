package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteTransactionListener
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.SECONDS

internal class DbConnectionImplTest {
  @Test
  fun `close completes observers and releases all cached managers exactly once`() {
    val fixture = ConnectionFixture(
      generatedDatabase = TestGeneratedDatabase(
        tableCount = 2,
        submoduleTableCounts = mapOf("feature" to 1)
      )
    )
    val managers = listOf(
      fixture.connection.getEntityDbManager(
        moduleName = null,
        tablePos = 0
      ),
      fixture.connection.getEntityDbManager(
        moduleName = null,
        tablePos = 1
      ),
      fixture.connection.getEntityDbManager(
        moduleName = "feature",
        tablePos = 0
      )
    )
    val statements = List(6) { mock<SupportSQLiteStatement>() }
    whenever(fixture.db.compileStatement(any())).doReturnConsecutively(statements)
    for (manager in managers) {
      manager.insertStatement("INSERT%s INTO books VALUES (?)")
      manager.updateStatement("UPDATE%s books SET name=?")
    }
    val observer = fixture.connection.recordTriggers()

    mockStatic(Log::class.java)
      .use {
        fixture.connection.close()
        fixture.connection.close()
      }
    fixture.connection.sendTableTrigger("books")

    assertThat(observer.triggers).isEmpty()
    assertThat(observer.closeCalls).isEqualTo(1)
    verify(fixture.helper, times(1)).close()
    statements.forEach { verify(it, times(1)).close() }
    for (manager in managers) {
      val failure = assertThrows(IllegalStateException::class.java) {
        manager.compileStatement("SELECT 1")
      }
      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("DB connection closed")
    }
    for ((label, moduleName) in mapOf("main" to null, "feature" to "feature")) {
      val failure = assertThrows(IllegalStateException::class.java) {
        fixture.connection.getEntityDbManager(
          moduleName = moduleName,
          tablePos = 0
        )
      }
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("DB connection closed")
    }
  }

  @Test
  fun `manager routing keeps main and submodule caches distinct and stable`() {
    val fixture = ConnectionFixture(
      generatedDatabase = TestGeneratedDatabase(
        tableCount = 1,
        submoduleTableCounts = mapOf("feature" to 1)
      )
    )
    val main = fixture.connection.getEntityDbManager(
      moduleName = null,
      tablePos = 0
    )
    val feature = fixture.connection.getEntityDbManager(
      moduleName = "feature",
      tablePos = 0
    )

    assertThat(main).isNotSameInstanceAs(feature)
    for ((moduleName, expected) in mapOf(null to main, "feature" to feature)) {
      assertWithMessage(moduleName ?: "main")
        .that(
          fixture.connection.getEntityDbManager(
            moduleName = moduleName,
            tablePos = 0
          )
        )
        .isSameInstanceAs(expected)
    }
  }

  @Test
  fun `requesting the manager of a submodule the database lacks fails clearly`() {
    val fixture = ConnectionFixture()

    val failure = assertThrows(IllegalStateException::class.java) {
      fixture.connection.getEntityDbManager(
        moduleName = "feature",
        tablePos = 0
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Submodule feature is not part of this database")
  }

  @Test
  fun `clear data forwards writable database and only publishes returned table names`() {
    for ((label, clearedTables) in mapOf(
      "no triggers" to MutableScatterSet(),
      "cleared tables" to MutableScatterSet<String>().apply { addAll(arrayOf("books", "authors")) }
    )) {
      val databases = mutableListOf<SupportSQLiteDatabase>()
      val generated = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
        override fun clearData(db: SupportSQLiteDatabase): MutableScatterSet<String> {
          databases += db
          return clearedTables
        }
      }
      val fixture = ConnectionFixture(generated)
      val observer = fixture.connection.recordTriggers()

      fixture.connection.clearData()

      assertWithMessage(label)
        .that(databases)
        .containsExactly(fixture.db)
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
  fun `multiple table triggers outside transactions publish distinct names immediately`() {
    val fixture = ConnectionFixture()
    val observer = fixture.connection.recordTriggers()

    fixture.connection.sendTableTriggers(arrayOf("books", "authors", "books"))

    assertThat(observer.triggers).containsExactly(setOf("books", "authors"))
  }

  @Test
  fun `published transaction table storage remains an independent snapshot`() {
    val fixture = ConnectionFixture()
    val published = mutableListOf<Set<String>>()
    fixture.connection.tableObservers.add(
      tables = null,
      observer = object : TableChangeObserver {
        override fun onTablesChanged(tables: Set<String>) {
          published += tables
        }

        override fun onClosed() = Unit
      }
    )
    val first = fixture.connection.newTransaction()
    fixture.connection.sendTableTrigger("books")
    fixture.listeners.last().onCommit()
    first.end()
    val firstPublished = published.single()

    val second = fixture.connection.newTransaction()
    fixture.connection.sendTableTriggers(arrayOf("authors", "books"))
    fixture.listeners.last().onCommit()
    second.end()

    assertThat(firstPublished).containsExactly("books")
    assertThat(published.last()).containsExactly("authors", "books")
  }

  @Test
  fun `transaction handles are shared and end and close reject an absent transaction`() {
    val fixture = ConnectionFixture()
    val transaction = fixture.connection.newTransaction()
    val nested = fixture.connection.newTransaction()

    assertThat(nested).isSameInstanceAs(transaction)
    nested.close()
    assertThat(fixture.connection.hasActiveTransaction).isTrue()
    transaction.end()
    assertThat(fixture.connection.hasActiveTransaction).isFalse()
    verify(fixture.db, times(2)).endTransaction()

    val endings = mapOf(
      "end" to transaction::end,
      "close" to transaction::close
    )
    for ((label, end) in endings) {
      val failure = assertThrows(IllegalStateException::class.java, end)
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("Not in transaction.")
    }
    verify(fixture.db, times(2)).endTransaction()
  }

  @Test
  fun `transaction yield overloads forward result and convert sleep duration to milliseconds`() {
    val fixture = ConnectionFixture()
    whenever(fixture.db.yieldIfContendedSafely())
      .thenReturn(true)
    whenever(fixture.db.yieldIfContendedSafely(2_000L))
      .thenReturn(true)
    val transaction = fixture.connection.newTransaction()

    assertThat(transaction.yieldIfContendedSafely()).isTrue()
    assertThat(
      transaction.yieldIfContendedSafely(
        sleepAmount = 2L,
        sleepUnit = SECONDS
      )
    ).isTrue()
    assertThat(
      transaction.yieldIfContendedSafely(
        sleepAmount = 500L,
        sleepUnit = MICROSECONDS
      )
    ).isFalse()

    verify(fixture.db).yieldIfContendedSafely()
    verify(fixture.db).yieldIfContendedSafely(2_000L)
    verify(fixture.db).yieldIfContendedSafely(0L)
    transaction.end()
  }

  @Test
  fun `subscription guard observes only transactions on the subscribing thread`() {
    val fixture = ConnectionFixture()
    fixture.connection.ensureNotInTransaction()
    val transaction = fixture.connection.newTransaction()

    val failure = assertThrows(IllegalStateException::class.java) {
      fixture.connection.ensureNotInTransaction()
    }
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Cannot subscribe to observable query in a transaction.")
    val executor = Executors.newSingleThreadExecutor()
    try {
      val otherThreadActive = executor
        .submit<Boolean> {
          fixture.connection.ensureNotInTransaction()
          fixture.connection.hasActiveTransaction
        }
        .get(5L, SECONDS)
      assertThat(otherThreadActive).isFalse()
    } finally {
      executor.shutdownNow()
    }
    transaction.end()
    fixture.connection.ensureNotInTransaction()
  }

  @Test
  fun `committed notifications follow database end and restore transaction state first`() {
    val fixture = ConnectionFixture()
    val events = mutableListOf<String>()
    fixture.connection.tableObservers.add(
      tables = null,
      observer = object : TableChangeObserver {
        override fun onTablesChanged(tables: Set<String>) {
          assertThat(fixture.connection.hasActiveTransaction).isFalse()
          assertThat(tables).containsExactly("books", "authors")
          events += "notify"
        }

        override fun onClosed() = Unit
      }
    )
    val transaction = fixture.connection.newTransaction()
    fixture.connection.sendTableTrigger("books")
    fixture.connection.sendTableTriggers(arrayOf("authors", "books"))
    doAnswer {
      assertThat(fixture.connection.hasActiveTransaction).isFalse()
      fixture.listeners.single()
        .onCommit()
      events += "database end"
      null
    }
      .whenever(fixture.db)
      .endTransaction()

    assertThat(events).isEmpty()
    transaction.end()

    assertThat(events)
      .containsExactly("database end", "notify")
      .inOrder()
  }

  @Test
  fun `nested committed notifications merge into parent and rollback suppresses notifications`() {
    for ((label, committed) in mapOf("commit" to true, "rollback" to false)) {
      val fixture = ConnectionFixture()
      val observer = fixture.connection.recordTriggers()
      val outer = fixture.connection.newTransaction()
      fixture.connection.sendTableTrigger("books")
      val inner = fixture.connection.newTransaction()
      fixture.connection.sendTableTriggers(arrayOf("authors", "books"))
      fixture.listeners
        .last()
        .onCommit()
      inner.end()

      assertWithMessage(label)
        .that(fixture.connection.hasActiveTransaction)
        .isTrue()
      assertWithMessage(label)
        .that(observer.triggers)
        .isEmpty()
      if (committed) {
        fixture.listeners
          .first()
          .onCommit()
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
        .that(fixture.connection.hasActiveTransaction)
        .isFalse()
    }
  }

  @Test
  fun `database access and direct compilation forward to the configured helper`() {
    val fixture = ConnectionFixture()
    val readable = mock<SupportSQLiteDatabase>()
    val statement = mock<SupportSQLiteStatement>()
    whenever(fixture.helper.readableDatabase)
      .thenReturn(readable)
    whenever(fixture.db.compileStatement("SELECT 1"))
      .thenReturn(statement)

    assertThat(fixture.connection.readableDatabase).isSameInstanceAs(readable)
    assertThat(fixture.connection.writableDatabase).isSameInstanceAs(fixture.db)
    assertThat(fixture.connection.compileStatement("SELECT 1")).isSameInstanceAs(statement)
    verify(readable, never()).compileStatement(any())
  }
}

private class ConnectionFixture(
  generatedDatabase: GeneratedDatabase = TestGeneratedDatabase(tableCount = 1)
) {
  val db = mock<SupportSQLiteDatabase>()
  val helper = mock<SupportSQLiteOpenHelper> {
    on { writableDatabase } doReturn db
    on { readableDatabase } doReturn db
    on { databaseName } doReturn "test.db"
  }
  val listeners: List<SQLiteTransactionListener>
    field = mutableListOf()

  init {
    doAnswer { invocation ->
      listeners += invocation.getArgument<SQLiteTransactionListener>(0)
      null
    }
      .whenever(db)
      .beginTransactionWithListener(any())
  }

  val connection = DbConnectionImpl(
    database = generatedDatabase,
    dbHelper = helper,
    queryScheduler = Schedulers.trampoline()
  )
}
