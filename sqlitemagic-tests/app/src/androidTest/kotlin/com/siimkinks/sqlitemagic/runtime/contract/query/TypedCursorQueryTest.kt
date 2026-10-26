package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class TypedCursorQueryTest(
  private val modelCase: InsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeMapsExpectedModels() = assertExecutedModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceMapsExpectedModels() = assertObservedModels(modelCase = modelCase)

  private fun <T> assertExecutedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )
    val cursorSelect = SELECT.FROM(modelCase.table)
      .toCursor()
    val actual = readTypedCursor(cursorSelect = cursorSelect)

    assertThat(actual).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )
    val cursorSelect = SELECT.FROM(modelCase.table)
      .toCursor()
    val actual = readTypedCursor(
      cursorSelect = cursorSelect,
      cursor = cursorSelect
        .observe()
        .runQueryOnce()
        .blockingGet()
    )

    assertThat(actual).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
