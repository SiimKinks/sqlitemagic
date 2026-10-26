package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.seed
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectBulkInsertConflictTest(
  private val modelCase: UniqueInsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithDefaultConflictThrowsSQLiteConstraintExceptionAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithDefaultConflictEmitsSQLiteConstraintExceptionAndRollsBackBatch() = assertDefaultConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithConflictIgnoreReturnsTrueAndCommitsFreshRows() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithConflictIgnoreCompletesAndCommitsFreshRows() = assertConflictIgnoreMixed(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithAllConflictsReturnsFalseAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithAllConflictsCompletesAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertDefaultConflict(
    modelCase: UniqueInsertModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val values = listOf(
      modelCase.newValue(sequence = 2),
      modelCase.conflictingValue(
        existing = seed,
        sequence = 3
      ),
      modelCase.newValue(sequence = 4)
    )

    when (terminal) {
      EXECUTE -> assertThrows(SQLiteConstraintException::class.java) {
        modelCase
          .bulkInsert(values)
          .execute()
      }
      OBSERVE -> modelCase
        .bulkInsert(values)
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
    assertRows(
      modelCase = modelCase,
      expected = listOf(seed)
    )
  }

  private fun <T> assertConflictIgnoreMixed(
    modelCase: UniqueInsertModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val firstFresh = modelCase.newValue(sequence = 2)
    val secondFresh = modelCase.newValue(sequence = 4)
    val values = listOf(
      firstFresh,
      modelCase.conflictingValue(
        existing = seed,
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
    assertRows(
      modelCase = modelCase,
      expected = listOf(seed, firstFresh, secondFresh)
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: UniqueInsertModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.seed()
    val values = listOf(
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
    assertRows(
      modelCase = modelCase,
      expected = listOf(seed)
    )
  }

  private fun <T> assertRows(
    modelCase: UniqueInsertModelCase<T>,
    expected: List<T>
  ) {
    val actual = captureRows(table = modelCase.table)
    assertThat(actual)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = expected,
          actual = actual
        )
      )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.uniqueInsertCases
  }
}
