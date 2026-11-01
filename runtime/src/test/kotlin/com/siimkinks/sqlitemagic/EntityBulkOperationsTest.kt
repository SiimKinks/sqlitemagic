package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class EntityBulkOperationsTest {
  @Test
  fun `bulk operations cover empty ignored partial and failure transactions`() {
    data class BulkCase(
      val label: String,
      val operation: (RecordingDbConnection) -> Boolean,
      val expected: Boolean,
      val committed: Int,
      val rolledBack: Int
    )

    listOf(
      BulkCase(
        label = "empty insert",
        operation = { connection ->
          TestAdapter()
            .bulkInsert(emptyList())
            .usingConnection(connection.dbConnection)
            .execute()
        },
        expected = false,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "successful insert",
        operation = { connection ->
          connection.sqliteConnection.insertResults += listOf(5L, 6L)
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.dbConnection)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "partial ignored insert",
        operation = { connection ->
          connection.sqliteConnection.insertResults += listOf(-1L, 5L)
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "all ignored insert",
        operation = { connection ->
          connection.sqliteConnection.insertResults += listOf(-1L, -1L)
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        label = "non-ignored insert failure",
        operation = { connection ->
          connection.sqliteConnection.insertResults += listOf(5L, -1L)
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.dbConnection)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        label = "successful update",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(1, 1)
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "partially ignored update",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(0, 1)
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "all ignored update",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(0, 0)
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        label = "non-ignored update failure",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(1, 0)
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        label = "successful persist",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(0, 1)
          connection.sqliteConnection.insertResults += 7L
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "partially ignored persist",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(0, 0)
          connection.sqliteConnection.insertResults += listOf(-1L, 7L)
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        label = "all ignored persist",
        operation = { connection ->
          connection.sqliteConnection.updateResults += listOf(0, 0)
          connection.sqliteConnection.insertResults += listOf(-1L, -1L)
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        label = "non-ignored persist failure",
        operation = { connection ->
          connection.sqliteConnection.updateResults += 0
          connection.sqliteConnection.insertResults += -1L
          TestAdapter()
            .bulkPersist(listOf(TestEntity()))
            .usingConnection(connection.dbConnection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      )
    ).forEach { case ->
      val connection = newConnection()
      val actual = connection.transactionResult(case.operation(connection))

      assertWithMessage(case.label)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = case.expected,
            committed = case.committed,
            rolledBack = case.rolledBack
          )
        )
      assertWithMessage(case.label)
        .that(connection.triggers)
        .isEqualTo(
          when {
            case.expected -> listOf(setOf("books"))
            else -> emptyList()
          }
        )
    }
  }

  private fun twoEntities() = listOf(
    TestEntity(
      id = "id-1",
      key = "key-1",
      name = "name"
    ),
    TestEntity(
      id = "id-2",
      key = "key-2",
      name = "name"
    )
  )
}
