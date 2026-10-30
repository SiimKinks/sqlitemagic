package com.siimkinks.sqlitemagic.coroutines

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.RECURSIVE_TABLES
import com.siimkinks.sqlitemagic.RecursiveParent
import com.siimkinks.sqlitemagic.RecursiveScenario
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.TestSchema
import com.siimkinks.sqlitemagic.TransactionResult
import com.siimkinks.sqlitemagic.bulkDelete
import com.siimkinks.sqlitemagic.bulkDeleteByColumn
import com.siimkinks.sqlitemagic.bulkInsert
import com.siimkinks.sqlitemagic.bulkPersist
import com.siimkinks.sqlitemagic.bulkPersistByColumn
import com.siimkinks.sqlitemagic.bulkUpdate
import com.siimkinks.sqlitemagic.bulkUpdateByColumn
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.deleteByColumn
import com.siimkinks.sqlitemagic.deleteTable
import com.siimkinks.sqlitemagic.entity.ConnectionProvidedOperation
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.newConnection
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.persistByColumn
import com.siimkinks.sqlitemagic.testDbConnection
import com.siimkinks.sqlitemagic.twoParentBulkGraphs
import com.siimkinks.sqlitemagic.update
import com.siimkinks.sqlitemagic.updateByColumn
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class EntityOperationsTest {
  @Test
  fun `await returns what execute returns for every builder`() {
    val adapter = TestAdapter()
    fun entities() = listOf(
      TestEntity(),
      TestEntity(
        id = "id-2",
        key = "key-2"
      )
    )
    val cases = listOf(
      Case(
        label = "insert",
        builder = { adapter.insert(TestEntity()) },
        execute = EntityInsertBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "bulk insert",
        builder = { adapter.bulkInsert(entities()) },
        execute = EntityBulkInsertBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "update",
        builder = { adapter.update(TestEntity()) },
        execute = EntityUpdateBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "update by column",
        builder = { adapter.updateByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "bulk update",
        builder = { adapter.bulkUpdate(entities()) },
        execute = EntityBulkUpdateBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "bulk update by column",
        builder = { adapter.bulkUpdateByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "persist",
        builder = { adapter.persist(TestEntity()) },
        execute = EntityPersistBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "persist by column",
        builder = { adapter.persistByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "bulk persist",
        builder = { adapter.bulkPersist(entities()) },
        execute = EntityBulkPersistBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "bulk persist by column",
        builder = { adapter.bulkPersistByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "delete",
        builder = { adapter.delete(TestEntity()) },
        execute = EntityDeleteBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "delete by column",
        builder = { adapter.deleteByColumn(TestEntity()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "bulk delete",
        builder = { adapter.bulkDelete(entities()) },
        execute = EntityBulkDeleteBuilder::execute,
        await = { builder -> builder.await() }
      ),
      Case(
        label = "bulk delete by column",
        builder = { adapter.bulkDeleteByColumn(entities()) },
        execute = { builder -> builder.execute(TestSchema.key) },
        await = { builder -> builder.await(TestSchema.key) }
      ),
      Case(
        label = "delete table",
        builder = { adapter.deleteTable() },
        execute = EntityDeleteTableBuilder::execute,
        await = { builder -> builder.await() }
      )
    )

    for (case in cases) {
      val (executed, awaited) = listOf(false, true).map(case::run)

      assertWithMessage(case.label)
        .that(awaited)
        .isEqualTo(executed)
    }
  }

  @Test
  fun `a failing operation throws the database failure`() {
    val failure = RuntimeException("statement failed")
    val database = mock<SupportSQLiteDatabase>()
    whenever(database.compileStatement(any())).thenThrow(failure)

    val thrown = assertThrows(RuntimeException::class.java) {
      runBlocking {
        TestAdapter()
          .insert(TestEntity())
          .usingConnection(testDbConnection(database))
          .await()
      }
    }

    assertThat(thrown.javaClass to thrown.message).isEqualTo(failure.javaClass to failure.message)
  }

  @Test
  fun `cancelling a bulk operation rolls back its partial work without table triggers`() {
    mapOf<String, suspend (Iterable<TestEntity>, DbConnectionImpl) -> Boolean>(
      "bulk insert" to { entities, connection ->
        TestAdapter()
          .bulkInsert(entities)
          .usingConnection(connection)
          .await()
      },
      "bulk update" to { entities, connection ->
        TestAdapter()
          .bulkUpdate(entities)
          .usingConnection(connection)
          .await()
      },
      "bulk persist" to { entities, connection ->
        TestAdapter()
          .bulkPersist(entities)
          .usingConnection(connection)
          .await()
      }
    ).forEach { (label, operation) ->
      val connection = newConnection()
      val caller = Caller()
      val entities = Iterable {
        iterator {
          yield(TestEntity())
          caller.cancel()
          yield(
            TestEntity(
              id = "id-2",
              key = "key-2"
            )
          )
        }
      }

      val completed = caller.run { operation(entities, connection.connection) }

      assertWithMessage(label)
        .that(connection.transactionResult(completed) to connection.triggers)
        .isEqualTo(
          TransactionResult(
            result = false,
            committed = 0,
            rolledBack = 1
          ) to emptyList<Set<String>>()
        )
    }
  }

  @Test
  fun `cancelling a recursive bulk insert rolls back the active graph and keeps completed ignored graphs`() {
    val outcomes = listOf(CONFLICT_NONE, CONFLICT_IGNORE).map { conflictAlgorithm ->
      val scenario = RecursiveScenario()
      scenario.database.insertResults.addAll(listOf(201L, 202L, 211L, 212L))
      val caller = Caller()
      val graphs = twoParentBulkGraphs()
      val entities = object : Iterable<RecursiveParent> {
        override fun iterator() = object : Iterator<RecursiveParent> {
          private var index = 0

          override fun hasNext(): Boolean {
            if (index == 1) {
              caller.cancel()
            }
            return index < graphs.size
          }

          override fun next() = graphs[index++]
        }
      }

      caller.run {
        scenario.parent
          .bulkInsert(entities)
          .usingConnection(scenario.connection)
          .conflictAlgorithm(conflictAlgorithm)
          .await()
      }
      Triple(
        first = scenario.database.successfulTransactions,
        second = scenario.database.rolledBackTransactions,
        third = scenario.triggers
      )
    }

    assertThat(outcomes).isEqualTo(
      listOf(
        Triple(
          first = 0,
          second = 1,
          third = emptyList()
        ),
        Triple(
          first = 1,
          second = 0,
          third = listOf(RECURSIVE_TABLES)
        )
      )
    )
  }

  private data class Outcome(
    val result: Any?,
    val statements: List<String>,
    val triggers: List<Set<String>>
  )

  private class Case<B : ConnectionProvidedOperation<B>>(
    val label: String,
    val builder: () -> B,
    val execute: (B) -> Any?,
    val await: suspend (B) -> Any?
  ) {
    fun run(awaited: Boolean): Outcome {
      val connection = newConnection()
      val builder = builder().usingConnection(connection.connection)
      val result = when {
        awaited -> runBlocking { await(builder) }
        else -> execute(builder)
      }
      return Outcome(
        result = result,
        statements = connection.statementSql,
        triggers = connection.triggers
      )
    }
  }

  private class Caller {
    private lateinit var job: Job

    fun cancel() = job.cancel()

    fun run(operation: suspend () -> Boolean): Boolean {
      var completed = false
      runBlocking {
        job = launch(start = LAZY) { completed = operation() }
        job.start()
        job.join()
      }
      return completed
    }
  }
}
