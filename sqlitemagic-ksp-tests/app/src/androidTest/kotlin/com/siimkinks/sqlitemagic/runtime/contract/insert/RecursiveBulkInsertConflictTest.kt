package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveBulkInsertConflictTest(
  private val modelCase: RecursiveInsertConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictReturnsFalseAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictEmitsOperationFailedExceptionAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictReturnsFalseAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictEmitsOperationFailedExceptionAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsFreshGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithFreshGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsFreshGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithFreshGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithAllParentAndChildConflictsReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithAllParentAndChildConflictsCompletesAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertDefaultConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = defaultConflictScenario(
      modelCase = modelCase,
      conflict = conflict
    )

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .bulkInsert(scenario.values)
          .execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
        .bulkInsert(scenario.values)
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnoreMixed(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = mixedConflictScenario(
      modelCase = modelCase,
      conflict = conflict
    )

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .bulkInsert(scenario.values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isTrue()
      OperationTerminal.OBSERVE -> modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: RecursiveInsertConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .bulkInsert(scenario.values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
        .bulkInsert(scenario.values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> defaultConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkScenario(
        values = listOf(
          modelCase.newValue(sequence = 2),
          modelCase.valueWithConflict(
            existing = seed,
            conflict = conflict,
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

  private fun <T> mixedConflictScenario(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkScenario(
        values = listOf(
          firstFresh,
          modelCase.valueWithConflict(
            existing = seed,
            conflict = conflict,
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

  private fun <T> RecursiveInsertConflictModelCase<T>.valueWithConflict(
    existing: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = when (conflict) {
    RecursiveConflictTarget.PARENT -> valueWithParentConflict(
      existing = existing,
      sequence = sequence
    )
    RecursiveConflictTarget.CHILD -> valueWithChildConflict(
      existing = existing,
      sequence = sequence
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

  private fun <T> assertSnapshot(
    modelCase: RecursiveInsertConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    val actualParents = captureRows(table = modelCase.table)
    assertThat(actualParents)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = expectedParents,
          actual = actualParents
        )
      )
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
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
