package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ScalarColumnCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarColumnCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
internal class ScalarColumnFirstQueryTest(
  private val scalarColumnCase: ScalarColumnCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsFirstExpectedValue() {
    scalarColumnCase.seed()

    assertExecutedFirst(scalarColumnCase)
  }

  @Test
  fun observeRunQueryOnceReturnsFirstExpectedValue() {
    scalarColumnCase.seed()

    assertObservedFirst(scalarColumnCase)
  }

  private fun <T> assertExecutedFirst(scalarColumnCase: ScalarColumnCase<T>) {
    val actual = scalarColumnCase
      .query()
      .takeFirst()
      .execute()

    assertThat(scalarColumnCase.comparableValue(actual))
      .isEqualTo(scalarColumnCase.comparableValue(scalarColumnCase.expectedValues.firstOrNull()))
  }

  private fun <T> assertObservedFirst(scalarColumnCase: ScalarColumnCase<T>) {
    val observer = scalarColumnCase
      .query()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .map(scalarColumnCase::comparableValue)
      .test()

    when (val expected = scalarColumnCase.comparableValue(scalarColumnCase.expectedValues.firstOrNull())) {
      null -> observer.assertResult()
      else -> observer.assertResult(expected)
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun scalarColumnCases() = ScalarColumnCatalog.cases
  }
}
