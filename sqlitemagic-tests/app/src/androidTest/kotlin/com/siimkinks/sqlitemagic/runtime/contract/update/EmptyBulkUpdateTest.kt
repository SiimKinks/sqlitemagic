package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
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
internal class EmptyBulkUpdateTest(
  private val modelCase: BulkUpdateModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsFalseAndLeavesDatabaseUnchanged() = assertEmptyUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeCompletesAndLeavesDatabaseUnchanged() = assertEmptyUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertEmptyUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.newValue(sequence = 1)
    insertedResult(
      result = modelCase
        .insert(value = seed)
        .execute(),
      modelName = modelCase.name
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = emptyList())
      ).isFalse()
      OBSERVE -> modelCase
        .observeBulkUpdate(values = emptyList())
        .blockingAwait()
    }
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkUpdateCases
  }
}
