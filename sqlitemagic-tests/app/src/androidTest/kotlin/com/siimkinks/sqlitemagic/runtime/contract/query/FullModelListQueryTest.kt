package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class FullModelListQueryTest(
  private val modelCase: InsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedModels() = assertExecutedModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedModels() = assertObservedModels(modelCase = modelCase)

  @Test
  fun executeReturnsEmptyModelListForEmptyTable() {
    assertThat(
      SELECT.FROM(modelCase.table)
        .execute()
    ).isEmpty()
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyModelListForEmptyTable() {
    SELECT.FROM(modelCase.table)
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(emptyList())
  }

  private fun <T> assertExecutedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertThat(
      SELECT.FROM(modelCase.table)
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertThat(
      SELECT.FROM(modelCase.table)
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
