package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Completable
import io.reactivex.observers.TestObserver
import org.junit.Test

internal class EntityBulkOperationsTest {
  @Test
  fun `disposed bulk operations roll back partial work without table triggers`() {
    mapOf<String, (Iterable<TestEntity>, DbConnectionImpl) -> Completable>(
      "bulk insert" to { entities, connection ->
        TestAdapter()
          .bulkInsert(entities)
          .usingConnection(connection)
          .observe()
      },
      "bulk update" to { entities, connection ->
        TestAdapter()
          .bulkUpdate(entities)
          .usingConnection(connection)
          .observe()
      },
      "bulk persist" to { entities, connection ->
        TestAdapter()
          .bulkPersist(entities)
          .usingConnection(connection)
          .observe()
      }
    ).forEach { (operationName, operation) ->
      val connection = newConnection()
      connection.recordingDatabase.insertResults += 1L
      connection.recordingDatabase.updateResults += 1
      val observer = TestObserver<Void>()
      val (first, second) = twoEntities()
      val entities = Iterable {
        iterator {
          yield(first)
          observer.dispose()
          yield(second)
        }
      }

      operation(entities, connection.connection).subscribe(observer)

      observer.assertEmpty()
      assertWithMessage(operationName)
        .that(connection.transactionResult(Unit))
        .isEqualTo(
          TransactionResult(
            result = Unit,
            committed = 0,
            rolledBack = 1
          )
        )
      assertWithMessage(operationName)
        .that(connection.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `empty bulk Rx terminals complete`() {
    data class EmptyBulkCase(
      val label: String,
      val operation: (BulkRxInput) -> Completable
    )
    listOf(
      EmptyBulkCase(
        label = "bulk insert",
        operation = { input ->
          TestAdapter()
            .bulkInsert(input.entities)
            .usingConnection(input.connection.connection)
            .observe()
        }
      ),
      EmptyBulkCase(
        label = "bulk update",
        operation = { input ->
          TestAdapter()
            .bulkUpdate(input.entities)
            .usingConnection(input.connection.connection)
            .observe()
        }
      ),
      EmptyBulkCase(
        label = "bulk persist",
        operation = { input ->
          TestAdapter()
            .bulkPersist(input.entities)
            .usingConnection(input.connection.connection)
            .observe()
        }
      )
    ).forEach { case ->
      val connection = newConnection()
      case.operation(
        BulkRxInput(
          connection = connection,
          entities = oneShotIterable(emptyList())
        )
      )
        .test()
        .assertResult()

      assertWithMessage(case.label)
        .that(connection.recordingDatabase.successfulTransactions)
        .isEqualTo(1)
      assertWithMessage(case.label)
        .that(connection.recordingDatabase.rolledBackTransactions)
        .isEqualTo(0)
      assertWithMessage(case.label)
        .that(connection.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `non-empty one-shot bulk Rx terminals preserve failure and ignored completion`() {
    data class NonEmptyBulkCase(
      val label: String,
      val configure: (RecordingDatabase) -> Unit,
      val operation: (BulkRxInput) -> Completable,
      val expectedTerminal: BulkRxTerminal
    )
    listOf(
      NonEmptyBulkCase(
        label = "bulk insert default conflict",
        configure = { it.insertResults += -1L },
        operation = { input ->
          TestAdapter()
            .bulkInsert(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.ERROR
      ),
      NonEmptyBulkCase(
        label = "bulk insert ignored conflict",
        configure = { it.insertResults += -1L },
        operation = { input ->
          TestAdapter()
            .bulkInsert(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.COMPLETE
      ),
      NonEmptyBulkCase(
        label = "bulk update default conflict",
        configure = { it.updateResults += 0 },
        operation = { input ->
          TestAdapter()
            .bulkUpdate(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.ERROR
      ),
      NonEmptyBulkCase(
        label = "bulk update ignored conflict",
        configure = { it.updateResults += 0 },
        operation = { input ->
          TestAdapter()
            .bulkUpdate(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.COMPLETE
      ),
      NonEmptyBulkCase(
        label = "bulk persist default conflict",
        configure = {
          it.updateResults += 0
          it.insertResults += -1L
        },
        operation = { input ->
          TestAdapter()
            .bulkPersist(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_NONE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.ERROR
      ),
      NonEmptyBulkCase(
        label = "bulk persist ignored conflict",
        configure = {
          it.updateResults += 0
          it.insertResults += -1L
        },
        operation = { input ->
          TestAdapter()
            .bulkPersist(input.entities)
            .usingConnection(input.connection.connection)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .observe()
        },
        expectedTerminal = BulkRxTerminal.COMPLETE
      )
    ).forEach { case ->
      val connection = newConnection()
      case.configure(connection.recordingDatabase)
      val observer = case.operation(
        BulkRxInput(
          connection = connection,
          entities = oneShotIterable(listOf(TestEntity()))
        )
      ).test()

      when (case.expectedTerminal) {
        BulkRxTerminal.ERROR -> observer.assertFailure(OperationFailedException::class.java)
        BulkRxTerminal.COMPLETE -> observer.assertResult()
      }
      assertWithMessage(case.label)
        .that(connection.recordingDatabase.successfulTransactions)
        .isEqualTo(0)
      assertWithMessage(case.label)
        .that(connection.recordingDatabase.rolledBackTransactions)
        .isEqualTo(1)
      assertWithMessage(case.label)
        .that(connection.triggers)
        .isEmpty()
    }
  }

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

  private data class BulkRxInput(
    val connection: RecordingConnection,
    val entities: Iterable<TestEntity>
  )

  private enum class BulkRxTerminal {
    ERROR,
    COMPLETE
  }
}
