package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.insertedPersistResult
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeInsertsAndInvalidatesOnce() = assertInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeInsertsAndInvalidatesOnce() = assertInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeUpdatesAndInvalidatesOnce() = assertUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeUpdatesAndInvalidatesOnce() = assertUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executePersistsMissingRowAndInvalidatesOnce() = assertPersistInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistsMissingRowAndInvalidatesOnce() = assertPersistInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executePersistsExistingRowAndInvalidatesOnce() = assertPersistUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistsExistingRowAndInvalidatesOnce() = assertPersistUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeDeletesSingleRowAndInvalidatesOnce() = assertDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeDeletesSingleRowAndInvalidatesOnce() = assertDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeDeletesTableAndInvalidatesOnce() = assertTableDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeDeletesTableAndInvalidatesOnce() = assertTableDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertInsert(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        terminal.select(
          execute = {
            modelCase
              .insert(value = value)
              .execute()
          },
          observe = {
            modelCase
              .insert(value = value)
              .observe()
          }
        )
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

  private fun <T> assertUpdate(
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
          EXECUTE -> modelCase.executeUpdate(value = updatedValue)
          OBSERVE -> {
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

  private fun <T> assertPersistInsert(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      modelCase = modelCase,
      before = before,
      operation = {
        terminal.select(
          execute = { modelCase.executePersist(value = value) },
          observe = { modelCase.observePersist(value = value) }
        )
      },
      expected = { result ->
        val inserted = insertedPersistResult(
          result = result,
          modelName = modelCase.name
        )
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

  private fun <T> assertPersistUpdate(
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
        terminal.select(
          execute = { modelCase.executePersist(value = updatedValue) },
          observe = { modelCase.observePersist(value = updatedValue) }
        )
      },
      expected = { result ->
        assertThat(result).isEqualTo(EntityPersistResult.Updated)
        DatabaseSnapshot(parents = listOf(expectedValue))
      }
    )
  }

  private fun <T> assertDelete(
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
        terminal.select(
          execute = { modelCase.executeDelete(value = before.parents.first()) },
          observe = { modelCase.observeDelete(value = before.parents.first()) }
        )
      },
      expected = { result ->
        assertThat(result).isEqualTo(1)
        expected
      }
    )
  }

  private fun <T> assertTableDelete(
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
        terminal.select(
          execute = modelCase::executeTableDelete,
          observe = modelCase::observeTableDelete
        )
      },
      expected = { result ->
        assertThat(result).isEqualTo(3)
        expected
      }
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerCases
  }
}
