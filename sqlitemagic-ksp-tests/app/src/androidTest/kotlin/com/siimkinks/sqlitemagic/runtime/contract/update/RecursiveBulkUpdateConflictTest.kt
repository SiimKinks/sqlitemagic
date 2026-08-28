package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveBulkUpdateConflictTest(
  private val modelCase: RecursiveUpdateConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflictExecute(
      modelCase = modelCase,
      scenarioFactory = ::parentConflictScenario
    )
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflictObserve(
      modelCase = modelCase,
      scenarioFactory = ::parentConflictScenario
    )
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflictExecute(
      modelCase = modelCase,
      scenarioFactory = ::childConflictScenario
    )
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflictObserve(
      modelCase = modelCase,
      scenarioFactory = ::childConflictScenario
    )
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsNonConflictingGraphs() {
    assertConflictIgnoreMixedExecute(
      modelCase = modelCase,
      scenarioFactory = ::mixedParentConflictScenario
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() {
    assertConflictIgnoreMixedObserve(
      modelCase = modelCase,
      scenarioFactory = ::mixedParentConflictScenario
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsNonConflictingGraphs() {
    assertConflictIgnoreMixedExecute(
      modelCase = modelCase,
      scenarioFactory = ::mixedChildConflictScenario
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() {
    assertConflictIgnoreMixedObserve(
      modelCase = modelCase,
      scenarioFactory = ::mixedChildConflictScenario
    )
  }

  @Test
  fun executeWithAllParentAndChildConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllParentAndChildConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertDefaultConflictExecute(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    scenarioFactory: (RecursiveUpdateConflictModelCase<T>) -> RecursiveBulkUpdateScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    assertThrows(SQLiteConstraintException::class.java) {
      modelCase
        .bulkUpdate(values = scenario.values)
        .execute()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertDefaultConflictObserve(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    scenarioFactory: (RecursiveUpdateConflictModelCase<T>) -> RecursiveBulkUpdateScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    modelCase
      .bulkUpdate(values = scenario.values)
      .observe()
      .test()
      .assertFailure(SQLiteConstraintException::class.java)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnoreMixedExecute(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    scenarioFactory: (RecursiveUpdateConflictModelCase<T>) -> RecursiveBulkUpdateScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    assertThat(
      modelCase
        .bulkUpdate(values = scenario.values)
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
    modelCase: RecursiveUpdateConflictModelCase<T>,
    scenarioFactory: (RecursiveUpdateConflictModelCase<T>) -> RecursiveBulkUpdateScenario<T>
  ) {
    val scenario = scenarioFactory(modelCase)

    modelCase
      .bulkUpdate(values = scenario.values)
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

  private fun <T> assertAllConflictsExecute(modelCase: RecursiveUpdateConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    assertThat(
      modelCase
        .bulkUpdate(values = scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isFalse()
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflictsObserve(modelCase: RecursiveUpdateConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    modelCase
      .bulkUpdate(values = scenario.values)
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
    modelCase: RecursiveUpdateConflictModelCase<T>
  ) = threeRowScenario(
    modelCase = modelCase,
    conflict = RecursiveConflict.PARENT
  )

  private fun <T> childConflictScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>
  ) = threeRowScenario(
    modelCase = modelCase,
    conflict = RecursiveConflict.CHILD
  )

  private fun <T> mixedParentConflictScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>
  ) = threeRowScenario(
    modelCase = modelCase,
    conflict = RecursiveConflict.PARENT,
    expectPartialSuccess = true
  )

  private fun <T> mixedChildConflictScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>
  ) = threeRowScenario(
    modelCase = modelCase,
    conflict = RecursiveConflict.CHILD,
    expectPartialSuccess = true
  )

  private fun <T> threeRowScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflict,
    expectPartialSuccess: Boolean = false
  ): RecursiveBulkUpdateScenario<T> {
    val rows = seedRows(
      modelCase = modelCase,
      count = 3
    )
    val updatedFirst = modelCase.updatedValue(
      value = rows[0],
      sequence = 4
    )
    val conflictingMiddle = when (conflict) {
      RecursiveConflict.PARENT -> modelCase.valueWithParentConflict(
        existing = rows[1],
        conflicting = rows[2],
        sequence = 5
      )
      RecursiveConflict.CHILD -> modelCase.valueWithChildConflict(
        existing = rows[1],
        conflicting = rows[2],
        sequence = 5
      )
    }
    val updatedThird = modelCase.updatedValue(
      value = rows[2],
      sequence = 6
    )
    val expectedParents = when {
      expectPartialSuccess -> listOf(
        modelCase.expectedAfterUpdate(updatedFirst),
        rows[1],
        modelCase.expectedAfterUpdate(updatedThird)
      )
      else -> rows
    }
    return RecursiveBulkUpdateScenario(
      values = listOf(updatedFirst, conflictingMiddle, updatedThird),
      expectedParents = expectedParents,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = expectedParents
      )
    )
  }

  private fun <T> allConflictsScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>
  ): RecursiveBulkUpdateScenario<T> {
    val rows = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return RecursiveBulkUpdateScenario(
      values = listOf(
        modelCase.valueWithParentConflict(
          existing = rows[0],
          conflicting = rows[1],
          sequence = 3
        ),
        modelCase.valueWithChildConflict(
          existing = rows[1],
          conflicting = rows[0],
          sequence = 4
        )
      ),
      expectedParents = rows,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = rows
      )
    )
  }

  private fun <T> seedRows(
    modelCase: RecursiveUpdateConflictModelCase<T>,
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

  private fun <T> relatedRows(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    values: List<T>
  ) = values.flatMap(modelCase::relatedValues)

  private fun <T> assertSnapshot(
    modelCase: RecursiveUpdateConflictModelCase<T>,
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

  private data class RecursiveBulkUpdateScenario<T>(
    val values: List<T>,
    val expectedParents: List<T>,
    val expectedRelated: List<Any?>
  )

  private enum class RecursiveConflict {
    PARENT,
    CHILD
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveUpdateConflictCases
  }
}
