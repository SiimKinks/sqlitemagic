package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SuccessfulSinglePersistTest(
  private val modelCase: PersistModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executePersistsMissingRowAndReadsBack() {
    captureMissingRow(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observePersistsMissingRowAndReadsBack() {
    captureMissingRow(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executePersistsExistingRowAndReadsBack() {
    captureExistingRow(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observePersistsExistingRowAndReadsBack() {
    captureExistingRow(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> captureMissingRow(
    modelCase: PersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val result = when (terminal) {
      OperationTerminal.EXECUTE -> modelCase.executePersist(value = value)
      OperationTerminal.OBSERVE -> modelCase
        .observePersist(value = value)
        .blockingGet()
    }
    val inserted = when (result) {
      is EntityPersistResult.Inserted -> result
      EntityPersistResult.Updated -> throw AssertionError("Persist unexpectedly updated for ${modelCase.name}")
      EntityPersistResult.Ignored -> throw AssertionError("Persist was ignored for ${modelCase.name}")
    }
    when (modelCase.rowIdExpectation) {
      InsertRowIdExpectation.PRESENT -> assertThat(inserted.rowId)
        .isNotNull()
      InsertRowIdExpectation.ABSENT -> assertThat(inserted.rowId)
        .isNull()
    }
    val insertResult = EntityInsertResult.Inserted(rowId = inserted.rowId)
    modelCase.verifyAfterInsert(
      value = value,
      result = insertResult
    )
    val actual = captureRows(table = modelCase.table)
    val expected = modelCase.expectedAfterInsert(
      value = value,
      result = insertResult
    )
    assertThat(actual)
      .containsExactly(expected)
  }

  private fun <T> captureExistingRow(
    modelCase: PersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val insertedValue = modelCase.newValue(sequence = 1)
    val insertedResult = modelCase
      .insert(value = insertedValue)
      .execute()
    when (insertedResult) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
    }
    val persistedValue = captureRows(table = modelCase.table).single()
    val updatedValue = modelCase.updatedValue(
      value = persistedValue,
      sequence = 2
    )
    val result = when (terminal) {
      OperationTerminal.EXECUTE -> modelCase.executePersist(value = updatedValue)
      OperationTerminal.OBSERVE -> modelCase
        .observePersist(value = updatedValue)
        .blockingGet()
    }
    assertThat(result)
      .isEqualTo(EntityPersistResult.Updated)
    val actual = captureRows(table = modelCase.table)
    assertThat(actual)
      .containsExactly(modelCase.expectedAfterUpdate(value = updatedValue))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistCases
  }
}
