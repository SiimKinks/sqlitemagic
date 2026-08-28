package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SuccessfulBulkPersistTest(
  private val modelCase: BulkPersistModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executePersistsExistingAndMissingRowsAndReadsBack() {
    capturePersist(
      modelCase = modelCase,
      terminal = BulkPersistTerminal.EXECUTE
    )
  }

  @Test
  fun observePersistsExistingAndMissingRowsAndReadsBack() {
    capturePersist(
      modelCase = modelCase,
      terminal = BulkPersistTerminal.OBSERVE
    )
  }

  private fun <T> capturePersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: BulkPersistTerminal
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
    terminal: BulkPersistTerminal
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
      BulkPersistTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = values)
      ).isTrue()
      BulkPersistTerminal.OBSERVE -> modelCase
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
    terminal: BulkPersistTerminal
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
      BulkPersistTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = values)
      ).isTrue()
      BulkPersistTerminal.OBSERVE -> modelCase
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
    assertThat(captureRows(table = modelCase.relatedTable))
      .containsExactlyElementsIn(expectedParents.flatMap(modelCase::relatedValues))
  }

  private fun <T> seedRows(
    modelCase: BulkPersistModelCase<T>,
    count: Int
  ) = List(size = count, init = modelCase::newValue)
    .forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
      }
    }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private enum class BulkPersistTerminal {
    EXECUTE,
    OBSERVE
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkPersistCases
  }
}
