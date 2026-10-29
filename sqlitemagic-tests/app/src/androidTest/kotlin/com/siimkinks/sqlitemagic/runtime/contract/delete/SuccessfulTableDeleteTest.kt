package com.siimkinks.sqlitemagic.runtime.contract.delete

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TableDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulTableDeleteTest(
  private val modelCase: TableDeleteModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeDeletesAllParentRowsAndRetainsRelatedState() = assertSuccessfulDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeDeletesAllParentRowsAndRetainsRelatedState() = assertSuccessfulDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

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
      observe = { modelCase.observeTableDelete(RxJava2Adapter) }
    )
    assertThat(deletedCount).isEqualTo(3)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.tableDeleteCases
  }
}
