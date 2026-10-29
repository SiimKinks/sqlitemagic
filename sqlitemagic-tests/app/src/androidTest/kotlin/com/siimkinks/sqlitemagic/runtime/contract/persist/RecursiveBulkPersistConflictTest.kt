package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seed
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveBulkPersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRollsBackWholeBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = EXECUTE
    )

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackWholeBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OBSERVE
    )

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRollsBackWholeBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = EXECUTE
    )

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRollsBackWholeBatch() =
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OBSERVE
    )

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() = assertConflictIgnore(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() = assertConflictIgnore(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() = assertConflictIgnore(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() = assertConflictIgnore(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
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
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seedStoredValue()
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
      EXECUTE -> assertThrows(SQLiteConstraintException::class.java) {
        bulkPersist(
          modelCase = modelCase,
          values = values
        ).execute()
      }
      OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = values
      )
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = listOf(seed),
      expectedRelated = modelCase.relatedValues(seed)
    )
  }

  private fun <T> assertConflictIgnore(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seedStoredValue()
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
        bulkPersist(
          modelCase = modelCase,
          values = values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isTrue()
      OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
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
    modelCase: RecursivePersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seedStoredValue()
    val values = listOf(
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = RecursiveConflictTarget.PARENT,
        sequence = 2
      ),
      modelCase.valueWithInsertConflict(
        existing = seed,
        conflict = RecursiveConflictTarget.CHILD,
        sequence = 3
      )
    )

    when (terminal) {
      EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isFalse()
      OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = listOf(seed),
      expectedRelated = modelCase.relatedValues(seed)
    )
  }

  private fun <T> bulkPersist(
    modelCase: RecursivePersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> RecursivePersistConflictModelCase<T>.seedStoredValue(): T {
    seed()
    return captureRows(table = table).single()
  }

  private fun <T> assertSnapshot(
    modelCase: RecursivePersistConflictModelCase<T>,
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

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
