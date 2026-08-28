package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveBulkInsertConflictTest(
  private val modelCase: RecursiveInsertConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictReturnsFalseAndRollsBackBatch() {
    assertDefaultConflictExecute(
      modelCase = modelCase,
      scenarioFactory = ::parentConflictScenario
    )
  }

  @Test
  fun observeWithParentConflictEmitsOperationFailedExceptionAndRollsBackBatch() {
    assertDefaultConflictObserve(
      modelCase = modelCase,
      scenarioFactory = ::parentConflictScenario
    )
  }

  @Test
  fun executeWithChildConflictReturnsFalseAndRollsBackBatch() {
    assertDefaultConflictExecute(
      modelCase = modelCase,
      scenarioFactory = ::childConflictScenario
    )
  }

  @Test
  fun observeWithChildConflictEmitsOperationFailedExceptionAndRollsBackBatch() {
    assertDefaultConflictObserve(
      modelCase = modelCase,
      scenarioFactory = ::childConflictScenario
    )
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsFreshGraphs() {
    assertConflictIgnoreMixedExecute(
      modelCase = modelCase,
      scenarioFactory = ::mixedParentConflictScenario
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithFreshGraphs() {
    assertConflictIgnoreMixedObserve(
      modelCase = modelCase,
      scenarioFactory = ::mixedParentConflictScenario
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsFreshGraphs() {
    assertConflictIgnoreMixedExecute(
      modelCase = modelCase,
      scenarioFactory = ::mixedChildConflictScenario
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithFreshGraphs() {
    assertConflictIgnoreMixedObserve(
      modelCase = modelCase,
      scenarioFactory = ::mixedChildConflictScenario
    )
  }

  @Test
  fun executeWithAllParentAndChildConflictsReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllParentAndChildConflictsCompletesAndLeavesSeedUnchanged() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertDefaultConflictExecute(
    modelCase: RecursiveInsertConflictModelCase<T>,
    scenarioFactory: (RecursiveInsertConflictModelCase<T>) -> RecursiveBulkScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertDefaultConflictObserve(
    modelCase: RecursiveInsertConflictModelCase<T>,
    scenarioFactory: (RecursiveInsertConflictModelCase<T>) -> RecursiveBulkScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnoreMixedExecute(
    modelCase: RecursiveInsertConflictModelCase<T>,
    scenarioFactory: (RecursiveInsertConflictModelCase<T>) -> RecursiveBulkScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isTrue()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnoreMixedObserve(
    modelCase: RecursiveInsertConflictModelCase<T>,
    scenarioFactory: (RecursiveInsertConflictModelCase<T>) -> RecursiveBulkScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .test()
      .assertResult()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflictsExecute(modelCase: RecursiveInsertConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    assertThat(
      modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflictsObserve(modelCase: RecursiveInsertConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    modelCase
      .bulkInsert(scenario.values)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .test()
      .assertResult()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> parentConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkScenario(
        values = listOf(
          modelCase.newValue(sequence = 2),
          modelCase.valueWithParentConflict(
            existing = seed,
            sequence = 3
          ),
          modelCase.newValue(sequence = 4)
        ),
        expectedParents = listOf(seed),
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = listOf(seed)
        )
      )
    }

  private fun <T> childConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkScenario(
        values = listOf(
          modelCase.newValue(sequence = 2),
          modelCase.valueWithChildConflict(
            existing = seed,
            sequence = 3
          ),
          modelCase.newValue(sequence = 4)
        ),
        expectedParents = listOf(seed),
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = listOf(seed)
        )
      )
    }

  private fun <T> mixedParentConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkScenario(
        values = listOf(
          firstFresh,
          modelCase.valueWithParentConflict(
            existing = seed,
            sequence = 3
          ),
          secondFresh
        ),
        expectedParents = expectedParents,
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = expectedParents
        )
      )
    }

  private fun <T> mixedChildConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkScenario(
        values = listOf(
          firstFresh,
          modelCase.valueWithChildConflict(
            existing = seed,
            sequence = 3
          ),
          secondFresh
        ),
        expectedParents = expectedParents,
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = expectedParents
        )
      )
    }

  private fun <T> allConflictsScenario(
    modelCase: RecursiveInsertConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkScenario(
        values = listOf(
          modelCase.valueWithParentConflict(
            existing = seed,
            sequence = 2
          ),
          modelCase.valueWithChildConflict(
            existing = seed,
            sequence = 3
          )
        ),
        expectedParents = listOf(seed),
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = listOf(seed)
        )
      )
    }

  private fun <T> RecursiveInsertConflictModelCase<T>.seed(): T {
    val value = newValue(sequence = 1)
    when (insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $name")
    }
    return value
  }

  private fun <T> relatedRows(
    modelCase: RecursiveInsertConflictModelCase<T>,
    values: List<T>
  ) = values.flatMap(modelCase::relatedValues)

  private fun <T> assertSnapshot(
    modelCase: RecursiveInsertConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    val actualParents = Select
      .from(modelCase.table)
      .queryDeep()
      .execute()
    assertThat(actualParents)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = expectedParents,
          actual = actualParents
        )
      )
    assertThat(
      Select
        .from(modelCase.relatedTable)
        .queryDeep()
        .execute()
    ).containsExactlyElementsIn(expectedRelated)
  }

  private data class RecursiveBulkScenario<T>(
    val values: List<T>,
    val expectedParents: List<T>,
    val expectedRelated: List<Any?>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveInsertConflictCases
  }
}
