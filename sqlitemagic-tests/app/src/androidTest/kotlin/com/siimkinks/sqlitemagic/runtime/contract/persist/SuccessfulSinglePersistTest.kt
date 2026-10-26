package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedPersistResult
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulSinglePersistTest(
  private val modelCase: PersistModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executePersistsMissingRowAndReadsBack() = captureMissingRow(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistsMissingRowAndReadsBack() = captureMissingRow(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executePersistsExistingRowAndReadsBack() = captureExistingRow(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistsExistingRowAndReadsBack() = captureExistingRow(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> captureMissingRow(
    modelCase: PersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val result = terminal.select(
      execute = { modelCase.executePersist(value = value) },
      observe = { modelCase.observePersist(value = value) }
    )
    val inserted = insertedPersistResult(
      result = result,
      modelName = modelCase.name
    )
    modelCase.rowIdExpectation.assertRowId(inserted.rowId)
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
    insertedResult(
      result = modelCase
        .insert(value = insertedValue)
        .execute(),
      modelName = modelCase.name
    )
    val persistedValue = captureRows(table = modelCase.table).single()
    val updatedValue = modelCase.updatedValue(
      value = persistedValue,
      sequence = 2
    )
    val result = terminal.select(
      execute = { modelCase.executePersist(value = updatedValue) },
      observe = { modelCase.observePersist(value = updatedValue) }
    )
    assertThat(result)
      .isEqualTo(EntityPersistResult.Updated)
    val actual = captureRows(table = modelCase.table)
    assertThat(actual)
      .containsExactly(modelCase.expectedAfterUpdate(value = updatedValue))
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistCases
  }
}
