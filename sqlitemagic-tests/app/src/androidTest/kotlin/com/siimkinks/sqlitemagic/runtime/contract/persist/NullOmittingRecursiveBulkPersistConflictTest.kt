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
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NullOmittingRecursiveBulkPersistConflictTest(
  private val modelCase: RecursiveNullOmittingPersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  private fun <T> assertConflict(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val seed = expected.parents.single()
    assertSuccessfulBulkPersist(
      modelCase = modelCase,
      values = listOf(
        modelCase.withNullOmittingValues(value = seed),
        modelCase.valueWithNullOmittingInsertConflict(
          existing = seed,
          conflict = conflict,
          sequence = 3
        )
      ),
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
