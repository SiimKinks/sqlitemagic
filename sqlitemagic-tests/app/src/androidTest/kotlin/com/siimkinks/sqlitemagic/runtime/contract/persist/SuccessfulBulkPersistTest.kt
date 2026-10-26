package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulBulkPersistTest(
  private val modelCase: BulkPersistModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executePersistsExistingAndMissingRowsAndReadsBack() = capturePersist(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistsExistingAndMissingRowsAndReadsBack() = capturePersist(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> capturePersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: OperationTerminal
  ) = when (modelCase) {
    is RecursiveBulkPersistModelCase<*> -> captureRecursivePersist(
      modelCase = modelCase,
      terminal = terminal
    )
    else -> captureDirectPersist(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> captureDirectPersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val persistedValues = captureRows(table = modelCase.table)
    val updatedValues = persistedValues.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 3
      )
    }
    val insertedValue = modelCase.newValue(sequence = 5)
    val values = updatedValues + insertedValue

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = values)
      ).isTrue()
      OBSERVE -> modelCase
        .observeBulkPersist(values = values)
        .blockingAwait()
    }

    val actual = captureRows(table = modelCase.table)
    val expectedCandidates = updatedValues.map(modelCase::expectedAfterUpdate) + insertedValue
    val expected = modelCase.expectedAfterBulkInsert(
      values = expectedCandidates,
      actual = actual
    )
    assertThat(actual)
      .hasSize(3)
    assertThat(actual)
      .containsExactlyElementsIn(expected)
  }

  private fun <T> captureRecursivePersist(
    modelCase: RecursiveBulkPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val persistedValues = captureRows(table = modelCase.table)
    val updatedValues = persistedValues.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 3
      )
    }
    val insertedValue = modelCase.newValue(sequence = 5)
    val values = updatedValues + insertedValue

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = values)
      ).isTrue()
      OBSERVE -> modelCase
        .observeBulkPersist(values = values)
        .blockingAwait()
    }

    val actual = captureRows(table = modelCase.table)
    val expectedCandidates = updatedValues.map(modelCase::expectedAfterUpdate) + insertedValue
    val expectedParents = modelCase.expectedAfterBulkInsert(
      values = expectedCandidates,
      actual = actual
    )
    assertThat(actual)
      .hasSize(3)
    assertThat(actual)
      .containsExactlyElementsIn(expectedParents)
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = relatedRows(
        modelCase = modelCase,
        values = expectedParents
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkPersistCases
  }
}
