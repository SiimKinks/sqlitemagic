package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.DeleteModelCase
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
class MissingSingleDeleteTest(
  private val modelCase: DeleteModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithMissingRowReturnsZeroAndLeavesStateUnchanged() {
    assertMissingDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithMissingRowReturnsZeroAndLeavesStateUnchanged() {
    assertMissingDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertMissingDelete(
    modelCase: DeleteModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val deletedValue = snapshotBefore.parents.first()
    assertThat(modelCase.executeDelete(value = deletedValue)).isEqualTo(1)
    val expected = captureDatabaseSnapshot(modelCase = modelCase)
    val deletedCount = terminal.select(
      execute = { modelCase.executeDelete(value = deletedValue) },
      observe = { modelCase.observeDelete(value = deletedValue) }
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
    fun modelCases() = ModelCatalog.deleteCases
  }
}
