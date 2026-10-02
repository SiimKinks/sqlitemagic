package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SuccessfulSingleUpdateTest(
  private val modelCase: UpdateModelCase<*>
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
    modelCase: UpdateModelCase<T>,
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
    val success = when (terminal) {
      OperationTerminal.EXECUTE -> modelCase.executeUpdate(value = updatedValue)
      OperationTerminal.OBSERVE -> {
        modelCase
          .observeUpdate(value = updatedValue)
          .blockingAwait()
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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateCases
  }
}
