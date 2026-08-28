package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectBulkUpdateConflictTest(
  private val modelCase: BulkUpdateConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithDefaultConflictThrowsSQLiteConstraintExceptionAndRestoresSnapshot() {
    assertDefaultConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithDefaultConflictEmitsSQLiteConstraintExceptionAndRestoresSnapshot() {
    assertDefaultConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithConflictIgnoreReturnsTrueAndUpdatesOnlyNonConflictingRows() {
    assertConflictIgnoreExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithConflictIgnoreCompletesAndUpdatesOnlyNonConflictingRows() {
    assertConflictIgnoreObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithAllConflictsAndConflictIgnoreReturnsFalseAndRestoresSnapshot() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllConflictsAndConflictIgnoreCompletesAndRestoresSnapshot() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertDefaultConflictExecute(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    assertThrows(SQLiteConstraintException::class.java) {
      bulkUpdate(
        modelCase = modelCase,
        values = scenario.values
      ).execute()
    }
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertDefaultConflictObserve(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    bulkUpdate(
      modelCase = modelCase,
      values = scenario.values
    )
      .observe()
      .test()
      .assertFailure(SQLiteConstraintException::class.java)
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertConflictIgnoreExecute(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    assertThat(
      bulkUpdate(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isTrue()
    assertRows(
      table = modelCase.table,
      expected = scenario.afterMixedConflict
    )
  }

  private fun <T> assertConflictIgnoreObserve(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    bulkUpdate(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertRows(
      table = modelCase.table,
      expected = scenario.afterMixedConflict
    )
  }

  private fun <T> assertAllConflictsExecute(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = allConflictScenario(modelCase = modelCase)

    assertThat(
      bulkUpdate(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isFalse()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertAllConflictsObserve(
    modelCase: BulkUpdateConflictModelCase<T>
  ) {
    val scenario = allConflictScenario(modelCase = modelCase)

    bulkUpdate(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> bulkUpdate(
    modelCase: BulkUpdateConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkUpdate(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> threeRowScenario(
    modelCase: BulkUpdateConflictModelCase<T>
  ): BulkUpdateScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 3
    )
    val values = listOf(
      modelCase.updatedValue(
        value = before[0],
        sequence = 4
      ),
      modelCase.valueWithConflict(
        existing = before[1],
        conflicting = before[2],
        sequence = 5
      ),
      modelCase.updatedValue(
        value = before[2],
        sequence = 6
      )
    )
    return BulkUpdateScenario(
      before = before,
      values = values,
      afterMixedConflict = listOf(
        values[0],
        before[1],
        values[2]
      )
    )
  }

  private fun <T> allConflictScenario(
    modelCase: BulkUpdateConflictModelCase<T>
  ): BulkUpdateScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return BulkUpdateScenario(
      before = before,
      values = listOf(
        modelCase.valueWithConflict(
          existing = before[0],
          conflicting = before[1],
          sequence = 3
        ),
        modelCase.valueWithConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 4
        )
      ),
      afterMixedConflict = before
    )
  }

  private fun <T> seedRows(
    modelCase: BulkUpdateConflictModelCase<T>,
    count: Int
  ): List<T> {
    List(size = count, init = modelCase::newValue).forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
      }
    }
    return captureRows(table = modelCase.table)
  }

  private fun <T> assertRows(
    table: Table<T>,
    expected: List<T>
  ) = assertThat(captureRows(table = table))
    .containsExactlyElementsIn(expected)
    .inOrder()

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private data class BulkUpdateScenario<T>(
    val before: List<T>,
    val values: List<T>,
    val afterMixedConflict: List<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateConflictCases
  }
}
