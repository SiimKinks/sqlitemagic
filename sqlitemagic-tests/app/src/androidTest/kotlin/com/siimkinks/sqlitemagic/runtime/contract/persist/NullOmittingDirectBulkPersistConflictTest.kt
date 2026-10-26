package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NullOmittingDirectBulkPersistConflictTest(
  private val modelCase: NullOmittingPersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithMixedConflictsAndConflictIgnoreOmitsNullValuesAndCommitsSuccessfulRows() = assertMixedConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithMixedConflictsAndConflictIgnoreOmitsNullValuesAndCommitsSuccessfulRows() = assertMixedConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertMixedConflict(
    modelCase: NullOmittingPersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
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
    assertSuccessfulBulkPersist(
      modelCase = modelCase,
      values = listOf(
        successfulInsert,
        successfulUpdate,
        insertConflict,
        updateConflict
      ),
      terminal = terminal
    )
    val actual = captureDatabaseSnapshot(modelCase = modelCase)
    val inserted = actual.parents.filterNot(before.parents::contains)
    assertThat(inserted).hasSize(1)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = before.parents + inserted
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistConflictCases
  }
}
