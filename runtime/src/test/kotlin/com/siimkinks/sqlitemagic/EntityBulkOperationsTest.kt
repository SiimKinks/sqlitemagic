package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class EntityBulkOperationsTest {
  @Test
  fun `bulk operations cover empty ignored partial and failure transactions`() {
    data class BulkCase(
      val name: String,
      val operation: (RecordingConnection) -> Boolean,
      val expected: Boolean,
      val committed: Int,
      val rolledBack: Int
    )

    listOf(
      BulkCase(
        name = "empty insert",
        operation = { connection ->
          TestAdapter()
            .bulkInsert(emptyList())
            .usingConnection(connection.connection)
            .execute()
        },
        expected = false,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "successful insert",
        operation = { connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(5L, 6L))
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.connection)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "partial ignored insert",
        operation = { connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, 5L))
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "all ignored insert",
        operation = { connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, -1L))
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "non-ignored insert failure",
        operation = { connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(5L, -1L))
          TestAdapter()
            .bulkInsert(twoEntities())
            .usingConnection(connection.connection)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "successful update",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "partially ignored update",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(0, 1))
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "all ignored update",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(0, 0))
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "non-ignored update failure",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(1, 0))
          TestAdapter()
            .bulkUpdate(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "successful persist",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(0, 1))
          connection.recordingDatabase.insertResults += 7L
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "partially ignored persist",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(0, 0))
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, 7L))
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "all ignored persist",
        operation = { connection ->
          connection.recordingDatabase.updateResults.addAll(listOf(0, 0))
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, -1L))
          TestAdapter()
            .bulkPersist(twoEntities())
            .usingConnection(connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "non-ignored persist failure",
        operation = { connection ->
          connection.recordingDatabase.updateResults += 0
          connection.recordingDatabase.insertResults += -1L
          TestAdapter()
            .bulkPersist(listOf(TestEntity()))
            .usingConnection(connection.connection)
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

      assertWithMessage(case.name)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = case.expected,
            committed = case.committed,
            rolledBack = case.rolledBack
          )
        )
      assertWithMessage(case.name)
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
