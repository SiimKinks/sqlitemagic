package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursiveUpdateConflictCases
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.CHILD
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.PARENT
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.isConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveBulkUpdateConflictTest(
  private val modelCase: RecursiveUpdateConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsConstraintFailureAndRollsBackBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = PARENT,
      terminal = EXECUTE
    )

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsConstraintFailureAndRollsBackBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = PARENT,
      terminal = OBSERVE
    )

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsConstraintFailureAndRollsBackBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = CHILD,
      terminal = EXECUTE
    )

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsConstraintFailureAndRollsBackBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = CHILD,
      terminal = OBSERVE
    )

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsNonConflictingGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsNonConflictingGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithAllParentAndChildConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithAllParentAndChildConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertDefaultConflict(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = threeRowScenario(
      modelCase = modelCase,
      conflict = conflict
    )

    when (terminal) {
      EXECUTE -> assertConstraintFailure {
        modelCase
          .bulkUpdate(values = scenario.values)
          .execute()
      }
      OBSERVE -> modelCase
        .bulkUpdate(values = scenario.values)
        .observe()
        .test()
        .assertFailure(Throwable::isConstraintFailure)
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnoreMixed(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = threeRowScenario(
      modelCase = modelCase,
      conflict = conflict,
      expectPartialSuccess = true
    )

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase
          .bulkUpdate(values = scenario.values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isTrue()
      OBSERVE -> modelCase
        .bulkUpdate(values = scenario.values)
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
    modelCase: RecursiveUpdateConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val rows = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val values = listOf(
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
    )

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase
          .bulkUpdate(values = values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OBSERVE -> modelCase
        .bulkUpdate(values = values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = rows,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = rows
      )
    )
  }

  private fun <T> threeRowScenario(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
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
      PARENT -> modelCase.valueWithParentConflict(
        existing = rows[1],
        conflicting = rows[2],
        sequence = 5
      )
      CHILD -> modelCase.valueWithChildConflict(
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

  private fun <T> assertSnapshot(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  private data class RecursiveBulkUpdateScenario<T>(
    val values: List<T>,
    val expectedParents: List<T>,
    val expectedRelated: List<Any?>
  )

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = recursiveUpdateConflictCases
  }
}
