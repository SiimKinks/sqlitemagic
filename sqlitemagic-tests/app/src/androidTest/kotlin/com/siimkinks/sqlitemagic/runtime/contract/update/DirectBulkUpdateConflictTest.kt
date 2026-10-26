package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectBulkUpdateConflictTest(
  private val modelCase: BulkUpdateConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithDefaultConflictThrowsSQLiteConstraintExceptionAndRestoresSnapshot() = assertDefaultConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithDefaultConflictEmitsSQLiteConstraintExceptionAndRestoresSnapshot() = assertDefaultConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithConflictIgnoreReturnsTrueAndUpdatesOnlyNonConflictingRows() = assertConflictIgnore(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithConflictIgnoreCompletesAndUpdatesOnlyNonConflictingRows() = assertConflictIgnore(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithAllConflictsAndConflictIgnoreReturnsFalseAndRestoresSnapshot() = assertAllConflicts(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithAllConflictsAndConflictIgnoreCompletesAndRestoresSnapshot() = assertAllConflicts(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertDefaultConflict(
    modelCase: BulkUpdateConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    when (terminal) {
      EXECUTE -> assertThrows(SQLiteConstraintException::class.java) {
        bulkUpdate(
          modelCase = modelCase,
          values = scenario.values
        ).execute()
      }
      OBSERVE -> bulkUpdate(
        modelCase = modelCase,
        values = scenario.values
      )
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertConflictIgnore(
    modelCase: BulkUpdateConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = threeRowScenario(modelCase = modelCase)

    when (terminal) {
      EXECUTE -> assertThat(
        bulkUpdate(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isTrue()
      OBSERVE -> bulkUpdate(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = scenario.afterMixedConflict
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: BulkUpdateConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = allConflictScenario(modelCase = modelCase)

    when (terminal) {
      EXECUTE -> assertThat(
        bulkUpdate(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isFalse()
      OBSERVE -> bulkUpdate(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> bulkUpdate(
    modelCase: BulkUpdateConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkUpdate(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> threeRowScenario(
    modelCase: BulkUpdateConflictModelCase<T>
  ): BulkUpdateScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 3
    )
    val values = listOf(
      modelCase.updatedValue(
        value = before[0],
        sequence = 4
      ),
      modelCase.valueWithConflict(
        existing = before[1],
        conflicting = before[2],
        sequence = 5
      ),
      modelCase.updatedValue(
        value = before[2],
        sequence = 6
      )
    )
    return BulkUpdateScenario(
      before = before,
      values = values,
      afterMixedConflict = listOf(
        values[0],
        before[1],
        values[2]
      )
    )
  }

  private fun <T> allConflictScenario(
    modelCase: BulkUpdateConflictModelCase<T>
  ): BulkUpdateScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return BulkUpdateScenario(
      before = before,
      values = listOf(
        modelCase.valueWithConflict(
          existing = before[0],
          conflicting = before[1],
          sequence = 3
        ),
        modelCase.valueWithConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 4
        )
      ),
      afterMixedConflict = before
    )
  }

  private data class BulkUpdateScenario<T>(
    val before: List<T>,
    val values: List<T>,
    val afterMixedConflict: List<T>
  )

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateConflictCases
  }
}
