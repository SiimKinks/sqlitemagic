package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NullOmittingDirectSinglePersistConflictTest(
  private val modelCase: NullOmittingPersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertInsertConflict(
    modelCase: NullOmittingPersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    insertedResult(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val candidate = modelCase.valueWithNullOmittingInsertConflict(
      existing = existing,
      sequence = 2
    )
    assertIgnoredPersist(
      modelCase = modelCase,
      value = candidate,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = before
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: NullOmittingPersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    insertedResult(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val conflicting = modelCase.newValue(sequence = 2)
    insertedResult(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val candidate = modelCase.valueWithNullOmittingUpdateConflict(
      existing = before.parents[0],
      conflicting = before.parents[1],
      sequence = 3
    )
    assertIgnoredPersist(
      modelCase = modelCase,
      value = candidate,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = before
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistConflictCases
  }
}
