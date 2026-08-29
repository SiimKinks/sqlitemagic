package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
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
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsNonConflictingGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsNonConflictingGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithNonConflictingGraphs() {
    assertConflictIgnoreMixed(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithAllParentAndChildConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithAllParentAndChildConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

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
      OperationTerminal.EXECUTE -> assertThrows(SQLiteConstraintException::class.java) {
        modelCase
          .bulkUpdate(values = scenario.values)
          .execute()
      }
      OperationTerminal.OBSERVE -> modelCase
        .bulkUpdate(values = scenario.values)
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
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
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .bulkUpdate(values = scenario.values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isTrue()
      OperationTerminal.OBSERVE -> modelCase
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
    val scenario = allConflictsScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .bulkUpdate(values = scenario.values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
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
      RecursiveConflictTarget.PARENT -> modelCase.valueWithParentConflict(
        existing = rows[1],
        conflicting = rows[2],
        sequence = 5
      )
      RecursiveConflictTarget.CHILD -> modelCase.valueWithChildConflict(
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

  private fun <T> assertSnapshot(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    assertRowsInOrder(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsInOrder(
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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveUpdateConflictCases
  }
}
