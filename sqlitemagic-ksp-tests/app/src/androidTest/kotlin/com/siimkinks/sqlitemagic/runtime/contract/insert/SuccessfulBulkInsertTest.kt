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
class SuccessfulBulkInsertTest(
  private val modelCase: BulkInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeInsertsAndReadsBack() {
    assertSuccessfulBulkInsert(
      operation = EntityBulkInsertBuilder::execute
    )
  }

  @Test
  fun observeInsertsAndReadsBack() {
    assertSuccessfulBulkInsert { builder ->
      builder
        .observe()
        .blockingAwait()
      true
    }
  }

  private fun assertSuccessfulBulkInsert(
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) = when (modelCase) {
    is RecursiveInsertConflictModelCase<*> -> assertRecursiveSuccessfulBulkInsert(
      modelCase = modelCase,
      operation = operation
    )
    else -> assertDirectSuccessfulBulkInsert(
      modelCase = modelCase,
      operation = operation
    )
  }

  private fun <T> assertDirectSuccessfulBulkInsert(
    modelCase: BulkInsertModelCase<T>,
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) {
    val values = List(3, init = modelCase::newValue)
    assertThat(
      operation(
        modelCase.bulkInsert(values = values)
      )
    ).isTrue()

    val actual = captureRows(modelCase.table)
    assertThat(actual)
      .hasSize(values.size)
    assertThat(actual)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = values,
          actual = actual
        )
      )
  }

  private fun <T> assertRecursiveSuccessfulBulkInsert(
    modelCase: RecursiveInsertConflictModelCase<T>,
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) {
    val values = List(3, init = modelCase::newValue)
    assertThat(
      operation(
        modelCase.bulkInsert(values = values)
      )
    ).isTrue()

    val actual = captureRows(modelCase.table)
    assertThat(actual)
      .hasSize(values.size)
    assertThat(actual)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = values,
          actual = actual
        )
      )
    assertThat(captureRows(modelCase.relatedTable))
      .containsExactlyElementsIn(values.flatMap(modelCase::relatedValues))
  }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkInsertCases
  }
}
