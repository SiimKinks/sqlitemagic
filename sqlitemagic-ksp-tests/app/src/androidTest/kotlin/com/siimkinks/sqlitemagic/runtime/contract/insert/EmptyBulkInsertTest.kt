package com.siimkinks.sqlitemagic.runtime.contract.insert

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EmptyBulkInsertTest(
  private val modelCase: BulkInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsFalseAndLeavesDatabaseUnchanged() {
    assertEmptyBulkInsert(
      modelCase = modelCase,
      operation = EntityBulkInsertBuilder::execute
    )
  }

  @Test
  fun observeCompletesAndLeavesDatabaseUnchanged() {
    assertEmptyBulkInsert(
      modelCase = modelCase,
      operation = { builder ->
        builder
          .observe()
          .blockingAwait()
        false
      }
    )
  }

  private fun <T> assertEmptyBulkInsert(
    modelCase: BulkInsertModelCase<T>,
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) {
    val seed = modelCase.newValue(sequence = 1)
    assertThat(
      modelCase
        .bulkInsert(values = listOf(seed))
        .execute()
    ).isTrue()

    val parentBefore = captureRows(modelCase.table)
    val relatedBefore = when (modelCase) {
      is RecursiveInsertConflictModelCase<*> -> captureRows(modelCase.relatedTable)
      else -> null
    }

    assertThat(
      operation(
        modelCase.bulkInsert(values = emptyList())
      )
    ).isFalse()
    assertThat(captureRows(modelCase.table))
      .containsExactlyElementsIn(parentBefore)

    if (modelCase is RecursiveInsertConflictModelCase<*>) {
      assertThat(captureRows(modelCase.relatedTable))
        .containsExactlyElementsIn(checkNotNull(relatedBefore))
    }
  }

  private fun <T> captureRows(table: Table<T>): List<T> = Select
    .from(table)
    .queryDeep()
    .execute()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() =
      ModelCatalog.uniqueInsertCases +
          ModelCatalog.recursiveInsertConflictCases
  }
}
