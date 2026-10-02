package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NullOmittingDirectSinglePersistConflictTest(
  private val modelCase: NullOmittingPersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertInsertConflict(
    modelCase: NullOmittingPersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
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
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val conflicting = modelCase.newValue(sequence = 2)
    assertSeedInserted(
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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistConflictCases
  }
}
