package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectBulkPersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithInsertFallbackConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRestoresSnapshot() =
    assertInsertConflict(
      modelCase = modelCase,
      terminal = EXECUTE
    )

  @Test
  fun observeWithInsertFallbackConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRestoresSnapshot() =
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OBSERVE
    )

  @Test
  fun executeWithUpdatePathConflictAndDefaultAlgorithmThrowsSQLiteConstraintExceptionAndRestoresSnapshot() =
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = EXECUTE
    )

  @Test
  fun observeWithUpdatePathConflictAndDefaultAlgorithmEmitsSQLiteConstraintExceptionAndRestoresSnapshot() =
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OBSERVE
    )

  @Test
  fun executeWithMixedConflictsAndConflictIgnoreReturnsTrueAndCommitsSuccessfulOperations() = assertMixedConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithMixedConflictsAndConflictIgnoreCompletesAndCommitsSuccessfulOperations() = assertMixedConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithAllConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithAllConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() = assertAllConflicts(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertInsertConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val before = seedRows(
      modelCase = modelCase,
      count = 1
    )
    val values = listOf(
      modelCase.newValue(sequence = 2),
      modelCase.valueWithInsertConflict(
        existing = before.single(),
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
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = before
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val values = listOf(
      modelCase.valueWithUpdateConflict(
        existing = before[0],
        conflicting = before[0],
        sequence = 3
      ),
      modelCase.valueWithUpdateConflict(
        existing = before[1],
        conflicting = before[0],
        sequence = 4
      ),
      modelCase.newValue(sequence = 5)
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
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = before
    )
  }

  private fun <T> assertMixedConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val successfulUpdate = modelCase.valueWithUpdateConflict(
      existing = before[0],
      conflicting = before[0],
      sequence = 3
    )
    val successfulInsert = modelCase.newValue(sequence = 6)
    val values = listOf(
      successfulUpdate,
      modelCase.valueWithInsertConflict(
        existing = before[1],
        sequence = 4
      ),
      modelCase.valueWithUpdateConflict(
        existing = before[1],
        conflicting = before[0],
        sequence = 5
      ),
      successfulInsert
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
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = listOf(
        successfulUpdate,
        before[1],
        successfulInsert
      )
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val values = listOf(
      modelCase.valueWithInsertConflict(
        existing = before[0],
        sequence = 3
      ),
      modelCase.valueWithUpdateConflict(
        existing = before[1],
        conflicting = before[0],
        sequence = 4
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
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = before
    )
  }

  private fun <T> bulkPersist(
    modelCase: PersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
