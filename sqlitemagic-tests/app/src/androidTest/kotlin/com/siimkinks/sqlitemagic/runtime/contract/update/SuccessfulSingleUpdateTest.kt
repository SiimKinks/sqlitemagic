package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulSingleUpdateTest(
  private val modelCase: UpdateModelCase<*>
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
    modelCase: UpdateModelCase<T>,
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
    val success = when (terminal) {
      EXECUTE -> modelCase.executeUpdate(value = updatedValue)
      OBSERVE -> {
        modelCase
          .observeUpdate(
            adapter = RxJava2Adapter,
            value = updatedValue
          )
          .await()
        true
      }
    }
    assertThat(success).isTrue()
    val actual = captureRows(table = modelCase.table)
    assertThat(actual)
      .containsExactly(modelCase.expectedAfterUpdate(value = updatedValue))
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateCases
  }
}
