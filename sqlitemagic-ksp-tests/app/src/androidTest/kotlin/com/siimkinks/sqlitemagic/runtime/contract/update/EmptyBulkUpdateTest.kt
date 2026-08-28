package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
      terminal = BulkUpdateTerminal.EXECUTE
    )
  }

  @Test
  fun observeCompletesAndLeavesDatabaseUnchanged() {
    assertEmptyUpdate(
      modelCase = modelCase,
      terminal = BulkUpdateTerminal.OBSERVE
    )
  }

  private fun <T> assertEmptyUpdate(
    modelCase: BulkUpdateModelCase<T>,
    terminal: BulkUpdateTerminal
  ) {
    val seed = modelCase.newValue(sequence = 1)
    when (modelCase.insert(value = seed).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
    }
    val parentBefore = captureRows(modelCase.table)
    val relatedBefore = when (modelCase) {
      is RecursiveBulkUpdateModelCase<*> -> captureRows(modelCase.relatedTable)
      else -> null
    }
    when (terminal) {
      BulkUpdateTerminal.EXECUTE -> assertThat(
        modelCase.executeBulkUpdate(values = emptyList())
      ).isFalse()
      BulkUpdateTerminal.OBSERVE -> modelCase
        .observeBulkUpdate(values = emptyList())
        .blockingAwait()
    }
    assertThat(captureRows(modelCase.table))
      .containsExactlyElementsIn(parentBefore)
      .inOrder()
    if (modelCase is RecursiveBulkUpdateModelCase<*>) {
      assertThat(captureRows(modelCase.relatedTable))
        .containsExactlyElementsIn(checkNotNull(relatedBefore))
        .inOrder()
    }
  }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private enum class BulkUpdateTerminal {
    EXECUTE,
    OBSERVE
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.emptyBulkUpdateCases
  }
}
