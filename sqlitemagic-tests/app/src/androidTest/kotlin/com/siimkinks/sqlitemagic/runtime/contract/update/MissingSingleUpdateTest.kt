package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.UpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.seed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class MissingSingleUpdateTest(
  private val modelCase: UpdateModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithMissingRowAndDefaultAlgorithmReturnsFalse() = assertMissingRow(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithMissingRowAndDefaultAlgorithmEmitsOperationFailedException() = assertMissingRow(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithMissingRowAndConflictIgnoreReturnsFalse() = assertMissingRow(
    modelCase = modelCase,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithMissingRowAndConflictIgnoreCompletes() = assertMissingRow(
    modelCase = modelCase,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  private fun <T> assertMissingRow(
    modelCase: UpdateModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val value = missingValue(modelCase)
    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeUpdate(
          value = value,
          conflictAlgorithm = conflictAlgorithm
        )
      ).isFalse()
      OBSERVE -> {
        val observer = modelCase
          .observeUpdate(
            adapter = RxJava2Adapter,
            value = value,
            conflictAlgorithm = conflictAlgorithm
          )
          .record()
        when (conflictAlgorithm) {
          null -> observer.assertFailure(OperationFailedException::class::isInstance)
          else -> observer.assertComplete()
        }
      }
    }
    assertEmptyState(modelCase = modelCase)
  }

  private fun <T> missingValue(modelCase: UpdateModelCase<T>): T {
    modelCase.seed()
    val persisted = captureRows(table = modelCase.table)
      .single()
    SqliteMagic
      .getDefaultConnection()
      .clearData()
    return persisted
  }

  private fun <T> assertEmptyState(modelCase: UpdateModelCase<T>) {
    assertThat(captureRows(table = modelCase.table)).isEmpty()
    if (modelCase is RecursiveBulkUpdateModelCase<*>) {
      assertThat(captureRows(table = modelCase.relatedTable)).isEmpty()
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateCases
  }
}
