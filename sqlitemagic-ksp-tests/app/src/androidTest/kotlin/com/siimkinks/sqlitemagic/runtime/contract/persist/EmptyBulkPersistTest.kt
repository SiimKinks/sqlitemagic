package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotInOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EmptyBulkPersistTest(
  private val modelCase: BulkPersistModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithEmptyInputReturnsFalseAndLeavesSnapshotUnchanged() {
    assertEmptyPersist(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithEmptyInputCompletesAndLeavesSnapshotUnchanged() {
    assertEmptyPersist(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertEmptyPersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRow(modelCase = modelCase)
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = emptyList())
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
        .observeBulkPersist(values = emptyList())
        .blockingAwait()
    }

    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  private fun <T> seedRow(modelCase: BulkPersistModelCase<T>) {
    val value = modelCase.newValue(sequence = 1)
    when (modelCase.insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkPersistCases
  }
}
