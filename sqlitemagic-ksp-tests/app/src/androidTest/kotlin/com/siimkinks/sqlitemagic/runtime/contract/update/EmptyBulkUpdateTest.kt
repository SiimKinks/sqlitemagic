package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotInOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EmptyBulkUpdateTest(
  private val modelCase: BulkUpdateModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsFalseAndLeavesDatabaseUnchanged() {
    assertEmptyUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeCompletesAndLeavesDatabaseUnchanged() {
    assertEmptyUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertEmptyUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: OperationTerminal
  ) {
    val seed = modelCase.newValue(sequence = 1)
    when (modelCase.insert(value = seed).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
    }
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = emptyList())
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
        .observeBulkUpdate(values = emptyList())
        .blockingAwait()
    }
    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkUpdateCases
  }
}
