package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class EmptyBulkPersistTest(
  private val modelCase: BulkPersistModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithEmptyInputReturnsFalseAndLeavesSnapshotUnchanged() = assertEmptyPersist(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithEmptyInputCompletesAndLeavesSnapshotUnchanged() = assertEmptyPersist(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertEmptyPersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    insertedResult(
      result = modelCase
        .insert(value = modelCase.newValue(sequence = 1))
        .execute(),
      modelName = modelCase.name
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)

    when (terminal) {
      EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = emptyList())
      ).isFalse()
      OBSERVE -> modelCase
        .observeBulkPersist(
          adapter = RxJava2Adapter,
          values = emptyList()
        )
        .await()
    }

    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkPersistCases
  }
}
