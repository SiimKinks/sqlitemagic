package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteTransactionListener
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.StringArraySet
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    val statements = List(
      size = 6,
      init = { mock<SupportSQLiteStatement>() }
    )
    val remainingStatements = statements
      .drop(1)
      .toTypedArray()
    whenever(fixture.db.compileStatement(any()))
      .thenReturn(statements.first(), *remainingStatements)
    for (manager in managers) {
      manager.insertStatement("INSERT%s INTO books VALUES (?)")
      manager.updateStatement("UPDATE%s books SET name=?")
    }
    val observer = fixture.connection.triggers
      .map(::snapshotTables)
      .test()
      .assertEmpty()

    mockStatic(Log::class.java)
      .use {
        fixture.connection.close()
        fixture.connection.close()
      }
    fixture.connection.sendTableTrigger("books")

    observer.assertResult()
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
  fun `requesting submodule manager without configured submodules fails clearly`() {
    val fixture = ConnectionFixture()

    val failure = assertThrows(IllegalStateException::class.java) {
      fixture.connection.getEntityDbManager(
        moduleName = "feature",
        tablePos = 0
      )
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("No submodules configured")
  }

  @Test
  fun `clear data forwards writable database and only publishes returned table names`() {
    for ((label, clearedTables) in mapOf(
      "no triggers" to null,
      "cleared tables" to StringArraySet(arrayOf("books", "authors"))
    )) {
      val databases = mutableListOf<SupportSQLiteDatabase>()
      val generated = object : GeneratedDatabase by TestGeneratedDatabase(tableCount = 0) {
        override fun clearData(db: SupportSQLiteDatabase): StringArraySet? {
          databases += db
          return clearedTables
        }
      }
      val fixture = ConnectionFixture(generated)
      val observer = fixture.connection.triggers
        .map(::snapshotTables)
        .test()
        .assertEmpty()

      fixture.connection.clearData()

      assertWithMessage(label)
        .that(databases)
        .containsExactly(fixture.db)
      when (clearedTables) {
        null -> observer.assertEmpty()
        else -> observer.assertValuesOnly(setOf("books", "authors"))
      }
    }
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
      val failure = assertThrows(IllegalStateException::class.java) { end() }
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
        sleepUnit = TimeUnit.SECONDS
      )
    ).isTrue()
    assertThat(
      transaction.yieldIfContendedSafely(
        sleepAmount = 500L,
        sleepUnit = TimeUnit.MICROSECONDS
      )
    ).isFalse()
    transaction.markSuccessful()

    verify(fixture.db).yieldIfContendedSafely()
    verify(fixture.db).yieldIfContendedSafely(2_000L)
    verify(fixture.db).yieldIfContendedSafely(0L)
    verify(fixture.db).setTransactionSuccessful()
    transaction.end()
  }

  @Test
  fun `subscription guard observes only transactions on the subscribing thread`() {
    val fixture = ConnectionFixture()
    fixture.connection.ensureNotInTransaction.accept(Unit)
    val transaction = fixture.connection.newTransaction()

    val failure = assertThrows(IllegalStateException::class.java) {
      fixture.connection.ensureNotInTransaction.accept(Unit)
    }
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Cannot subscribe to observable query in a transaction.")
    val executor = Executors.newSingleThreadExecutor()
    try {
      val otherThreadActive = executor
        .submit<Boolean> {
          fixture.connection.ensureNotInTransaction.accept(Unit)
          fixture.connection.hasActiveTransaction
        }
        .get(5L, TimeUnit.SECONDS)
      assertThat(otherThreadActive).isFalse()
    } finally {
      executor.shutdownNow()
    }
    transaction.end()
    fixture.connection.ensureNotInTransaction.accept(Unit)
  }

  @Test
  fun `committed notifications follow database end and restore transaction state first`() {
    val fixture = ConnectionFixture()
    val events = mutableListOf<String>()
    fixture.connection.triggers
      .map(::snapshotTables)
      .subscribe { tables ->
        assertThat(fixture.connection.hasActiveTransaction).isFalse()
        assertThat(tables).containsExactly("books", "authors")
        events += "notify"
      }
    val transaction = fixture.connection.newTransaction()
    fixture.connection.sendTableTrigger("books")
    fixture.connection.sendTableTriggers("authors", "books")
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
      val observer = fixture.connection.triggers
        .map(::snapshotTables)
        .test()
      val outer = fixture.connection.newTransaction()
      fixture.connection.sendTableTrigger("books")
      val inner = fixture.connection.newTransaction()
      fixture.connection.sendTableTriggers("authors", "books")
      fixture.listeners
        .last()
        .onCommit()
      inner.end()

      assertWithMessage(label)
        .that(fixture.connection.hasActiveTransaction)
        .isTrue()
      observer.assertEmpty()
      if (committed) {
        fixture.listeners
          .first()
          .onCommit()
      }
      outer.end()

      when {
        committed -> observer.assertValuesOnly(setOf("books", "authors"))
        else -> observer.assertEmpty()
      }
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
  val helper = mock<SupportSQLiteOpenHelper>()
  val listeners = mutableListOf<SQLiteTransactionListener>()
  val connection: DbConnectionImpl

  init {
    whenever(helper.writableDatabase)
      .thenReturn(db)
    whenever(helper.readableDatabase)
      .thenReturn(db)
    whenever(helper.databaseName)
      .thenReturn("test.db")
    doAnswer { invocation ->
      listeners += invocation.getArgument<SQLiteTransactionListener>(0)
      null
    }
      .whenever(db)
      .beginTransactionWithListener(any())
    connection = DbConnectionImpl(
      database = generatedDatabase,
      dbHelper = helper,
      queryScheduler = Schedulers.trampoline()
    )
  }
}

private fun snapshotTables(tables: Set<String>) = when (tables) {
  is StringArraySet -> tables.indices
    .map(tables::valueAt)
    .toSet()
  else -> tables.toSet()
}
