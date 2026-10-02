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
class TypedCursorQueryTest(
  private val modelCase: InsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeMapsExpectedModels() {
    assertExecutedModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceMapsExpectedModels() {
    assertObservedModels(modelCase = modelCase)
  }

  private fun <T> assertExecutedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )
    val cursorSelect = Select
      .from(modelCase.table)
      .toCursor()
    val actual = readTypedCursor(
      cursorSelect = cursorSelect,
      cursor = cursorSelect.execute()
    )

    assertThat(actual).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedModels(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )
    val cursorSelect = Select
      .from(modelCase.table)
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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
