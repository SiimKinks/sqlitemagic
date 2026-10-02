package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NullOmittingDirectBulkPersistConflictTest(
  private val modelCase: NullOmittingPersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithMixedConflictsAndConflictIgnoreOmitsNullValuesAndCommitsSuccessfulRows() {
    assertMixedConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithMixedConflictsAndConflictIgnoreOmitsNullValuesAndCommitsSuccessfulRows() {
    assertMixedConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertMixedConflict(
    modelCase: NullOmittingPersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = mixedConflictScenario(modelCase = modelCase)
    assertSuccessfulBulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      terminal = terminal
    )
    val actual = captureDatabaseSnapshot(modelCase = modelCase)
    val inserted = actual.parents.filterNot(scenario.before.parents::contains)
    assertThat(inserted).hasSize(1)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = scenario.before.parents + inserted
      )
    )
  }

  private fun <T> mixedConflictScenario(
    modelCase: NullOmittingPersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val successfulUpdate = modelCase.withNullOmittingValues(value = before.parents[0])
    val successfulInsert = modelCase.newValue(sequence = 6)
    val insertConflict = modelCase.valueWithNullOmittingInsertConflict(
      existing = before.parents[1],
      sequence = 4
    )
    val updateConflict = modelCase.valueWithNullOmittingUpdateConflict(
      existing = before.parents[1],
      conflicting = before.parents[0],
      sequence = 5
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        successfulInsert,
        successfulUpdate,
        insertConflict,
        updateConflict
      )
    )
  }

  private data class DirectBulkPersistScenario<T>(
    val before: DatabaseSnapshot<T>,
    val values: List<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistConflictCases
  }
}
