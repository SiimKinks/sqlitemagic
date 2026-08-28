package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.UpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MissingSingleUpdateTest(
  private val modelCase: UpdateModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithMissingRowAndDefaultAlgorithmReturnsFalse() {
    assertMissingRow(
      modelCase = modelCase,
      terminal = UpdateTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithMissingRowAndDefaultAlgorithmEmitsOperationFailedException() {
    assertMissingRow(
      modelCase = modelCase,
      terminal = UpdateTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithMissingRowAndConflictIgnoreReturnsFalse() {
    assertMissingRow(
      modelCase = modelCase,
      terminal = UpdateTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithMissingRowAndConflictIgnoreCompletes() {
    assertMissingRow(
      modelCase = modelCase,
      terminal = UpdateTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  private fun <T> assertMissingRow(
    modelCase: UpdateModelCase<T>,
    terminal: UpdateTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val value = missingValue(modelCase)
    when (terminal) {
      UpdateTerminal.EXECUTE -> assertThat(
        when (conflictAlgorithm) {
          null -> modelCase.executeUpdate(value = value)
          else -> modelCase.executeUpdate(
            value = value,
            conflictAlgorithm = conflictAlgorithm
          )
        }
      ).isFalse()
      UpdateTerminal.OBSERVE -> when (conflictAlgorithm) {
        null -> modelCase
          .observeUpdate(value = value)
          .test()
          .assertFailure(OperationFailedException::class.java)
        else -> modelCase
          .observeUpdate(
            value = value,
            conflictAlgorithm = conflictAlgorithm
          )
          .test()
          .assertComplete()
      }
    }
    assertEmptyState(modelCase = modelCase)
  }

  private fun <T> missingValue(modelCase: UpdateModelCase<T>): T {
    val seed = modelCase.newValue(sequence = 1)
    when (modelCase.insert(value = seed).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
    }
    val persisted = Select
      .from(modelCase.table)
      .queryDeep()
      .execute()
      .single()
    SqliteMagic
      .getDefaultConnection()
      .clearData()
    return persisted
  }

  private fun <T> assertEmptyState(modelCase: UpdateModelCase<T>) {
    assertThat(captureRows(table = modelCase.table)).isEmpty()
    when (modelCase) {
      is RecursiveBulkUpdateModelCase<*> -> assertThat(captureRows(table = modelCase.relatedTable)).isEmpty()
      else -> Unit
    }
  }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private enum class UpdateTerminal {
    EXECUTE,
    OBSERVE
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateCases
  }
}
