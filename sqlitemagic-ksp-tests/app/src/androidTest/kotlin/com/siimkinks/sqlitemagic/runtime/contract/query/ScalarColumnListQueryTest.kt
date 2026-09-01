package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ScalarColumnCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarColumnCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class ScalarColumnListQueryTest(
  private val scalarColumnCase: ScalarColumnCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedValues() {
    scalarColumnCase.seed()

    val actual = scalarColumnCase
      .query()
      .execute()

    assertThat(scalarColumnCase.comparableValues(actual))
      .isEqualTo(scalarColumnCase.comparableValues(scalarColumnCase.expectedValues))
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedValues() {
    scalarColumnCase.seed()

    assertObservedValues(scalarColumnCase = scalarColumnCase)
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
    @Parameterized.Parameters(name = "{0}")
    fun scalarColumnCases() = ScalarColumnCatalog.cases
  }
}
