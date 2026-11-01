package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursiveInsertConflictCases
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.CHILD
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.PARENT
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.isConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seed
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveBulkInsertConflictTest(
  private val modelCase: RecursiveInsertConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictThrowsConstraintFailureAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictEmitsConstraintFailureAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictThrowsConstraintFailureAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictEmitsConstraintFailureAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithParentConflictAndConflictIgnoreCommitsFreshGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesWithFreshGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreCommitsFreshGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesWithFreshGraphs() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithAllParentAndChildConflictsReturnsFalseAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithAllParentAndChildConflictsCompletesAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertDefaultConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val values = listOf(
      modelCase.newValue(sequence = 2),
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = conflict,
        sequence = 3
      ),
      modelCase.newValue(sequence = 4)
    )

    when (terminal) {
      EXECUTE -> assertConstraintFailure {
        modelCase
          .bulkInsert(values)
          .execute()
      }
      OBSERVE -> modelCase
        .bulkInsert(values)
        .observe()
        .test()
        .assertFailure(Throwable::isConstraintFailure)
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = listOf(seed),
      expectedRelated = modelCase.relatedValues(seed)
    )
  }

  private fun <T> assertConflictIgnoreMixed(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val firstFresh = modelCase.newValue(sequence = 2)
    val secondFresh = modelCase.newValue(sequence = 4)
    val values = listOf(
      firstFresh,
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = conflict,
        sequence = 3
      ),
      secondFresh
    )

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase
          .bulkInsert(values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isTrue()
      OBSERVE -> modelCase
        .bulkInsert(values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult()
    }
    val expectedParents = listOf(seed, firstFresh, secondFresh)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = expectedParents,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = expectedParents
      )
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: RecursiveInsertConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val values = listOf(
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = PARENT,
        sequence = 2
      ),
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = CHILD,
        sequence = 3
      )
    )

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase
          .bulkInsert(values)
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OBSERVE -> modelCase
        .bulkInsert(values)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = listOf(seed),
      expectedRelated = modelCase.relatedValues(seed)
    )
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

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = recursiveInsertConflictCases
  }
}
