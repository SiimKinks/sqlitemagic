package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityDbManagerTest {
  @Test
  fun `insert and update caches compile lazily and reuse their first statement`() {
    val connection = newConnection()
    val manager = EntityDbManager(connection.connection)

    assertThat(connection.statementSql).isEmpty()

    val insert = manager.insertStatement("INSERT%s INTO books(name) VALUES ('100%%')")
    val update = manager.updateStatement("UPDATE%s books SET name=? WHERE id=?")

    assertThat(manager.insertStatement("unused insert SQL"))
      .isSameInstanceAs(insert)
    assertThat(manager.updateStatement("unused update SQL"))
      .isSameInstanceAs(update)
    assertThat(update).isNotSameInstanceAs(insert)
    assertThat(connection.statementSql)
      .containsExactly("INSERT INTO books(name) VALUES ('100%')", "UPDATE books SET name=? WHERE id=?")
      .inOrder()
    assertThat(connection.statementCloseCalls)
      .containsExactly(0, 0)

    manager.close()
    manager.close()

    assertThat(connection.statementCloseCalls)
      .containsExactly(1, 1)
  }

  @Test
  fun `plain compilation preserves SQL and returns caller-owned statements`() {
    val connection = newConnection()
    val manager = EntityDbManager(connection.connection)
    val sql = "UPDATE books SET name='100%' WHERE name LIKE '%s'"
    val first = manager.compileStatement(sql)
    val second = manager.compileStatement(sql)

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(connection.statementSql)
      .containsExactly(sql, sql)
      .inOrder()

    manager.close()

    assertThat(connection.statementCloseCalls)
      .containsExactly(0, 0)

    first.close()
    second.close()

    assertThat(connection.statementCloseCalls)
      .containsExactly(1, 1)
  }

  @Test
  fun `conflict compilation formats SQL and leaves ownership with the caller`() {
    data class ConflictCase(
      val label: String,
      val algorithm: Int,
      val clause: String
    )

    val cases = listOf(
      ConflictCase(
        label = "none",
        algorithm = CONFLICT_NONE,
        clause = ""
      ),
      ConflictCase(
        label = "rollback",
        algorithm = CONFLICT_ROLLBACK,
        clause = " OR ROLLBACK"
      ),
      ConflictCase(
        label = "abort",
        algorithm = CONFLICT_ABORT,
        clause = " OR ABORT"
      ),
      ConflictCase(
        label = "fail",
        algorithm = CONFLICT_FAIL,
        clause = " OR FAIL"
      ),
      ConflictCase(
        label = "ignore",
        algorithm = CONFLICT_IGNORE,
        clause = " OR IGNORE"
      ),
      ConflictCase(
        label = "replace",
        algorithm = CONFLICT_REPLACE,
        clause = " OR REPLACE"
      )
    )
    for ((label, algorithm, clause) in cases) {
      val connection = newConnection()
      val manager = EntityDbManager(connection.connection)
      val statement = manager.compileStatement(
        sql = "INSERT%s INTO books(name) VALUES ('100%%')",
        conflictAlgorithm = algorithm
      )

      assertWithMessage(label)
        .that(connection.statementSql)
        .containsExactly("INSERT$clause INTO books(name) VALUES ('100%')")

      manager.close()

      assertWithMessage(label)
        .that(connection.recordingDatabase.compiledStatements.single().closeCalls)
        .isEqualTo(0)

      statement.close()
    }
  }

  @Test
  fun `close independently attempts cached statement cleanup and tolerates failures`() {
    val failureCases = mapOf(
      "insert fails" to setOf(0),
      "update fails" to setOf(1),
      "both fail" to setOf(0, 1)
    )
    for ((label, failingIndexes) in failureCases) {
      val connection = newConnection()
      val manager = EntityDbManager(connection.connection)
      manager.insertStatement("INSERT%s INTO books(name) VALUES (?)")
      manager.updateStatement("UPDATE%s books SET name=? WHERE id=?")
      for (index in failingIndexes) {
        connection.recordingDatabase.compiledStatements[index].closeFailure = IllegalStateException("close failed")
      }

      manager.close()
      manager.close()

      assertWithMessage(label)
        .that(connection.statementCloseCalls)
        .containsExactly(1, 1)
      val failure = assertThrows(IllegalStateException::class.java) {
        manager.compileStatement("SELECT 1")
      }
      assertWithMessage(label)
        .that(failure)
        .hasMessageThat()
        .isEqualTo("DB connection closed")
    }
  }

  @Test
  fun `closed manager rejects every access path before formatting SQL`() {
    val accesses: Map<String, (EntityDbManager) -> Any> = mapOf(
      "insert" to { it.insertStatement("INSERT%s INTO books(name) VALUES (?)") },
      "update" to { it.updateStatement("UPDATE%s books SET name=? WHERE id=?") },
      "plain compile" to { it.compileStatement("SELECT 1") },
      "conflict compile" to {
        it.compileStatement(
          sql = "INSERT%s INTO books(name) VALUES (?)",
          conflictAlgorithm = CONFLICT_IGNORE
        )
      },
      "malformed insert format" to { it.insertStatement("%q") },
      "malformed update format" to { it.updateStatement("%q") },
      "malformed conflict format" to {
        it.compileStatement(
          sql = "%q",
          conflictAlgorithm = CONFLICT_IGNORE
        )
      },
      "invalid conflict algorithm" to {
        it.compileStatement(
          sql = "INSERT%s INTO books(name) VALUES (?)",
          conflictAlgorithm = -1
        )
      }
    )
    for (initialized in listOf(false, true)) {
      val connection = newConnection()
      val manager = EntityDbManager(connection.connection)
      if (initialized) {
        manager.insertStatement("INSERT%s INTO books(name) VALUES (?)")
        manager.updateStatement("UPDATE%s books SET name=? WHERE id=?")
      }
      val compiledSql = connection.statementSql.toList()

      manager.close()
      manager.close()

      for ((label, access) in accesses) {
        val failure = assertThrows(IllegalStateException::class.java) { access(manager) }
        assertWithMessage("$label with initialized caches=$initialized")
          .that(failure)
          .hasMessageThat()
          .isEqualTo("DB connection closed")
      }
      assertThat(connection.statementSql).containsExactlyElementsIn(compiledSql)
    }
  }
}

private val RecordingConnection.statementCloseCalls
  get() = recordingDatabase.compiledStatements.map(RecordingStatement::closeCalls)
