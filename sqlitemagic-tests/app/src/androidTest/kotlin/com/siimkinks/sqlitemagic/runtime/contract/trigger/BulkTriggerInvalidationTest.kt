package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class BulkTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeBulkInsertsAndInvalidatesOnce() = assertBulkInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkInsertsAndInvalidatesOnce() = assertBulkInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkUpdatesAndInvalidatesOnce() = assertBulkUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkUpdatesAndInvalidatesOnce() = assertBulkUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeMixedBulkPersistsAndInvalidatesOnce() = assertBulkPersist(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeMixedBulkPersistsAndInvalidatesOnce() = assertBulkPersist(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkDeletesAndInvalidatesOnce() = assertBulkDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkDeletesAndInvalidatesOnce() = assertBulkDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertBulkInsert(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(
      size = 3,
      init = modelCase::newValue
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    assertOneQueryInvalidation(
      adapter = adapter,
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          EXECUTE -> modelCase
            .bulkInsert(values = values)
            .execute()
          OBSERVE -> {
            adapter
              .observe(modelCase.bulkInsert(values = values))
              .await()
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

  private fun <T> assertBulkUpdate(
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
      adapter = adapter,
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          EXECUTE -> modelCase.executeBulkUpdate(values = updatedValues)
          OBSERVE -> {
            modelCase
              .observeBulkUpdate(
                adapter = adapter,
                values = updatedValues
              )
              .await()
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

  private fun <T> assertBulkPersist(
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
      adapter = adapter,
      modelCase = modelCase,
      before = before,
      operation = {
        when (terminal) {
          EXECUTE -> modelCase.executeBulkPersist(values = values)
          OBSERVE -> {
            modelCase
              .observeBulkPersist(
                adapter = adapter,
                values = values
              )
              .await()
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

  private fun <T> assertBulkDelete(
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
      adapter = adapter,
      modelCase = modelCase,
      before = before,
      operation = {
        terminal.select(
          execute = { modelCase.executeBulkDelete(values = values) },
          observe = {
            modelCase.observeBulkDelete(
              adapter = adapter,
              values = values
            )
          }
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
    @Parameters(name = "{0} {1}")
    fun modelCases() = withReactiveAdapters(ModelCatalog.triggerCases)
  }
}
