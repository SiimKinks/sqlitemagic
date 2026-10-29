package com.siimkinks.sqlitemagic.runtime.contract.transaction

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class EmptyBulkTransactionTest(
  private val modelCase: BulkPersistModelCase<*>,
  private val operation: Operation,
  private val terminal: OperationTerminal,
  private val conflictAlgorithm: Int,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  enum class Operation { INSERT, UPDATE, PERSIST, DELETE }

  @Test
  fun emptyBulkOperationCommitsWritesBeforeAndAfterIt() = assertEnclosingTransactionCommits(modelCase)

  private fun <T> assertEnclosingTransactionCommits(modelCase: BulkPersistModelCase<T>) {
    val before = modelCase.newValue(sequence = 1)
    val after = modelCase.newValue(sequence = 2)
    var expected = captureDatabaseSnapshot(modelCase)
    inTransaction {
      insertedResult(
        result = modelCase
          .insert(value = before)
          .execute(),
        modelName = modelCase.name
      )
      executeEmptyBulkOperation(modelCase)
      insertedResult(
        result = modelCase
          .insert(value = after)
          .execute(),
        modelName = modelCase.name
      )
      expected = captureDatabaseSnapshot(modelCase)
    }

    assertThat(expected.parents).hasSize(2)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  private fun executeEmptyBulkOperation(modelCase: BulkPersistModelCase<*>) {
    when (operation) {
      Operation.INSERT -> {
        val builder = modelCase
          .bulkInsert(values = emptyList())
          .conflictAlgorithm(conflictAlgorithm)
        when (terminal) {
          EXECUTE -> assertThat(builder.execute()).isFalse()
          OBSERVE -> adapter
            .observe(builder)
            .await()
        }
      }
      Operation.UPDATE -> when (terminal) {
        EXECUTE -> assertThat(
          modelCase.executeBulkUpdate(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
        ).isFalse()
        OBSERVE -> modelCase
          .observeBulkUpdate(
            adapter = adapter,
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
          .await()
      }
      Operation.PERSIST -> when (terminal) {
        EXECUTE -> assertThat(
          modelCase.executeBulkPersist(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
        ).isFalse()
        OBSERVE -> modelCase
          .observeBulkPersist(
            adapter = adapter,
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
          .await()
      }
      Operation.DELETE -> {
        check(modelCase is BulkDeleteModelCase<*>)
        val deletedCount = terminal.select(
          execute = { modelCase.executeBulkDelete(values = emptyList()) },
          observe = {
            modelCase.observeBulkDelete(
              adapter = adapter,
              values = emptyList()
            )
          }
        )
        assertThat(deletedCount).isEqualTo(0)
      }
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}: {1}, {2}, conflict={3}, {4}")
    fun cases() = ModelCatalog.emptyBulkPersistCases.flatMap { modelCase ->
      Operation.entries.flatMap { operation ->
        OperationTerminal.entries.flatMap { terminal ->
          val conflicts = when (operation) {
            Operation.DELETE -> listOf(CONFLICT_NONE)
            else -> listOf(CONFLICT_NONE, CONFLICT_IGNORE)
          }
          conflicts.flatMap { conflict ->
            reactiveAdapters.map { adapter -> arrayOf(modelCase, operation, terminal, conflict, adapter) }
          }
        }
      }
    }
  }
}
