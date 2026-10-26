package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistConflictModelCase
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
internal class NullOmittingRecursiveSinglePersistConflictTest(
  private val modelCase: RecursiveNullOmittingPersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildInsertConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithParentUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildUpdateConflictAndConflictIgnoreOmitsNullValuesAndReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  private fun <T> assertInsertConflict(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    insertedResult(
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
    insertedResult(
      result = modelCase
        .insert(value = first)
        .execute(),
      modelName = modelCase.name
    )
    val second = modelCase.newValue(sequence = 2)
    insertedResult(
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
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveNullOmittingPersistConflictCases
  }
}
