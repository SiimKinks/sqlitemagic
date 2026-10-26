package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import com.siimkinks.sqlitemagic.internal.identity
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test

internal class OperationContextTest {
  @Test
  fun `transaction context maps operation results to transaction state`() {
    val connection = newConnection()
    val context = context(connection = connection.connection)
    val ignoring = context(
      connection = connection.connection,
      conflictAlgorithm = CONFLICT_IGNORE
    )

    assertThat(
      listOf(
        connection.transactionResult(context.executeInTransaction { EntityInsertResult.Inserted(rowId = 7L) }),
        connection.transactionResult(context.executeInTransaction { EntityPersistResult.Ignored }),
        connection.transactionResult(context.executeInTransaction { 0 }),
        connection.transactionResult(ignoring.executeInTransaction { false })
      )
    ).isEqualTo(
      listOf(
        TransactionResult(
          result = EntityInsertResult.Inserted(rowId = 7L),
          committed = 1,
          rolledBack = 0
        ),
        TransactionResult(
          result = EntityPersistResult.Ignored,
          committed = 1,
          rolledBack = 1
        ),
        TransactionResult(
          result = 0,
          committed = 2,
          rolledBack = 1
        ),
        TransactionResult(
          result = false,
          committed = 2,
          rolledBack = 2
        )
      )
    )
  }

  @Test
  fun `a context without table triggers shares execution state and configuration`() {
    val context = context(connection = newConnection().connection)
    context.bindValues["name"] = "book"
    context.rememberGeneratedId(
      columnName = "child",
      rowId = 7L
    )
    val child = context.childWithoutTableTriggers()

    assertThat(child.bindValues).isSameInstanceAs(context.bindValues)
    assertThat(child.configuration).isSameInstanceAs(context.configuration)
    assertThat(child.skipTableTriggers).isTrue()
    assertThat(child.generatedRelationshipIds).isEmpty()

    child.rememberGeneratedId(
      columnName = "child",
      rowId = 9L
    )
    assertThat(context.generatedRelationshipIds).containsExactly("child", 7L)
    assertThat(context.skipTableTriggers).isFalse()
  }

  @Test
  fun `adapter child context rebases metadata and keeps trigger suppression local`() {
    val context = context(
      connection = newConnection().connection,
      tableName = "parent",
      tablePosition = 2,
      ignoreNullValues = true
    )
    context.bindValues["name"] = "parent"
    context.rememberGeneratedId(
      columnName = "parent",
      rowId = 7L
    )
    val childAdapter = metadata(
      tableName = "child",
      tablePosition = 1
    )
    val child = context.relationshipContext(
      adapter = childAdapter,
      ignoreNullValues = false
    )

    assertThat(child.adapter).isSameInstanceAs(childAdapter)
    assertThat(child.bindValues).isSameInstanceAs(context.bindValues)
    assertThat(child.connection).isSameInstanceAs(context.connection)
    assertThat(child.conflictAlgorithm).isEqualTo(context.conflictAlgorithm)
    assertThat(child.skipTableTriggers).isTrue()
    assertThat(child.ignoreNullValues).isFalse()
    assertThat(child.generatedRelationshipIds).isEmpty()
  }

  @Test
  fun `adapter module identity selects the corresponding entity manager cache`() {
    val database = RecordingDatabase()
    val generatedDatabase = TestGeneratedDatabase(
      tableCount = 1,
      submoduleTableCounts = mapOf("Feature" to 1)
    )
    val connection = DbConnectionImpl(
      database = generatedDatabase,
      dbHelper = RecordingOpenHelper(database),
      queryScheduler = Schedulers.trampoline()
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
    assertThat(featureContext.childWithoutTableTriggers().entityDbManager())
      .isSameInstanceAs(featureContext.entityDbManager())
    assertThat(
      mainContext
        .relationshipContext(
          adapter = metadata(
            moduleName = "Feature",
            tableName = "feature_items",
            tablePosition = 0
          ),
          ignoreNullValues = false
        )
        .entityDbManager()
    ).isSameInstanceAs(featureContext.entityDbManager())
  }

  @Test
  fun `operation logging is silent while runtime logging is disabled`() {
    withLogger(loggingEnabled = false) { logger ->
      context(connection = newConnection().connection).apply {
        logInsert(entity = "book")
        logInsertId(rowId = 7L)
        logUpdate(entity = "book")
        logUpdateRowsAffected(rowsAffected = 2)
        logPersist(entity = "book")
        logPersistUpdateFailed()
        logPersistInsertId(rowId = 8L)
      }

      assertThat(logger.events).isEmpty()
    }
  }

  @Test
  fun `transaction failures propagate for their operation to log`() {
    withLogger(loggingEnabled = true) { logger ->
      val exception = IllegalStateException("boom")

      val actual = assertThrows(IllegalStateException::class.java) {
        context(connection = newConnection().connection).executeInTransaction {
          throw exception
        }
      }

      assertThat(actual).isSameInstanceAs(exception)
      assertThat(logger.events.filter { it.level == LogLevel.ERROR }).isEmpty()
    }
  }

  @Test
  fun `default statements are the ones the connection caches`() {
    for (conflictAlgorithm in listOf(CONFLICT_NONE, CONFLICT_ABORT)) {
      val connection = newConnection()
      val context = context(
        connection = connection.connection,
        conflictAlgorithm = conflictAlgorithm
      )
      val statements = List(2) {
        listOf(
          context.withInsertStatement(
            sql = INSERT_SQL,
            block = ::identity
          ),
          context.withUpdateStatement(
            sql = UPDATE_SQL,
            default = true,
            block = ::identity
          )
        )
      }

      assertWithMessage("conflict algorithm $conflictAlgorithm")
        .that(statements[1])
        .isEqualTo(statements[0])
      assertThat(connection.statementSql)
        .containsExactly("INSERT INTO books(name) VALUES (?)", "UPDATE books SET name=? WHERE id=?")
        .inOrder()
      assertThat(connection.statementCloseCalls)
        .containsExactly(0, 0)
    }
  }

  @Test
  fun `other statements outside a transaction are compiled for one call and closed after it`() {
    data class ScopeCase(
      val label: String,
      val conflictAlgorithm: Int,
      val insert: Boolean,
      val defaultIdentity: Boolean = true,
      val expectedSql: String
    )
    listOf(
      ScopeCase(
        label = "ignored insert",
        conflictAlgorithm = CONFLICT_IGNORE,
        insert = true,
        expectedSql = "INSERT OR IGNORE INTO books(name) VALUES (?)"
      ),
      ScopeCase(
        label = "ignored update",
        conflictAlgorithm = CONFLICT_IGNORE,
        insert = false,
        expectedSql = "UPDATE OR IGNORE books SET name=? WHERE id=?"
      ),
      ScopeCase(
        label = "update by another identity column",
        conflictAlgorithm = CONFLICT_NONE,
        insert = false,
        defaultIdentity = false,
        expectedSql = "UPDATE books SET name=? WHERE id=?"
      )
    ).forEach { case ->
      val connection = newConnection()
      val context = context(
        connection = connection.connection,
        conflictAlgorithm = case.conflictAlgorithm
      )
      repeat(2) {
        when {
          case.insert -> context.withInsertStatement(INSERT_SQL) {}
          else -> context.withUpdateStatement(
            sql = UPDATE_SQL,
            default = case.defaultIdentity
          ) {}
        }
      }

      assertWithMessage(case.label)
        .that(connection.statementSql)
        .containsExactly(case.expectedSql, case.expectedSql)
      assertWithMessage(case.label)
        .that(connection.statementCloseCalls)
        .containsExactly(1, 1)
    }
  }

  @Test
  fun `a transaction compiles each other statement once and closes it when it ends`() {
    val connection = newConnection()
    val context = context(
      connection = connection.connection,
      conflictAlgorithm = CONFLICT_IGNORE
    )

    val openDuringTransaction = context.executeInTransaction {
      repeat(2) {
        context.withInsertStatement(INSERT_SQL) {}
      }
      connection.statementCloseCalls
    }

    assertThat(openDuringTransaction).containsExactly(0)
    assertThat(connection.statementSql).containsExactly("INSERT OR IGNORE INTO books(name) VALUES (?)")
    assertThat(connection.statementCloseCalls)
      .containsExactly(1)
  }

  @Test
  fun `a failed close keeps the result of the call`() {
    val connection = newConnection()
    val context = context(
      connection = connection.connection,
      conflictAlgorithm = CONFLICT_IGNORE
    )

    val result = context.withInsertStatement(INSERT_SQL) {
      connection.recordingDatabase.compiledStatements
        .single()
        .closeFailure = IllegalStateException("close failed")
      7
    }

    assertThat(result).isEqualTo(7)
    assertThat(connection.recordingDatabase.compiledStatements.single().closeCalls).isEqualTo(1)
  }

  private fun context(
    connection: DbConnectionImpl,
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
}

private const val INSERT_SQL = "INSERT%s INTO books(name) VALUES (?)"
private const val UPDATE_SQL = "UPDATE%s books SET name=? WHERE id=?"
