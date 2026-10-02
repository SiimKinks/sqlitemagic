package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SuccessfulBulkUpdateTest(
  private val modelCase: BulkUpdateModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeUpdatesAndReadsBack() {
    captureUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeUpdatesAndReadsBack() {
    captureUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> captureUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) = when (modelCase) {
    is RecursiveBulkUpdateModelCase<*> -> captureRecursiveUpdate(
      modelCase = modelCase,
      terminal = terminal
    )
    else -> captureDirectUpdate(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> captureDirectUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(3, init = modelCase::newValue)
    values.forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
      }
    }
    val persistedValues = captureRows(table = modelCase.table)
    assertThat(persistedValues).hasSize(values.size)
    val updatedValues = persistedValues.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 4
      )
    }
    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = updatedValues)
      ).isTrue()
      OperationTerminal.OBSERVE -> modelCase
        .observeBulkUpdate(values = updatedValues)
        .blockingAwait()
    }
    val actual = captureRows(table = modelCase.table)
    assertThat(actual).hasSize(persistedValues.size)
    val expectedValues = updatedValues.map(modelCase::expectedAfterUpdate)
    assertThat(actual)
      .containsExactlyElementsIn(expectedValues)
  }

  private fun <T> captureRecursiveUpdate(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(3, init = modelCase::newValue)
    values.forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
      }
    }
    val persistedValues = captureRows(table = modelCase.table)
    assertThat(persistedValues).hasSize(values.size)
    val updatedValues = persistedValues.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 4
      )
    }
    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = updatedValues)
      ).isTrue()
      OperationTerminal.OBSERVE -> modelCase
        .observeBulkUpdate(values = updatedValues)
        .blockingAwait()
    }
    val actual = captureRows(table = modelCase.table)
    assertThat(actual).hasSize(persistedValues.size)
    val expectedValues = updatedValues.map(modelCase::expectedAfterUpdate)
    assertThat(actual)
      .containsExactlyElementsIn(expectedValues)
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = expectedValues.flatMap(modelCase::relatedValues)
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateCases
  }
}
