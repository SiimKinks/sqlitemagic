package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledRawCursor
import com.siimkinks.sqlitemagic.DeclaredTables
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RawCursorModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RawCursorQueryTest(
  private val modelCase: RawCursorModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedModels() = assertExecutedModels(modelCase = modelCase)

  @Test
  fun executeWithArgsReturnsMatchingModel() = assertExecutedModelWithArgs(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedModels() = assertObservedModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceWithArgsReturnsMatchingModel() = assertObservedModelWithArgs(modelCase = modelCase)

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
    query: CompiledRawCursor<DeclaredTables>,
    expected: List<T>
  ) {
    val actual = readRawCursor(
      modelCase = modelCase,
      cursor = query.execute()
    )

    assertThat(actual)
      .isEqualTo(expected)
  }

  private fun <T> assertObservedRawModels(
    modelCase: RawCursorModelCase<T>,
    query: CompiledRawCursor<DeclaredTables>,
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
      .isEqualTo(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.rawCursorCases
  }
}
