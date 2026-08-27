package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectBulkInsertConflictTest(
  private val modelCase: UniqueInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithDefaultConflictReturnsFalseAndRollsBackBatch() {
    assertDefaultConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithDefaultConflictEmitsOperationFailedExceptionAndRollsBackBatch() {
    assertDefaultConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithConflictIgnoreReturnsTrueAndCommitsFreshRows() {
    assertConflictIgnoreMixedExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithConflictIgnoreCompletesAndCommitsFreshRows() {
    assertConflictIgnoreMixedObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithAllConflictsReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllConflictsCompletesAndLeavesSeedUnchanged() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertDefaultConflictExecute(modelCase: UniqueInsertModelCase<T>) {
    val scenario = defaultConflictScenario(modelCase = modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .execute()
    ).isFalse()
    assertRows(
      modelCase = modelCase,
      expected = listOf(scenario.seed)
    )
  }

  private fun <T> assertDefaultConflictObserve(modelCase: UniqueInsertModelCase<T>) {
    val scenario = defaultConflictScenario(modelCase = modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertRows(
      modelCase = modelCase,
      expected = listOf(scenario.seed)
    )
  }

  private fun <T> assertConflictIgnoreMixedExecute(modelCase: UniqueInsertModelCase<T>) {
    val scenario = mixedConflictScenario(modelCase = modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isTrue()
    assertRows(
      modelCase = modelCase,
      expected = scenario.expectedRows
    )
  }

  private fun <T> assertConflictIgnoreMixedObserve(modelCase: UniqueInsertModelCase<T>) {
    val scenario = mixedConflictScenario(modelCase = modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .test()
      .assertResult()
    assertRows(
      modelCase = modelCase,
      expected = scenario.expectedRows
    )
  }

  private fun <T> assertAllConflictsExecute(modelCase: UniqueInsertModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isFalse()
    assertRows(
      modelCase = modelCase,
      expected = listOf(scenario.seed)
    )
  }

  private fun <T> assertAllConflictsObserve(modelCase: UniqueInsertModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .test()
      .assertResult()
    assertRows(
      modelCase = modelCase,
      expected = listOf(scenario.seed)
    )
  }

  private fun <T> defaultConflictScenario(
    modelCase: UniqueInsertModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      DirectBulkScenario(
        seed = seed,
        values = listOf(
          modelCase.newValue(sequence = 2),
          modelCase.conflictingValue(
            existing = seed,
            sequence = 3
          ),
          modelCase.newValue(sequence = 4)
        ),
        expectedRows = listOf(seed)
      )
    }

  private fun <T> mixedConflictScenario(
    modelCase: UniqueInsertModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      DirectBulkScenario(
        seed = seed,
        values = listOf(
          firstFresh,
          modelCase.conflictingValue(
            existing = seed,
            sequence = 3
          ),
          secondFresh
        ),
        expectedRows = listOf(seed, firstFresh, secondFresh)
      )
    }

  private fun <T> allConflictsScenario(
    modelCase: UniqueInsertModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      DirectBulkScenario(
        seed = seed,
        values = listOf(
          modelCase.conflictingValue(
            existing = seed,
            sequence = 2
          ),
          modelCase.conflictingValue(
            existing = seed,
            sequence = 3
          ),
          modelCase.conflictingValue(
            existing = seed,
            sequence = 4
          )
        ),
        expectedRows = listOf(seed)
      )
    }

  private fun <T> UniqueInsertModelCase<T>.seed(): T {
    val value = newValue(sequence = 1)
    when (insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $name")
    }
    return value
  }

  private fun <T> assertRows(
    modelCase: UniqueInsertModelCase<T>,
    expected: List<T>
  ) {
    val actual = Select
      .from(modelCase.table)
      .queryDeep()
      .execute()
    assertThat(actual)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = expected,
          actual = actual
        )
      )
  }

  private data class DirectBulkScenario<T>(
    val seed: T,
    val values: List<T>,
    val expectedRows: List<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.uniqueInsertCases
  }
}
