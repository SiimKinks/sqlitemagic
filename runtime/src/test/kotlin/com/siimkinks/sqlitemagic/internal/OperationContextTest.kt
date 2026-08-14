package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class OperationContextTest {
  @Test
  fun `transaction context maps operation results to transaction state`() {
    val connection = RecordingConnection()
    val context = context(connection = connection)

    assertThat(context.executeInTransaction { EntityInsertResult.Inserted(rowId = 7L) })
      .isEqualTo(EntityInsertResult.Inserted(rowId = 7L))
    assertThat(connection.transaction.markSuccessfulCalls).isEqualTo(1)
    assertThat(connection.transaction.endCalls).isEqualTo(1)

    context.executeInTransaction { EntityPersistResult.Ignored }
    assertThat(connection.transaction.markSuccessfulCalls).isEqualTo(1)
    assertThat(connection.transaction.endCalls).isEqualTo(2)

    context.executeInTransaction { -1L }
    assertThat(connection.transaction.markSuccessfulCalls).isEqualTo(1)
    assertThat(connection.transaction.endCalls).isEqualTo(3)

    context.executeInTransaction { 0 }
    assertThat(connection.transaction.markSuccessfulCalls).isEqualTo(2)
    assertThat(connection.transaction.endCalls).isEqualTo(4)

    context(
      connection = connection,
      conflictAlgorithm = CONFLICT_IGNORE
    ).executeInTransaction { false }
    assertThat(connection.transaction.markSuccessfulCalls).isEqualTo(2)
    assertThat(connection.transaction.endCalls).isEqualTo(5)
  }

  @Test
  fun `child context shares execution state and copies immutable operation configuration`() {
    val connection = RecordingConnection()
    val context = context(connection = connection)
    context.bindValues.put("name", "book")
    val helper = context.variableArgsOperationHelper
    context.generatedRelationshipIds["child"] = 7L
    val child = context.child(
      skipTableTriggers = true,
      ignoreNullValues = true
    )

    assertThat(child.bindValues).isSameInstanceAs(context.bindValues)
    assertThat(child.variableArgsOperationHelper).isSameInstanceAs(helper)
    assertThat(child.connection).isSameInstanceAs(context.connection)
    assertThat(child.conflictAlgorithm).isEqualTo(context.conflictAlgorithm)
    assertThat(child.skipTableTriggers).isTrue()
    assertThat(child.ignoreNullValues).isTrue()
    assertThat(child.generatedRelationshipIds).isNotSameInstanceAs(context.generatedRelationshipIds)
    assertThat(child.generatedRelationshipIds).isEmpty()

    child.generatedRelationshipIds["child"] = 9L
    assertThat(context.generatedRelationshipIds).containsExactly("child", 7L)
    assertThat(context.skipTableTriggers).isFalse()
    assertThat(context.ignoreNullValues).isFalse()
  }

  @Test
  fun `top-level execution contexts isolate mutable scratch state`() {
    val connection = RecordingConnection()
    val first = context(connection = connection)
    val second = context(connection = connection)
    first.bindValues.put("name", "first")
    first.generatedRelationshipIds["child"] = 7L
    val firstHelper = first.variableArgsOperationHelper
    val secondHelper = second.variableArgsOperationHelper

    assertThat(second.bindValues).isNotSameInstanceAs(first.bindValues)
    assertThat(second.bindValues.isEmpty).isTrue()
    assertThat(second.generatedRelationshipIds).isNotSameInstanceAs(first.generatedRelationshipIds)
    assertThat(second.generatedRelationshipIds).isEmpty()
    assertThat(secondHelper).isNotSameInstanceAs(firstHelper)
  }

  @Test
  fun `adapter child context rebases metadata and keeps trigger suppression local`() {
    val connection = RecordingConnection()
    val context = context(
      connection = connection,
      tableName = "parent",
      tablePosition = 2,
      ignoreNullValues = true
    )
    context.bindValues.put("name", "parent")
    val helper = context.variableArgsOperationHelper
    context.generatedRelationshipIds["parent"] = 7L
    val child = context.childFor(
      adapter = metadata(
        tableName = "child",
        tablePosition = 1
      ),
      skipTableTriggers = true,
      ignoreNullValues = false
    )

    assertThat(child.tableName).isEqualTo("child")
    assertThat(child.moduleName).isNull()
    assertThat(child.tablePosition).isEqualTo(1)
    assertThat(child.bindValues).isSameInstanceAs(context.bindValues)
    assertThat(child.variableArgsOperationHelper).isSameInstanceAs(helper)
    assertThat(child.connection).isSameInstanceAs(context.connection)
    assertThat(child.conflictAlgorithm).isEqualTo(context.conflictAlgorithm)
    assertThat(child.skipTableTriggers).isTrue()
    assertThat(child.ignoreNullValues).isFalse()
    assertThat(child.generatedRelationshipIds).isNotSameInstanceAs(context.generatedRelationshipIds)
    assertThat(child.generatedRelationshipIds).isEmpty()

    val sameTable = context.childWithoutTableTriggers()
    assertThat(sameTable.tableName).isEqualTo(context.tableName)
    assertThat(sameTable.moduleName).isEqualTo(context.moduleName)
    assertThat(sameTable.tablePosition).isEqualTo(context.tablePosition)
    assertThat(sameTable.skipTableTriggers).isTrue()
    assertThat(sameTable.ignoreNullValues).isEqualTo(context.ignoreNullValues)
  }

  @Test
  fun `adapter module identity selects the corresponding entity manager cache`() {
    val database = RecordingDatabase()
    val generatedDatabase = TestGeneratedDatabase(
      tableCount = 1,
      submoduleTableCounts = mapOf("Feature" to 1)
    )
    val connection = DbConnectionImpl(
      generatedDatabase,
      RecordingOpenHelper(database),
      Schedulers.trampoline()
    )
    val mainContext = context(
      connection = connection,
      moduleName = null
    )
    val featureContext = context(
      connection = connection,
      moduleName = "Feature"
    )

    assertThat(mainContext.entityDbManager()).isNotSameInstanceAs(featureContext.entityDbManager())
    assertThat(featureContext.child().entityDbManager()).isSameInstanceAs(featureContext.entityDbManager())
    assertThat(
      mainContext
        .childFor(
          adapter = metadata(
            moduleName = "Feature",
            tableName = "feature_items",
            tablePosition = 0
          )
        )
        .entityDbManager()
    ).isSameInstanceAs(featureContext.entityDbManager())
  }

  @Test
  fun `operation logging matches the APT event contract`() {
    data class LoggingCase(
      val label: String,
      val runtimeLoggingEnabled: Boolean,
      val expectedMessages: List<String>
    )

    val cases = listOf(
      LoggingCase(
        label = "runtime logging disabled",
        runtimeLoggingEnabled = false,
        expectedMessages = emptyList()
      ),
      LoggingCase(
        label = "runtime logging enabled",
        runtimeLoggingEnabled = true,
        expectedMessages = listOf(
          "INSERT\n  table: books\n  object: book",
          "INSERT id: 7",
          "UPDATE\n  table: books\n  object: book",
          "UPDATE rows affected: 2",
          "PERSIST\n  table: books\n  object: book",
          "PERSIST update failed; trying insertion",
          "PERSIST insert id: 8"
        )
      )
    )

    try {
      cases.forEach { case ->
        val logger = RecordingLogger()
        SqliteMagic.setLogger(logger)
        SqliteMagic.setLoggingEnabled(case.runtimeLoggingEnabled)

        context(connection = RecordingConnection()).apply {
          logInsert(entity = "book")
          logInsertId(rowId = 7L)
          logUpdate(entity = "book")
          logUpdateRowsAffected(rowsAffected = 2)
          logPersist(entity = "book")
          logPersistUpdateFailed()
          logPersistInsertId(rowId = 8L)
        }

        assertWithMessage(case.label)
          .that(logger.debugMessages)
          .containsExactlyElementsIn(case.expectedMessages)
          .inOrder()
      }
    } finally {
      SqliteMagic.setLoggingEnabled(false)
    }
  }

  @Test
  fun `operation errors follow the runtime logging flag`() {
    val logger = RecordingLogger()
    val exception = IllegalStateException("boom")
    SqliteMagic.setLogger(logger)
    SqliteMagic.setLoggingEnabled(true)

    try {
      context(connection = RecordingConnection()).logError(
        exception = exception,
        message = "Operation failed"
      )

      assertThat(logger.errorMessages).containsExactly("Operation failed")
      assertThat(logger.errorThrowables).containsExactly(exception)

      SqliteMagic.setLoggingEnabled(false)
      context(connection = RecordingConnection()).logError(
        exception = exception,
        message = "Disabled"
      )
      assertThat(logger.errorMessages).containsExactly("Operation failed")
    } finally {
      SqliteMagic.setLoggingEnabled(false)
    }
  }

  @Test
  fun `transaction failures log the APT transaction error`() {
    val logger = RecordingLogger()
    val exception = IllegalStateException("boom")
    SqliteMagic.setLogger(logger)
    SqliteMagic.setLoggingEnabled(true)

    try {
      val actual = try {
        context(connection = RecordingConnection()).executeInTransaction {
          throw exception
        }
      } catch (actual: IllegalStateException) {
        actual
      }

      assertThat(actual).isSameInstanceAs(exception)
      assertThat(logger.errorMessages)
        .containsExactly("Error while executing db transaction")
      assertThat(logger.errorThrowables).containsExactly(exception)
    } finally {
      SqliteMagic.setLoggingEnabled(false)
    }
  }

  private fun context(
    connection: DbConnection,
    moduleName: String? = null,
    tableName: String = "books",
    tablePosition: Int = 0,
    conflictAlgorithm: Int = CONFLICT_NONE,
    ignoreNullValues: Boolean = false
  ) = OperationContext(
    adapter = metadata(
      moduleName = moduleName,
      tableName = tableName,
      tablePosition = tablePosition
    ),
    configuration = OperationConfigurationSnapshot(
      connection = connection,
      conflictAlgorithm = conflictAlgorithm,
      ignoreNullValues = ignoreNullValues
    )
  )

  private fun metadata(
    moduleName: String? = null,
    tableName: String,
    tablePosition: Int
  ) = object : EntityAdapterMetadata {
    override val moduleName = moduleName
    override val tableName = tableName
    override val insertSql = "INSERT%s INTO $tableName"
    override val tablePosition = tablePosition
    override val withoutRowId = false
    override val maxColumns = 1
  }

  private class RecordingConnection : DbConnection {
    val transaction = RecordingTransaction()

    override fun newTransaction(): Transaction = transaction

    override fun clearData() = Unit

    override fun close() = Unit
  }

  private class RecordingTransaction : Transaction {
    var markSuccessfulCalls = 0
    var endCalls = 0

    override fun markSuccessful() {
      markSuccessfulCalls++
    }

    override fun end() {
      endCalls++
    }

    override fun yieldIfContendedSafely() = false

    override fun yieldIfContendedSafely(
      sleepAmount: Long,
      sleepUnit: TimeUnit
    ) = false

    override fun close() = end()
  }

  private class RecordingLogger : Logger {
    val debugMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()
    val errorThrowables = mutableListOf<Throwable>()

    override fun logDebug(message: String) {
      debugMessages += message
    }

    override fun logWarning(message: String) = Unit

    override fun logError(message: String) {
      errorMessages += message
    }

    override fun logError(message: String, throwable: Throwable) {
      errorMessages += message
      errorThrowables += throwable
    }

    override fun logQueryTime(
      queryTimeInMillis: Long,
      observedTables: Array<String>,
      sql: String,
      args: Array<String>?
    ) = Unit
  }
}
