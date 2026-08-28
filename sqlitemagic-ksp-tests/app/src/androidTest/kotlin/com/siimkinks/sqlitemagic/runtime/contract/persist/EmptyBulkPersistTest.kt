package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
      terminal = BulkPersistTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithEmptyInputCompletesAndLeavesSnapshotUnchanged() {
    assertEmptyPersist(
      modelCase = modelCase,
      terminal = BulkPersistTerminal.OBSERVE
    )
  }

  private fun <T> assertEmptyPersist(
    modelCase: BulkPersistModelCase<T>,
    terminal: BulkPersistTerminal
  ) {
    seedRow(modelCase = modelCase)
    val beforeParents = captureRows(table = modelCase.table)
    val beforeRelated = when (modelCase) {
      is RecursiveBulkPersistModelCase<*> -> captureRows(table = modelCase.relatedTable)
      else -> emptyList()
    }

    when (terminal) {
      BulkPersistTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkPersist(values = emptyList())
      ).isFalse()
      BulkPersistTerminal.OBSERVE -> modelCase
        .observeBulkPersist(values = emptyList())
        .blockingAwait()
    }

    assertRows(
      table = modelCase.table,
      expected = beforeParents
    )
    when (modelCase) {
      is RecursiveBulkPersistModelCase<*> -> assertThat(captureRows(table = modelCase.relatedTable))
        .containsExactlyElementsIn(beforeRelated)
        .inOrder()
      else -> Unit
    }
  }

  private fun <T> seedRow(modelCase: BulkPersistModelCase<T>) {
    val value = modelCase.newValue(sequence = 1)
    when (modelCase.insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
    }
  }

  private fun <T> assertRows(
    table: Table<T>,
    expected: List<T>
  ) = assertThat(captureRows(table = table))
    .containsExactlyElementsIn(expected)
    .inOrder()

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private enum class BulkPersistTerminal {
    EXECUTE,
    OBSERVE
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkPersistCases
  }
}
