package com.siimkinks.sqlitemagic.runtime.contract.transaction

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.coroutines.withTransaction
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Ignored
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursiveInsertConflictCases
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursiveUpdateConflictCases
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.uniqueInsertCases
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.seed
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class ContainedOperationTransactionTest(
  private val scenario: Scenario,
  private val transaction: TransactionKind
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  enum class TransactionKind { BLOCKING, COROUTINE }

  class Scenario(
    private val label: String,
    val tables: List<Table<*>>,
    val prepare: () -> Steps
  ) {
    override fun toString() = label
  }

  class Steps(
    val writeBefore: () -> Unit,
    val operation: () -> Unit
  )

  @Test
  fun operationThatAppliesNothingLeavesTheEnclosingTransactionToCommit() {
    val steps = scenario.prepare()
    var expected = emptyList<List<Any?>>()
    val body = {
      steps.writeBefore()
      expected = scenario.tables.map { table -> captureRows(table = table) }
      steps.operation()
    }
    when (transaction) {
      TransactionKind.BLOCKING -> inTransaction { body() }
      TransactionKind.COROUTINE -> runBlocking { withTransaction { body() } }
    }

    assertThat(expected).isNotEmpty()
    scenario.tables.zip(expected).forEach { (table, rows) ->
      assertRowsIgnoringOrder(
        table = table,
        expected = rows
      )
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}, {1}")
    fun cases() = scenarios().flatMap { scenario ->
      TransactionKind.entries.map { transaction -> arrayOf(scenario, transaction) }
    }

    private fun scenarios() = uniqueInsertCases.map { modelCase -> ignoredBulkInsert(modelCase) } +
        recursiveInsertConflictCases.flatMap { modelCase ->
          RecursiveConflictTarget.entries.flatMap { conflict ->
            listOf(
              ignoredRecursiveInsert(
                modelCase = modelCase,
                conflict = conflict
              ),
              failedRecursiveInsert(
                modelCase = modelCase,
                conflict = conflict
              )
            )
          }
        } +
        recursiveUpdateConflictCases.map { modelCase -> missingRecursiveUpdate(modelCase) }

    private fun <T> ignoredBulkInsert(modelCase: UniqueInsertModelCase<T>) = Scenario(
      label = "${modelCase.name}: bulk insert whose every row is ignored",
      tables = listOf(modelCase.table),
      prepare = {
        val existing = modelCase.seed()
        Steps(
          writeBefore = { modelCase.insertNew() },
          operation = {
            assertThat(
              modelCase
                .bulkInsert(
                  values = listOf(
                    modelCase.conflictingValue(
                      existing = existing,
                      sequence = 2
                    )
                  )
                )
                .conflictAlgorithm(CONFLICT_IGNORE)
                .execute()
            ).isFalse()
          }
        )
      }
    )

    private fun <T> ignoredRecursiveInsert(
      modelCase: RecursiveInsertConflictModelCase<T>,
      conflict: RecursiveConflictTarget
    ) = Scenario(
      label = "${modelCase.name}: ignored insert with a $conflict conflict",
      tables = listOf(modelCase.table, modelCase.relatedTable),
      prepare = {
        val existing = modelCase.seed()
        Steps(
          writeBefore = { modelCase.insertNew() },
          operation = {
            assertThat(
              modelCase
                .insert(
                  value = modelCase.valueWithInsertConflict(
                    existing = existing,
                    conflict = conflict,
                    sequence = 2
                  )
                )
                .conflictAlgorithm(CONFLICT_IGNORE)
                .execute()
            ).isEqualTo(Ignored)
          }
        )
      }
    )

    private fun <T> failedRecursiveInsert(
      modelCase: RecursiveInsertConflictModelCase<T>,
      conflict: RecursiveConflictTarget
    ) = Scenario(
      label = "${modelCase.name}: caught failure of an insert with a $conflict conflict",
      tables = listOf(modelCase.table, modelCase.relatedTable),
      prepare = {
        val existing = modelCase.seed()
        Steps(
          writeBefore = { modelCase.insertNew() },
          operation = {
            assertConstraintFailure {
              modelCase
                .insert(
                  value = modelCase.valueWithInsertConflict(
                    existing = existing,
                    conflict = conflict,
                    sequence = 2
                  )
                )
                .execute()
            }
          }
        )
      }
    )

    private fun <T> missingRecursiveUpdate(modelCase: RecursiveUpdateConflictModelCase<T>) = Scenario(
      label = "${modelCase.name}: update of a missing row",
      tables = listOf(modelCase.table, modelCase.relatedTable),
      prepare = {
        val missing = modelCase.seed()
        DELETE
          .FROM(modelCase.table)
          .execute()
        Steps(
          writeBefore = { modelCase.insertNew() },
          operation = {
            assertThat(modelCase.executeUpdate(value = missing)).isFalse()
          }
        )
      }
    )

    private fun <T> InsertModelCase<T>.insertNew() = insertedResult(
      result = insert(value = newValue(sequence = 3))
          .execute(),
      modelName = name
    )
  }
}
