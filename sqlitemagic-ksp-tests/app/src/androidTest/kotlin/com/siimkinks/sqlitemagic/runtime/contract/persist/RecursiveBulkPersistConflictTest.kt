package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveBulkPersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndDefaultAlgorithmReturnsFalseAndRollsBackWholeBatch() {
    assertParentConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRollsBackWholeBatch() {
    assertParentConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmReturnsFalseAndRollsBackWholeBatch() {
    assertChildConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRollsBackWholeBatch() {
    assertChildConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() {
    assertParentConflictIgnoreExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() {
    assertParentConflictIgnoreObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() {
    assertChildConflictIgnoreExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() {
    assertChildConflictIgnoreObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithAllParentAndChildConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllParentAndChildConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertParentConflictExecute(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = parentConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      ).execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertParentConflictObserve(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = parentConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values
    )
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertChildConflictExecute(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = childConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      ).execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertChildConflictObserve(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = childConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values
    )
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertParentConflictIgnoreExecute(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = mixedParentConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isTrue()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertParentConflictIgnoreObserve(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = mixedParentConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertChildConflictIgnoreExecute(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = mixedChildConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isTrue()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertChildConflictIgnoreObserve(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = mixedChildConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflictsExecute(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflictsObserve(modelCase: RecursivePersistConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> bulkPersist(
    modelCase: RecursivePersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> parentConflictScenario(
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkPersistScenario(
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
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkPersistScenario(
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
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkPersistScenario(
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
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkPersistScenario(
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
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkPersistScenario(
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

  private fun <T> RecursivePersistConflictModelCase<T>.seed(): T {
    val value = newValue(sequence = 1)
    when (insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $name")
    }
    return captureRows(table = table).single()
  }

  private fun <T> relatedRows(
    modelCase: RecursivePersistConflictModelCase<T>,
    values: List<T>
  ) = values.flatMap(modelCase::relatedValues)

  private fun <T> assertSnapshot(
    modelCase: RecursivePersistConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    assertThat(captureRows(table = modelCase.table))
      .containsExactlyElementsIn(expectedParents)
      .inOrder()
    assertThat(captureRows(table = modelCase.relatedTable))
      .containsExactlyElementsIn(expectedRelated)
      .inOrder()
  }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private data class RecursiveBulkPersistScenario<T>(
    val values: List<T>,
    val expectedParents: List<T>,
    val expectedRelated: List<Any?>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
