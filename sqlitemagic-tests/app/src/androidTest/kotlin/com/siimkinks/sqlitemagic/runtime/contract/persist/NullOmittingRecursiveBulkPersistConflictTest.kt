package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistConflictModelCase
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
class NullOmittingRecursiveBulkPersistConflictTest(
  private val modelCase: RecursiveNullOmittingPersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreOmitsNullValuesAndRollsBackFailedGraph() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertConflict(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = mixedConflictScenario(
      modelCase = modelCase,
      conflict = conflict
    )
    assertSuccessfulBulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = scenario.expected
    )
  }

  private fun <T> mixedConflictScenario(
    modelCase: RecursiveNullOmittingPersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget
  ): RecursiveBulkPersistScenario<T> {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val seed = expected.parents.single()
    val successfulUpdate = modelCase.withNullOmittingValues(value = seed)
    val failedGraph = modelCase.valueWithNullOmittingInsertConflict(
      existing = seed,
      conflict = conflict,
      sequence = 3
    )
    return RecursiveBulkPersistScenario(
      values = listOf(
        successfulUpdate,
        failedGraph
      ),
      expected = expected
    )
  }

  private data class RecursiveBulkPersistScenario<T>(
    val values: List<T>,
    val expected: DatabaseSnapshot<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveNullOmittingPersistConflictCases
  }
}
