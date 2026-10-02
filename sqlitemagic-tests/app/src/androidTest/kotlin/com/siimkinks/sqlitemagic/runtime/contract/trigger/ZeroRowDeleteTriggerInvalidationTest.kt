package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ZeroRowDeleteTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeMissingDeleteDoesNotRefresh() {
    assertMissingDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeMissingDeleteDoesNotRefresh() {
    assertMissingDelete(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertMissingDelete(
    modelCase: TriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seeded = seedRows(
      modelCase = modelCase,
      count = 2
    )
    assertThat(modelCase.executeDelete(value = seeded.first())).isEqualTo(1)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(table = modelCase.table)
    try {
      val deletedCount = when (terminal) {
        OperationTerminal.EXECUTE -> modelCase.executeDelete(value = seeded.first())
        OperationTerminal.OBSERVE -> modelCase.observeDelete(value = seeded.first()).blockingGet()
      }

      assertThat(deletedCount).isEqualTo(0)
      assertNoRefresh(
        observer = observer,
        expected = before.parents
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerCases
  }
}
