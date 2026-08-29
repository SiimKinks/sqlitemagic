package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectBulkPersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithInsertFallbackConflictAndDefaultAlgorithmReturnsFalseAndRestoresSnapshot() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithInsertFallbackConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRestoresSnapshot() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithUpdatePathConflictAndDefaultAlgorithmReturnsFalseAndRestoresSnapshot() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithUpdatePathConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRestoresSnapshot() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithMixedConflictsAndConflictIgnoreReturnsTrueAndCommitsSuccessfulOperations() {
    assertMixedConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithMixedConflictsAndConflictIgnoreCompletesAndCommitsSuccessfulOperations() {
    assertMixedConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithAllConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithAllConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertInsertConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = insertConflictScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values
        ).execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      )
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
    assertRowsInOrder(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = updateConflictScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values
        ).execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      )
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
    assertRowsInOrder(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertMixedConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = mixedConflictScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isTrue()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertRowsInOrder(
      table = modelCase.table,
      expected = scenario.expected
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertRowsInOrder(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> bulkPersist(
    modelCase: PersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> insertConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 1
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        modelCase.newValue(sequence = 2),
        modelCase.valueWithInsertConflict(
          existing = before.single(),
          sequence = 3
        ),
        modelCase.newValue(sequence = 4)
      ),
      expected = before
    )
  }

  private fun <T> updateConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
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
      ),
      expected = before
    )
  }

  private fun <T> mixedConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
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
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
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
      ),
      expected = listOf(
        successfulUpdate,
        before[1],
        successfulInsert
      )
    )
  }

  private fun <T> allConflictsScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        modelCase.valueWithInsertConflict(
          existing = before[0],
          sequence = 3
        ),
        modelCase.valueWithUpdateConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 4
        )
      ),
      expected = before
    )
  }

  private data class DirectBulkPersistScenario<T>(
    val before: List<T>,
    val values: List<T>,
    val expected: List<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
