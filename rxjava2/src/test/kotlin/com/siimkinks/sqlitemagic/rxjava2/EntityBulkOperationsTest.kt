package com.siimkinks.sqlitemagic.rxjava2

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.RecordingConnection
import com.siimkinks.sqlitemagic.RecordingDatabase
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.TransactionResult
import com.siimkinks.sqlitemagic.bulkInsert
import com.siimkinks.sqlitemagic.bulkPersist
import com.siimkinks.sqlitemagic.bulkUpdate
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.oneShotIterable
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
