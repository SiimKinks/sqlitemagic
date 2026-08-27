package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Completable
import io.reactivex.observers.TestObserver
import org.junit.Test

internal class EntityBulkOperationsTest {
  @Test
  fun `successful bulk update and persist emit one aggregate table trigger`() {
    data class TriggerCase(
      val name: String,
      val configure: (RecordingDatabase) -> Unit,
      val operation: (TestAdapter, RecordingConnection) -> Boolean
    )

    val entities = listOf(
      TestEntity(id = "id-1", key = "key-1", name = "name"),
      TestEntity(id = "id-2", key = "key-2", name = "name")
    )
    listOf(
      TriggerCase(
        name = "bulk update",
        configure = { it.updateResults.addAll(listOf(1, 1)) },
        operation = { adapter, connection ->
          adapter.bulkUpdate(entities)
            .usingConnection(connection.connection)
            .execute()
        }
      ),
      TriggerCase(
        name = "bulk persist with update and insert",
        configure = {
          it.updateResults.addAll(listOf(1, 0))
          it.insertResults.add(7L)
        },
        operation = { adapter, connection ->
          adapter.bulkPersist(entities)
            .usingConnection(connection.connection)
            .execute()
        }
      )
    ).forEach { case ->
      val connection = newConnection()
      case.configure(connection.recordingDatabase)

      assertWithMessage(case.name)
        .that(case.operation(TestAdapter(), connection))
        .isTrue()
      assertWithMessage(case.name)
        .that(connection.triggers)
        .containsExactlyElementsIn(listOf(setOf("books")))
    }
  }

  @Test
  fun `failed bulk update and persist roll back without table triggers`() {
    data class FailureCase(
      val name: String,
      val configure: (RecordingDatabase) -> Unit,
      val operation: (TestAdapter, RecordingConnection) -> Boolean
    )

    val entities = listOf(
      TestEntity(id = "id-1", key = "key-1", name = "name"),
      TestEntity(id = "id-2", key = "key-2", name = "name")
    )
    listOf(
      FailureCase(
        name = "bulk update",
        configure = { it.updateResults.addAll(listOf(1, 0)) },
        operation = { adapter, connection ->
          adapter.bulkUpdate(entities)
            .usingConnection(connection.connection)
            .execute()
        }
      ),
      FailureCase(
        name = "bulk persist",
        configure = {
          it.updateResults.addAll(listOf(1, 0))
          it.insertResults.add(-1L)
        },
        operation = { adapter, connection ->
          adapter.bulkPersist(entities)
            .usingConnection(connection.connection)
            .execute()
        }
      )
    ).forEach { case ->
      val connection = newConnection()
      case.configure(connection.recordingDatabase)
      val actual = connection.transactionResult(case.operation(TestAdapter(), connection))

      assertWithMessage(case.name)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = false,
            committed = 0,
            rolledBack = 1
          )
        )
      assertWithMessage(case.name)
        .that(connection.triggers)
        .isEmpty()
    }
  }

  @Test
  fun `disposed bulk update and persist roll back partial work without table triggers`() {
    listOf("bulk update", "bulk persist").forEach { operationName ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults += 1
      val adapter = TestAdapter()
      val observer = TestObserver<Void>()
      val entities = object : Iterable<TestEntity> {
        private val values = listOf(
          TestEntity(id = "id-1", key = "key-1", name = "name"),
          TestEntity(id = "id-2", key = "key-2", name = "name")
        )

        override fun iterator() = object : Iterator<TestEntity> {
          private var index = 0

          override fun hasNext(): Boolean {
            if (index == 1) {
              observer.dispose()
            }
            return index < values.size
          }

          override fun next() = values[index++]
        }
      }
      val operation = when (operationName) {
        "bulk update" -> adapter
          .bulkUpdate(entities)
          .usingConnection(connection.connection)
          .observe()
        "bulk persist" -> adapter
          .bulkPersist(entities)
          .usingConnection(connection.connection)
          .observe()
        else -> error("Unknown operation: $operationName")
      }

      operation.subscribe(observer)

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
  fun `empty bulk Rx terminals complete for update and persist builders`() {
    data class EmptyBulkCase(
      val label: String,
      val operation: (BulkRxInput) -> Completable
    )
    listOf(
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
        label = "bulk update by column",
        operation = { input ->
          TestAdapter()
            .bulkUpdateByColumn(input.entities)
            .usingConnection(input.connection.connection)
            .observe(TestSchema.key)
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
      ),
      EmptyBulkCase(
        label = "bulk persist by column",
        operation = { input ->
          TestAdapter()
            .bulkPersistByColumn(input.entities)
            .usingConnection(input.connection.connection)
            .observe(TestSchema.key)
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
  fun `non-empty one-shot bulk Rx terminals preserve failure and ignored completion`() {
    data class NonEmptyBulkCase(
      val label: String,
      val configure: (RecordingDatabase) -> Unit,
      val operation: (BulkRxInput) -> Completable,
      val expectedTerminal: BulkRxTerminal
    )
    listOf(
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
          entities = oneShotIterable(
            listOf(
              TestEntity(
                id = "id-1",
                key = "key-1",
                name = "name"
              )
            )
          )
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
      val operation: (TestAdapter, RecordingConnection) -> Boolean,
      val expected: Boolean,
      val committed: Int,
      val rolledBack: Int
    )

    listOf(
      BulkCase(
        name = "empty insert",
        operation = { handler, connection ->
          handler
            .bulkInsert(emptyList())
            .usingConnection(connection.connection)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkCase(
        name = "successful insert",
        operation = { handler, connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(5L, 6L))
          handler
            .bulkInsert(listOf(TestEntity("id-1", "key-1", "name"), TestEntity("id-2", "key-2", "name")))
            .usingConnection(connection.connection)
            .execute()
        },
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkCase(
        name = "partial ignored insert",
        operation = { handler, connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, 5L))
          handler
            .bulkInsert(listOf(TestEntity("id-1", "key-1", "name"), TestEntity("id-2", "key-2", "name")))
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
        operation = { handler, connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(-1L, -1L))
          handler
            .bulkInsert(listOf(TestEntity("id-1", "key-1", "name"), TestEntity("id-2", "key-2", "name")))
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
        operation = { handler, connection ->
          connection.recordingDatabase.insertResults.addAll(listOf(5L, -1L))
          handler
            .bulkInsert(listOf(TestEntity("id-1", "key-1", "name"), TestEntity("id-2", "key-2", "name")))
            .usingConnection(connection.connection)
            .execute()
        },
        expected = false,
        committed = 0,
        rolledBack = 1
      )
    ).forEach { case ->
      val connection = newConnection()
      val actual = connection.transactionResult(case.operation(TestAdapter(), connection))

      assertWithMessage(case.name)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = case.expected,
            committed = case.committed,
            rolledBack = case.rolledBack
          )
        )
      if (case.name == "successful insert") {
        assertThat(connection.triggers)
          .containsExactly(setOf("books"))
      }
    }

    data class BulkUpdateCase(
      val name: String,
      val updateResults: List<Int>,
      val conflictAlgorithm: Int,
      val entities: List<TestEntity>,
      val expected: Boolean,
      val committed: Int,
      val rolledBack: Int
    )

    listOf(
      BulkUpdateCase(
        name = "successful update",
        updateResults = listOf(1, 1),
        conflictAlgorithm = 0,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkUpdateCase(
        name = "empty update",
        updateResults = emptyList(),
        conflictAlgorithm = 0,
        entities = emptyList(),
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkUpdateCase(
        name = "partially ignored update",
        updateResults = listOf(0, 1),
        conflictAlgorithm = CONFLICT_IGNORE,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkUpdateCase(
        name = "all ignored update",
        updateResults = listOf(0, 0),
        conflictAlgorithm = CONFLICT_IGNORE,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkUpdateCase(
        name = "non-ignored update failure",
        updateResults = listOf(1, 0),
        conflictAlgorithm = 0,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = false,
        committed = 0,
        rolledBack = 1
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults.addAll(case.updateResults)
      val result = TestAdapter()
        .bulkUpdate(case.entities)
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)
        .execute()
      val actual = connection.transactionResult(result)

      assertWithMessage(case.name)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = case.expected,
            committed = case.committed,
            rolledBack = case.rolledBack
          )
        )
    }

    data class BulkPersistCase(
      val name: String,
      val updateResults: List<Int>,
      val insertResults: List<Long>,
      val conflictAlgorithm: Int,
      val entities: List<TestEntity>,
      val expected: Boolean,
      val committed: Int,
      val rolledBack: Int
    )

    listOf(
      BulkPersistCase(
        name = "successful persist",
        updateResults = listOf(0, 1),
        insertResults = listOf(7L),
        conflictAlgorithm = 0,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkPersistCase(
        name = "empty persist",
        updateResults = emptyList(),
        insertResults = emptyList(),
        conflictAlgorithm = 0,
        entities = emptyList(),
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkPersistCase(
        name = "partially ignored persist",
        updateResults = listOf(0, 0),
        insertResults = listOf(-1L, 7L),
        conflictAlgorithm = CONFLICT_IGNORE,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = true,
        committed = 1,
        rolledBack = 0
      ),
      BulkPersistCase(
        name = "all ignored persist",
        updateResults = listOf(0, 0),
        insertResults = listOf(-1L, -1L),
        conflictAlgorithm = CONFLICT_IGNORE,
        entities = listOf(
          TestEntity("id-1", "key-1", "name"),
          TestEntity("id-2", "key-2", "name")
        ),
        expected = false,
        committed = 0,
        rolledBack = 1
      ),
      BulkPersistCase(
        name = "non-ignored persist failure",
        updateResults = listOf(0),
        insertResults = listOf(-1L),
        conflictAlgorithm = 0,
        entities = listOf(TestEntity("id-1", "key-1", "name")),
        expected = false,
        committed = 0,
        rolledBack = 1
      )
    ).forEach { case ->
      val connection = newConnection()
      connection.recordingDatabase.updateResults.addAll(case.updateResults)
      connection.recordingDatabase.insertResults.addAll(case.insertResults)
      val result = TestAdapter()
        .bulkPersist(case.entities)
        .usingConnection(connection.connection)
        .conflictAlgorithm(case.conflictAlgorithm)
        .execute()
      val actual = connection.transactionResult(result)

      assertWithMessage(case.name)
        .that(actual)
        .isEqualTo(
          TransactionResult(
            result = case.expected,
            committed = case.committed,
            rolledBack = case.rolledBack
          )
        )
    }
  }

  private data class BulkRxInput(
    val connection: RecordingConnection,
    val entities: Iterable<TestEntity>
  )

  private enum class BulkRxTerminal {
    ERROR,
    COMPLETE
  }
}
