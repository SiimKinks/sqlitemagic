package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TableDeleteModelCase
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
class SuccessfulTableDeleteTest(
  private val modelCase: TableDeleteModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeDeletesAllParentRowsAndRetainsRelatedState() {
    assertSuccessfulDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeDeletesAllParentRowsAndRetainsRelatedState() {
    assertSuccessfulDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertSuccessfulDelete(
    modelCase: TableDeleteModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 3
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val expected = DatabaseSnapshot<T>(
      parents = emptyList(),
      related = snapshotBefore.related
    )
    val deletedCount = terminal.select(
      execute = { modelCase.executeTableDelete() },
      observe = { modelCase.observeTableDelete() }
    )
    assertThat(deletedCount).isEqualTo(3)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.tableDeleteCases
  }
}
