package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FullModelListQueryTest(
  private val modelCase: InsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedModels() {
    assertExecutedModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedModels() {
    assertObservedModels(modelCase = modelCase)
  }

  @Test
  fun executeReturnsEmptyModelListForEmptyTable() {
    assertEmptyModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyModelListForEmptyTable() {
    assertObservedEmptyModels(modelCase = modelCase)
  }

  private fun <T> assertExecutedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertThat(
      Select
        .from(modelCase.table)
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertEmptyModels(modelCase: InsertModelCase<T>) {
    assertThat(
      Select
        .from(modelCase.table)
        .execute()
    ).isEmpty()
  }

  private fun <T> assertObservedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertThat(
      Select
        .from(modelCase.table)
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedEmptyModels(modelCase: InsertModelCase<T>) {
    Select
      .from(modelCase.table)
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(emptyList())
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
