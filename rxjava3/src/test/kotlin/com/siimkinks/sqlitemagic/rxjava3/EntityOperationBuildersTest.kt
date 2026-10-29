package com.siimkinks.sqlitemagic.rxjava3

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.TestSchema
import com.siimkinks.sqlitemagic.Unique
import com.siimkinks.sqlitemagic.bulkInsert
import com.siimkinks.sqlitemagic.bulkPersistByColumn
import com.siimkinks.sqlitemagic.bulkUpdateByColumn
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.testDbConnection
import com.siimkinks.sqlitemagic.update
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class EntityOperationBuildersTest {
  @Test
  fun `observed terminal snapshots connection conflict and selected identity`() {
    val initialConnection = newConnection()
    initialConnection.recordingDatabase.insertResults += -1L
    val laterConnection = newConnection()
    laterConnection.recordingDatabase.insertResults += 7L
    val insertBuilder = TestAdapter()
      .insert(TestEntity())
      .usingConnection(initialConnection.connection)
    val insert = insertBuilder.observe()

    insertBuilder
      .usingConnection(laterConnection.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)

    insert
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertThat(initialConnection.recordingDatabase.compiledStatements).hasSize(1)
    assertThat(laterConnection.recordingDatabase.compiledStatements).isEmpty()
    insertBuilder
      .observe()
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 7L))

    val updateConnection = newConnection()
    updateConnection.recordingDatabase.updateResults.addAll(listOf(1, 1))
    val updateBuilder = TestAdapter()
      .update(TestEntity())
      .usingConnection(updateConnection.connection)
      .byColumn(TestSchema.id)
    val update = updateBuilder.observe()

    updateBuilder.byColumn(TestSchema.key)
    update
      .test()
      .assertResult()
    updateBuilder
      .observe()
      .test()
      .assertResult()

    assertThat(updateConnection.statementSql)
      .containsExactly(
        "UPDATE books SET name=? WHERE id=?",
        "UPDATE books SET name=? WHERE key=?"
      )
      .inOrder()
  }

  @Test
  fun `observed terminals resolve the default connection on subscription`() {
    val previousConnection = SqliteMagic.defaultConnection
    try {
      SqliteMagic.defaultConnection = null
      val insert = TestAdapter()
        .insert(TestEntity())
        .observe()
      val current = newConnection()
      current.recordingDatabase.insertResults += 7L
      SqliteMagic.defaultConnection = current.connection

      insert
        .test()
        .assertResult(EntityInsertResult.Inserted(rowId = 7L))
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }

  @Test
  fun `observed terminals report failures after disposal to RxJava plugins`() {
    val pluginErrors = mutableListOf<Throwable>()
    RxJavaPlugins.setErrorHandler(pluginErrors::add)
    try {
      for ((label, subscribe) in mapOf<String, (DbConnection, TestObserver<Any>) -> Unit>(
        "insert" to { connection, observer ->
          TestAdapter()
            .insert(TestEntity())
            .usingConnection(connection)
            .observe()
            .subscribe(observer)
        },
        "update" to { connection, observer ->
          TestAdapter()
            .update(TestEntity())
            .usingConnection(connection)
            .observe()
            .subscribe(observer)
        },
        "bulk insert" to { connection, observer ->
          TestAdapter()
            .bulkInsert(listOf(TestEntity()))
            .usingConnection(connection)
            .observe()
            .subscribe(observer)
        }
      )) {
        pluginErrors.clear()
        val observer = TestObserver<Any>()
        val failure = RuntimeException("$label failed after disposal")
        val database = mock<SupportSQLiteDatabase>()
        whenever(database.compileStatement(any())).thenAnswer {
          observer.dispose()
          throw failure
        }

        subscribe(testDbConnection(database), observer)

        observer
          .withTag(label)
          .assertEmpty()
        assertWithMessage(label)
          .that(pluginErrors.map(Throwable::cause))
          .containsExactly(failure)
      }
    } finally {
      RxJavaPlugins.reset()
    }
  }

  @Test
  fun `deferred bulk by-column terminals capture each selected column`() {
    val entity = TestEntity()
    val cases = listOf<Pair<String, (DbConnection) -> (Unique<TestEntity>) -> Completable>>(
      "bulk update" to { connection ->
        val builder = TestAdapter()
          .bulkUpdateByColumn(listOf(entity))
          .usingConnection(connection)
        builder::observe
      },
      "bulk persist" to { connection ->
        val builder = TestAdapter()
          .bulkPersistByColumn(listOf(entity))
          .usingConnection(connection)
        builder::observe
      }
    )

    for ((label, terminals) in cases) {
      val connection = newConnection()
      connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
      val observe = terminals(connection.connection)

      val first = observe(TestSchema.id)
      val second = observe(TestSchema.key)
      second
        .test()
        .assertResult()
      first
        .test()
        .assertResult()

      val statements = connection.recordingDatabase.compiledStatements.map { it.sql to it.bindings[2] }
      assertWithMessage(label)
        .that(statements)
        .containsExactly(
          "UPDATE books SET name=? WHERE key=?" to "key-1",
          "UPDATE books SET name=? WHERE id=?" to "id-1"
        )
        .inOrder()
    }
  }
}
