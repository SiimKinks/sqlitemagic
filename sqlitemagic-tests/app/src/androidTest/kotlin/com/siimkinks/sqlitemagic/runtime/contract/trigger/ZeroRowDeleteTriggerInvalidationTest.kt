package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.observeRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class ZeroRowDeleteTriggerInvalidationTest(
  private val modelCase: TriggerModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeMissingDeleteDoesNotRefresh() = assertMissingDelete(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeMissingDeleteDoesNotRefresh() = assertMissingDelete(
    modelCase = modelCase,
    terminal = OBSERVE
  )

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
      val deletedCount = terminal.select(
        execute = { modelCase.executeDelete(value = seeded.first()) },
        observe = { modelCase.observeDelete(value = seeded.first()) }
      )

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
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerCases
  }
}
