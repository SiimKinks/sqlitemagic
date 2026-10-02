package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BulkTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeBulkInsertsAndInvalidatesOnce() {
    assertBulkInsert(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeBulkInsertsAndInvalidatesOnce() {
    assertBulkInsert(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeBulkUpdatesAndInvalidatesOnce() {
    assertBulkUpdate(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeBulkUpdatesAndInvalidatesOnce() {
    assertBulkUpdate(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeMixedBulkPersistsAndInvalidatesOnce() {
    assertBulkPersist(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeMixedBulkPersistsAndInvalidatesOnce() {
    assertBulkPersist(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeBulkDeletesAndInvalidatesOnce() {
    assertBulkDelete(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeBulkDeletesAndInvalidatesOnce() {
    assertBulkDelete(terminal = OperationTerminal.OBSERVE)
  }

  private fun assertBulkInsert(terminal: OperationTerminal) {
    assertBulkInsertForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertBulkInsertForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(
      size = 3,
      init = modelCase::newValue
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase
            .bulkInsert(values = values)
            .execute()
          OperationTerminal.OBSERVE -> {
            modelCase
              .bulkInsert(values = values)
              .observe()
              .blockingAwait()
            true
          }
        }
      },
      expected = { result ->
        assertThat(result).isTrue()
        val actual = captureDatabaseSnapshot(modelCase = modelCase)
        DatabaseSnapshot(
          parents = modelCase.expectedAfterBulkInsert(
            values = values,
            actual = actual.parents
          )
        )
      }
    )
  }

  private fun assertBulkUpdate(terminal: OperationTerminal) {
    assertBulkUpdateForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertBulkUpdateForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 3
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValues = before.parents.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 4
      )
    }
    val expectedValues = updatedValues.map(modelCase::expectedAfterUpdate)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeBulkUpdate(values = updatedValues)
          OperationTerminal.OBSERVE -> {
            modelCase
              .observeBulkUpdate(values = updatedValues)
              .blockingAwait()
            true
          }
        }
      },
      expected = { result ->
        assertThat(result).isTrue()
        DatabaseSnapshot(parents = expectedValues)
      }
    )
  }

  private fun assertBulkPersist(terminal: OperationTerminal) {
    assertBulkPersistForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertBulkPersistForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValues = before.parents.mapIndexed { index, value ->
      modelCase.updatedValue(
        value = value,
        sequence = index + 3
      )
    }
    val insertedValue = modelCase.newValue(sequence = 5)
    val values = updatedValues + insertedValue
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeBulkPersist(values = values)
          OperationTerminal.OBSERVE -> {
            modelCase
              .observeBulkPersist(values = values)
              .blockingAwait()
            true
          }
        }
      },
      expected = { result ->
        assertThat(result).isTrue()
        val actual = captureRows(table = modelCase.table)
        val expectedCandidates = updatedValues.map(modelCase::expectedAfterUpdate) + insertedValue
        DatabaseSnapshot(
          parents = modelCase.expectedAfterBulkInsert(
            values = expectedCandidates,
            actual = actual
          )
        )
      }
    )
  }

  private fun assertBulkDelete(terminal: OperationTerminal) {
    assertBulkDeleteForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertBulkDeleteForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 3
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val values = before.parents
    val expected = DatabaseSnapshot<T>(parents = emptyList())
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeBulkDelete(values = values)
          OperationTerminal.OBSERVE -> modelCase.observeBulkDelete(values = values).blockingGet()
        }
      },
      expected = { result ->
        assertThat(result).isEqualTo(3)
        expected
      }
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerCases
  }
}
