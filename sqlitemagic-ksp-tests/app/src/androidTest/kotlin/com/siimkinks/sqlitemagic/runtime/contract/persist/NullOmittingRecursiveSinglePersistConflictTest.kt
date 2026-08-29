package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NullOmittingRecursiveSinglePersistConflictTest(
  private val modelCase: RecursiveNullOmittingPersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithParentUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertInsertConflict(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val candidate = modelCase.valueWithNullOmittingInsertConflict(
      existing = existing,
      conflict = conflict,
      sequence = 2
    )
    assertIgnoredPersist(
      modelCase = modelCase,
      value = candidate,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val first = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = first)
        .execute(),
      modelName = modelCase.name
    )
    val second = modelCase.newValue(sequence = 2)
    assertSeedInserted(
      result = modelCase
        .insert(value = second)
        .execute(),
      modelName = modelCase.name
    )
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val candidate = modelCase.valueWithNullOmittingUpdateConflict(
      existing = expected.parents[0],
      conflicting = expected.parents[1],
      conflict = conflict,
      sequence = 3
    )
    assertIgnoredPersist(
      modelCase = modelCase,
      value = candidate,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveNullOmittingPersistConflictCases
  }
}
