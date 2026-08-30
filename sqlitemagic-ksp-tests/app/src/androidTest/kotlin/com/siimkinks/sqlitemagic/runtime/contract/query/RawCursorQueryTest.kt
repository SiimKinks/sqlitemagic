package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledObservableRawSelect
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RawCursorModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RawCursorQueryTest(
  private val modelCase: RawCursorModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedModels() {
    assertExecutedModels(modelCase = modelCase)
  }

  @Test
  fun executeWithArgsReturnsMatchingModel() {
    assertExecutedModelWithArgs(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedModels() {
    assertObservedModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceWithArgsReturnsMatchingModel() {
    assertObservedModelWithArgs(modelCase = modelCase)
  }

  private fun <T> assertExecutedModels(modelCase: RawCursorModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertRawModels(
      modelCase = modelCase,
      query = modelCase.rawSelect(),
      expected = expected
    )
  }

  private fun <T> assertExecutedModelWithArgs(modelCase: RawCursorModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )[1]

    assertRawModels(
      modelCase = modelCase,
      query = modelCase.rawSelectWithArgs(value = expected),
      expected = listOf(expected)
    )
  }

  private fun <T> assertObservedModels(modelCase: RawCursorModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )

    assertObservedRawModels(
      modelCase = modelCase,
      query = modelCase.rawSelect(),
      expected = expected
    )
  }

  private fun <T> assertObservedModelWithArgs(modelCase: RawCursorModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    )[1]

    assertObservedRawModels(
      modelCase = modelCase,
      query = modelCase.rawSelectWithArgs(value = expected),
      expected = listOf(expected)
    )
  }

  private fun <T> assertRawModels(
    modelCase: RawCursorModelCase<T>,
    query: CompiledObservableRawSelect,
    expected: List<T>
  ) {
    val actual = readRawCursor(
      modelCase = modelCase,
      cursor = query.execute()
    )

    assertThat(actual)
      .containsExactlyElementsIn(expected)
      .inOrder()
  }

  private fun <T> assertObservedRawModels(
    modelCase: RawCursorModelCase<T>,
    query: CompiledObservableRawSelect,
    expected: List<T>
  ) {
    val actual = readRawCursor(
      modelCase = modelCase,
      cursor = query
        .observe()
        .runQueryOnce()
        .blockingGet()
    )

    assertThat(actual)
      .containsExactlyElementsIn(expected)
      .inOrder()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.rawCursorCases
  }
}
