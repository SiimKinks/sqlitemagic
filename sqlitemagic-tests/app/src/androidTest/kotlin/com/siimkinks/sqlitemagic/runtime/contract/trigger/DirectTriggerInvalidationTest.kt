package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeInsertsAndInvalidatesOnce() {
    assertInsert(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeInsertsAndInvalidatesOnce() {
    assertInsert(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeUpdatesAndInvalidatesOnce() {
    assertUpdate(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeUpdatesAndInvalidatesOnce() {
    assertUpdate(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executePersistsMissingRowAndInvalidatesOnce() {
    assertPersistInsert(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observePersistsMissingRowAndInvalidatesOnce() {
    assertPersistInsert(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executePersistsExistingRowAndInvalidatesOnce() {
    assertPersistUpdate(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observePersistsExistingRowAndInvalidatesOnce() {
    assertPersistUpdate(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeDeletesSingleRowAndInvalidatesOnce() {
    assertDelete(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeDeletesSingleRowAndInvalidatesOnce() {
    assertDelete(terminal = OperationTerminal.OBSERVE)
  }

  @Test
  fun executeDeletesTableAndInvalidatesOnce() {
    assertTableDelete(terminal = OperationTerminal.EXECUTE)
  }

  @Test
  fun observeDeletesTableAndInvalidatesOnce() {
    assertTableDelete(terminal = OperationTerminal.OBSERVE)
  }

  private fun assertInsert(terminal: OperationTerminal) {
    assertInsertForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertInsertForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase
            .insert(value = value)
            .execute()
          OperationTerminal.OBSERVE -> modelCase
            .insert(value = value)
            .observe()
            .blockingGet()
        }
      },
      expected = { result ->
        val inserted = insertedResult(
          result = result,
          modelName = modelCase.name
        )
        modelCase.verifyAfterInsert(
          value = value,
          result = inserted
        )
        DatabaseSnapshot(
          parents = listOf(
            modelCase.expectedAfterInsert(
              value = value,
              result = inserted
            )
          )
        )
      }
    )
  }

  private fun assertUpdate(terminal: OperationTerminal) {
    assertUpdateForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertUpdateForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val persisted = before.parents.single()
    val updatedValue = modelCase.updatedValue(
      value = persisted,
      sequence = 2
    )
    val expectedValue = modelCase.expectedAfterUpdate(value = updatedValue)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeUpdate(value = updatedValue)
          OperationTerminal.OBSERVE -> {
            modelCase
              .observeUpdate(value = updatedValue)
              .blockingAwait()
            true
          }
        }
      },
      expected = { result ->
        assertThat(result).isTrue()
        DatabaseSnapshot(parents = listOf(expectedValue))
      }
    )
  }

  private fun assertPersistInsert(terminal: OperationTerminal) {
    assertPersistInsertForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertPersistInsertForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executePersist(value = value)
          OperationTerminal.OBSERVE -> modelCase
            .observePersist(value = value)
            .blockingGet()
        }
      },
      expected = { result ->
        val inserted = when (result) {
          is EntityPersistResult.Inserted -> result
          EntityPersistResult.Updated -> throw AssertionError(
            "Persist unexpectedly updated for ${modelCase.name}"
          )
          EntityPersistResult.Ignored -> throw AssertionError(
            "Persist was ignored for ${modelCase.name}"
          )
        }
        val insertResult = EntityInsertResult.Inserted(rowId = inserted.rowId)
        modelCase.verifyAfterInsert(
          value = value,
          result = insertResult
        )
        DatabaseSnapshot(
          parents = listOf(
            modelCase.expectedAfterInsert(
              value = value,
              result = insertResult
            )
          )
        )
      }
    )
  }

  private fun assertPersistUpdate(terminal: OperationTerminal) {
    assertPersistUpdateForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertPersistUpdateForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValue = modelCase.updatedValue(
      value = before.parents.single(),
      sequence = 2
    )
    val expectedValue = modelCase.expectedAfterUpdate(value = updatedValue)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executePersist(value = updatedValue)
          OperationTerminal.OBSERVE -> modelCase
            .observePersist(value = updatedValue)
            .blockingGet()
        }
      },
      expected = { result ->
        assertThat(result).isEqualTo(EntityPersistResult.Updated)
        DatabaseSnapshot(parents = listOf(expectedValue))
      }
    )
  }

  private fun assertDelete(terminal: OperationTerminal) {
    assertDeleteForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertDeleteForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val expected = DatabaseSnapshot(
      parents = before.parents.drop(1)
    )
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeDelete(value = before.parents.first())
          OperationTerminal.OBSERVE -> modelCase
            .observeDelete(value = before.parents.first())
            .blockingGet()
        }
      },
      expected = { result ->
        assertThat(result).isEqualTo(1)
        expected
      }
    )
  }

  private fun assertTableDelete(terminal: OperationTerminal) {
    assertTableDeleteForCase(
      modelCase = modelCase,
      terminal = terminal
    )
  }

  private fun <T> assertTableDeleteForCase(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 3
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val expected = DatabaseSnapshot<T>(parents = emptyList())
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          OperationTerminal.EXECUTE -> modelCase.executeTableDelete()
          OperationTerminal.OBSERVE -> modelCase
            .observeTableDelete()
            .blockingGet()
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
