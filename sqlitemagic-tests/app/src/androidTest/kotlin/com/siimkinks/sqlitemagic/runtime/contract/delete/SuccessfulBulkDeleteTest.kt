package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
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
class SuccessfulBulkDeleteTest(
  private val modelCase: BulkDeleteModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeDeletesMiddleRowsAndRetainsRemainingState() {
    assertSuccessfulDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeDeletesMiddleRowsAndRetainsRemainingState() {
    assertSuccessfulDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertSuccessfulDelete(
    modelCase: BulkDeleteModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 4
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val deletedValues = snapshotBefore.parents.subList(
      fromIndex = 1,
      toIndex = 3
    )
    val expected = DatabaseSnapshot(
      parents = listOf(
        snapshotBefore.parents.first(),
        snapshotBefore.parents.last()
      ),
      related = snapshotBefore.related
    )
    val deletedCount = terminal.select(
      execute = { modelCase.executeBulkDelete(values = deletedValues) },
      observe = { modelCase.observeBulkDelete(values = deletedValues) }
    )
    assertThat(deletedCount).isEqualTo(2)
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
