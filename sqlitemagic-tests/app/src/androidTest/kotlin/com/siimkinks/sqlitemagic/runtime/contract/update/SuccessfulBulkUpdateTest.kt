package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulBulkUpdateTest(
  private val modelCase: BulkUpdateModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeUpdatesAndReadsBack() = captureUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeUpdatesAndReadsBack() = captureUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> captureUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) {
    val persistedValues = seedRows(
      modelCase = modelCase,
      count = 3
    )
    assertThat(persistedValues).hasSize(3)
    val updatedValues = persistedValues.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 4
      )
    }
    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = updatedValues)
      ).isTrue()
      OBSERVE -> modelCase
        .observeBulkUpdate(
          adapter = RxJava2Adapter,
          values = updatedValues
        )
        .await()
    }
    val actual = captureRows(table = modelCase.table)
    assertThat(actual).hasSize(persistedValues.size)
    val expectedValues = updatedValues.map(modelCase::expectedAfterUpdate)
    assertThat(actual)
      .containsExactlyElementsIn(expectedValues)
    if (modelCase is RecursiveBulkUpdateModelCase<T>) {
      assertRowsIgnoringOrder(
        table = modelCase.relatedTable,
        expected = expectedValues.flatMap(modelCase::relatedValues)
      )
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateCases
  }
}
