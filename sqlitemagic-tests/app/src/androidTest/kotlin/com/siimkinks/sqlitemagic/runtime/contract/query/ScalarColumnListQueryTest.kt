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
internal class ScalarColumnListQueryTest(
  private val scalarColumnCase: ScalarColumnCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedValues() {
    scalarColumnCase.seed()

    assertExecutedValues(scalarColumnCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedValues() {
    scalarColumnCase.seed()

    assertObservedValues(scalarColumnCase = scalarColumnCase)
  }

  private fun <T> assertExecutedValues(scalarColumnCase: ScalarColumnCase<T>) {
    val actual = scalarColumnCase
      .query()
      .execute()

    assertThat(scalarColumnCase.comparableValues(actual))
      .isEqualTo(scalarColumnCase.comparableValues(scalarColumnCase.expectedValues))
  }

  private fun <T> assertObservedValues(scalarColumnCase: ScalarColumnCase<T>) {
    scalarColumnCase
      .query()
      .observe()
      .runQueryOnce()
      .map(scalarColumnCase::comparableValues)
      .test()
      .assertResult(scalarColumnCase.comparableValues(scalarColumnCase.expectedValues))
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun scalarColumnCases() = ScalarColumnCatalog.cases
  }
}
