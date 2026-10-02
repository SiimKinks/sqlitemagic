package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.EntityOperation.INSERT
import com.siimkinks.sqlitemagic.EntityOperation.UPDATE
import org.junit.Test

internal class OperationHelperTest {
  @Test
  fun `default conflict helpers reuse statements owned by the manager`() {
    for (conflictAlgorithm in listOf(CONFLICT_NONE, CONFLICT_ABORT)) {
      val connection = newConnection()
      val manager = EntityDbManager(connection.connection)
      val helper = OperationHelper(
        conflictAlgorithm = conflictAlgorithm,
        operation = UPDATE
      )
      val insert = helper.insertStatement(
        tableName = "books",
        sql = "INSERT%s INTO books(name) VALUES (?)",
        manager = manager
      )
      val update = helper.updateStatement(
        tableName = "books",
        sql = "UPDATE%s books SET name=? WHERE id=?",
        manager = manager
      )
      helper.close()
      helper.close()
      val nextHelper = OperationHelper(
        conflictAlgorithm = conflictAlgorithm,
        operation = UPDATE
      )
      val reusedInsert = nextHelper.insertStatement(
        tableName = "books",
        sql = "INSERT%s INTO books(name) VALUES (?)",
        manager = manager
      )
      val reusedUpdate = nextHelper.updateStatement(
        tableName = "books",
        sql = "UPDATE%s books SET name=? WHERE id=?",
        manager = manager
      )

      assertWithMessage("conflict algorithm $conflictAlgorithm insert")
        .that(reusedInsert)
        .isSameInstanceAs(insert)
      assertWithMessage("conflict algorithm $conflictAlgorithm update")
        .that(reusedUpdate)
        .isSameInstanceAs(update)
      assertThat(connection.statementSql)
        .containsExactly("INSERT INTO books(name) VALUES (?)", "UPDATE books SET name=? WHERE id=?")
        .inOrder()
      assertThat(connection.recordingDatabase.compiledStatements.map(RecordingStatement::closeCalls))
        .containsExactly(0, 0)

      manager.close()

      assertThat(connection.recordingDatabase.compiledStatements.map(RecordingStatement::closeCalls))
        .containsExactly(1, 1)
    }
  }

  @Test
  fun `custom helpers cache statements by table and close them once`() {
    data class HelperCase(
      val label: String,
      val conflictAlgorithm: Int,
      val operation: EntityOperation,
      val columns: List<Column<*, *, *, *, *>>,
      val sql: String,
      val expectedSql: List<String>
    )
    listOf(
      HelperCase(
        label = "ignored insert",
        conflictAlgorithm = CONFLICT_IGNORE,
        operation = INSERT,
        columns = emptyList(),
        sql = "INSERT%s INTO TABLE(name) VALUES (?)",
        expectedSql = listOf(
          "INSERT OR IGNORE INTO books(name) VALUES (?)",
          "INSERT OR IGNORE INTO authors(name) VALUES (?)"
        )
      ),
      HelperCase(
        label = "ignored update",
        conflictAlgorithm = CONFLICT_IGNORE,
        operation = UPDATE,
        columns = emptyList(),
        sql = "UPDATE%s TABLE SET name=? WHERE id=?",
        expectedSql = listOf(
          "UPDATE OR IGNORE books SET name=? WHERE id=?",
          "UPDATE OR IGNORE authors SET name=? WHERE id=?"
        )
      ),
      HelperCase(
        label = "alternate identity update",
        conflictAlgorithm = CONFLICT_NONE,
        operation = UPDATE,
        columns = listOf(TestSchema.key),
        sql = "UPDATE%s TABLE SET name=? WHERE id=?",
        expectedSql = listOf(
          "UPDATE books SET name=? WHERE key=?",
          "UPDATE authors SET name=? WHERE id=?"
        )
      )
    ).forEach { case ->
      val connection = newConnection()
      val manager = EntityDbManager(connection.connection)
      val helper = OperationHelper(
        conflictAlgorithm = case.conflictAlgorithm,
        operation = case.operation,
        operationByColumns = case.columns
      )

      fun fetch(
        tableName: String,
        sql: String
      ) = when (case.operation) {
        INSERT -> helper.insertStatement(
          tableName = tableName,
          sql = sql,
          manager = manager
        )
        UPDATE -> helper.updateStatement(
          tableName = tableName,
          sql = sql,
          manager = manager
        )
      }
      for (tableName in listOf("books", "authors")) {
        val sql = case.sql.replace(
          oldValue = "TABLE",
          newValue = tableName
        )
        val first = fetch(
          tableName = tableName,
          sql = sql
        )
        val second = fetch(
          tableName = tableName,
          sql = sql
        )
        assertWithMessage("${case.label}: $tableName")
          .that(second)
          .isSameInstanceAs(first)
      }
      helper.close()
      helper.close()
      manager.close()

      assertWithMessage(case.label)
        .that(connection.statementSql)
        .containsExactlyElementsIn(case.expectedSql)
        .inOrder()
      assertWithMessage(case.label)
        .that(connection.recordingDatabase.compiledStatements.map(RecordingStatement::closeCalls))
        .containsExactly(1, 1)
    }
  }

  @Test
  fun `helper close tolerates statement close failures and is idempotent`() {
    for (operation in EntityOperation.entries) {
      val connection = newConnection()
      val helper = OperationHelper(
        conflictAlgorithm = CONFLICT_IGNORE,
        operation = operation
      )
      val manager = EntityDbManager(connection.connection)
      when (operation) {
        INSERT -> helper.insertStatement(
          tableName = "books",
          sql = "INSERT%s INTO books(name) VALUES (?)",
          manager = manager
        )
        UPDATE -> helper.updateStatement(
          tableName = "books",
          sql = "UPDATE%s books SET name=? WHERE id=?",
          manager = manager
        )
      }
      val statement = connection.recordingDatabase.compiledStatements.single()
      statement.closeFailure = IllegalStateException("close failed")

      helper.close()
      helper.close()

      assertWithMessage(operation.name)
        .that(statement.closeCalls)
        .isEqualTo(1)
    }
  }
}
