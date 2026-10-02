package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EmptyBulkDeleteTest(
  private val modelCase: BulkDeleteModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithEmptyInputReturnsZeroAndLeavesStateUnchanged() {
    assertEmptyDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithEmptyInputReturnsZeroAndLeavesStateUnchanged() {
    assertEmptyDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertEmptyDelete(
    modelCase: BulkDeleteModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val deletedCount = terminal.select(
      execute = { modelCase.executeBulkDelete(values = emptyList()) },
      observe = { modelCase.observeBulkDelete(values = emptyList()) }
    )
    assertThat(deletedCount).isEqualTo(0)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkDeleteCases
  }
}
