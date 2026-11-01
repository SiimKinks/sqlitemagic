package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.LogLevel.ERROR
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityAdapterMetadata
import com.siimkinks.sqlitemagic.internal.MutationResults
import org.junit.Assert.assertThrows
import org.junit.Test

internal class OperationContextTest {
  @Test
  fun `transaction context maps operation results to transaction state`() {
    val connection = newConnection()
    val context = context(connection = connection.dbConnection)
    val ignoring = context(
      connection = connection.dbConnection,
      conflictAlgorithm = CONFLICT_IGNORE
    )

    assertThat(
      listOf(
        connection.transactionResult(context.executeInTransaction { EntityInsertResult.Inserted(rowId = 7L) }),
        connection.transactionResult(context.executeInTransaction { EntityPersistResult.Ignored }),
        connection.transactionResult(context.executeInTransaction { 0 }),
        connection.transactionResult(ignoring.executeInTransaction { false })
      )
    )
      .isEqualTo(
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
    val context = context(connection = newConnection().dbConnection)
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
      connection = newConnection().dbConnection,
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
  fun `adapter module identity selects the corresponding entity SQL cache`() {
    val connection = testDbConnection(
      database = TestGeneratedDatabase(
        tableCount = 1,
        submoduleTableCounts = mapOf("Feature" to 1)
      )
    )
    val mainContext = context(
      connection = connection,
      moduleName = null
    )
    val featureContext = context(
      connection = connection,
      moduleName = "Feature"
    )

    assertThat(mainContext.entitySql()).isNotSameInstanceAs(featureContext.entitySql())
    assertThat(
      featureContext
        .childWithoutTableTriggers()
        .entitySql()
    )
      .isSameInstanceAs(featureContext.entitySql())
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
        .entitySql()
    )
      .isSameInstanceAs(featureContext.entitySql())
  }

  @Test
  fun `operation logging is silent while runtime logging is disabled`() {
    withLogger(loggingEnabled = false) { logger ->
      with(context(connection = newConnection().dbConnection)) {
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
        context(connection = newConnection().dbConnection).executeInTransaction {
          throw exception
        }
      }

      assertThat(actual).isSameInstanceAs(exception)
      assertThat(logger.events.filter { it.level == ERROR }).isEmpty()
    }
  }

  @Test
  fun `statements use the default or conflict SQL and are prepared once on the writer`() {
    data class StatementCase(
      val label: String,
      val conflictAlgorithm: Int,
      val insert: Boolean,
      val defaultIdentity: Boolean = true,
      val expectedSql: String
    )

    data class StatementUse(
      val sql: List<String>,
      val sameStatement: Boolean,
      val onWriter: List<Boolean>
    )

    listOf(
      StatementCase(
        label = "insert",
        conflictAlgorithm = CONFLICT_NONE,
        insert = true,
        expectedSql = "INSERT INTO books(name) VALUES (?)"
      ),
      StatementCase(
        label = "aborting insert",
        conflictAlgorithm = CONFLICT_ABORT,
        insert = true,
        expectedSql = "INSERT INTO books(name) VALUES (?)"
      ),
      StatementCase(
        label = "update",
        conflictAlgorithm = CONFLICT_NONE,
        insert = false,
        expectedSql = "UPDATE books SET name=? WHERE id=?"
      ),
      StatementCase(
        label = "aborting update",
        conflictAlgorithm = CONFLICT_ABORT,
        insert = false,
        expectedSql = "UPDATE books SET name=? WHERE id=?"
      ),
      StatementCase(
        label = "ignored insert",
        conflictAlgorithm = CONFLICT_IGNORE,
        insert = true,
        expectedSql = "INSERT OR IGNORE INTO books(name) VALUES (?)"
      ),
      StatementCase(
        label = "ignored update",
        conflictAlgorithm = CONFLICT_IGNORE,
        insert = false,
        expectedSql = "UPDATE OR IGNORE books SET name=? WHERE id=?"
      ),
      StatementCase(
        label = "update by another identity column",
        conflictAlgorithm = CONFLICT_NONE,
        insert = false,
        defaultIdentity = false,
        expectedSql = "UPDATE books SET name=? WHERE id=?"
      )
    )
      .forEach { case ->
        val connection = newConnection()
        val context = context(
          connection = connection.dbConnection,
          conflictAlgorithm = case.conflictAlgorithm
        )
        val onWriter = mutableListOf<Boolean>()
        val statements = List(2) {
          val use = { statement: SQLiteStatement, _: MutationResults ->
            onWriter += connection.dbConnection.pool.holdsWriter()
            statement
          }
          when {
            case.insert -> context.withInsertStatement(
              template = INSERT_SQL,
              block = use
            )
            else -> context.withUpdateStatement(
              template = UPDATE_SQL,
              default = case.defaultIdentity,
              block = use
            )
          }
        }

        assertWithMessage(case.label)
          .that(
            StatementUse(
              sql = connection.statementSql,
              sameStatement = statements[0] === statements[1],
              onWriter = onWriter
            )
          )
          .isEqualTo(
            StatementUse(
              sql = listOf(case.expectedSql),
              sameStatement = true,
              onWriter = listOf(true, true)
            )
          )
      }
  }

  @Test
  fun `entity SQL is built once per table, conflict algorithm and identity column`() {
    val connection = newConnection().dbConnection
    val operations = List(2) {
      context(
        connection = connection,
        conflictAlgorithm = CONFLICT_IGNORE
      )
    }
    val inserts = operations.map { it.insertSql(INSERT_SQL) }
    val defaultUpdates = operations.map {
      it.updateSql(
        template = UPDATE_SQL,
        default = true
      )
    }
    val otherIdentityUpdates = operations.map {
      it.updateSql(
        template = UPDATE_SQL,
        default = false
      )
    }
    val singleDeletes = operations.map {
      it.deleteSql(
        columnName = "id",
        count = 1
      )
    }
    val tableDeletes = operations.map(OperationContext::deleteTableSql)
    val twoEntityDelete = operations[0].deleteSql(
      columnName = "id",
      count = 2
    )

    mapOf(
      "insert" to inserts,
      "default update" to defaultUpdates,
      "update by another identity column" to otherIdentityUpdates,
      "single delete" to singleDeletes,
      "table delete" to tableDeletes
    ).forEach { (label, sql) ->
      assertWithMessage(label)
        .that(sql[1])
        .isSameInstanceAs(sql[0])
    }
    assertThat(listOf(singleDeletes[0], tableDeletes[0], twoEntityDelete))
      .containsExactly(
        "DELETE FROM books WHERE id IN (?)",
        "DELETE FROM books WHERE 1",
        "DELETE FROM books WHERE id IN (?,?)"
      )
      .inOrder()
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
