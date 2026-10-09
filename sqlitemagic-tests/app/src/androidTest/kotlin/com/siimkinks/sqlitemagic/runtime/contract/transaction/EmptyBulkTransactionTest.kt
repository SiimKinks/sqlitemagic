package com.siimkinks.sqlitemagic.runtime.contract.transaction

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EmptyBulkTransactionTest(
  private val modelCase: BulkPersistModelCase<*>,
  private val operation: Operation,
  private val terminal: Terminal,
  private val conflictAlgorithm: Int
) : RuntimeDatabaseTest() {
  enum class Operation { INSERT, UPDATE, PERSIST, DELETE }

  enum class Terminal { EXECUTE, OBSERVE }

  @Test
  fun emptyBulkOperationCommitsWritesBeforeAndAfterIt() = assertEnclosingTransactionCommits(modelCase)

  private fun <T> assertEnclosingTransactionCommits(modelCase: BulkPersistModelCase<T>) {
    val before = modelCase.newValue(sequence = 1)
    val after = modelCase.newValue(sequence = 2)
    var expected = captureDatabaseSnapshot(modelCase)
    inTransaction {
      assertSeedInserted(
        result = modelCase
          .insert(value = before)
          .execute(),
        modelName = modelCase.name
      )
      executeEmptyBulkOperation(modelCase)
      assertSeedInserted(
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
          Terminal.EXECUTE -> assertThat(builder.execute()).isFalse()
          Terminal.OBSERVE -> builder
            .observe()
            .blockingAwait()
        }
      }
      Operation.UPDATE -> when (terminal) {
        Terminal.EXECUTE -> assertThat(
          modelCase.executeBulkUpdate(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
        ).isFalse()
        Terminal.OBSERVE -> modelCase
          .observeBulkUpdate(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
          .blockingAwait()
      }
      Operation.PERSIST -> when (terminal) {
        Terminal.EXECUTE -> assertThat(
          modelCase.executeBulkPersist(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
        ).isFalse()
        Terminal.OBSERVE -> modelCase
          .observeBulkPersist(
            values = emptyList(),
            conflictAlgorithm = conflictAlgorithm
          )
          .blockingAwait()
      }
      Operation.DELETE -> {
        check(modelCase is BulkDeleteModelCase<*>)
        val deletedCount = when (terminal) {
          Terminal.EXECUTE -> modelCase.executeBulkDelete(values = emptyList())
          Terminal.OBSERVE -> modelCase
            .observeBulkDelete(values = emptyList())
            .blockingGet()
        }
        assertThat(deletedCount).isEqualTo(0)
      }
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}: {1}, {2}, conflict={3}")
    fun cases() = ModelCatalog.emptyBulkPersistCases.flatMap { modelCase ->
      Operation.entries.flatMap { operation ->
        Terminal.entries.flatMap { terminal ->
          val conflicts = when (operation) {
            Operation.DELETE -> listOf(CONFLICT_NONE)
            else -> listOf(CONFLICT_NONE, CONFLICT_IGNORE)
          }
          conflicts.map { conflict ->
            arrayOf(modelCase, operation, terminal, conflict)
          }
        }
      }
    }
  }
}
