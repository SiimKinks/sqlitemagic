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
class ScalarColumnFirstQueryTest(
  private val scalarColumnCase: ScalarColumnCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsFirstExpectedValue() {
    scalarColumnCase.seed()

    val actual = scalarColumnCase
      .query()
      .takeFirst()
      .execute()

    assertThat(actual).isEqualTo(scalarColumnCase.expectedValues.firstOrNull())
  }

  @Test
  fun observeRunQueryOnceReturnsFirstExpectedValue() {
    scalarColumnCase.seed()

    assertObservedFirst(scalarColumnCase)
  }

  private fun <T> assertObservedFirst(scalarColumnCase: ScalarColumnCase<T>) {
    val observer = scalarColumnCase
      .query()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()

    when (val expected = scalarColumnCase.expectedValues.firstOrNull()) {
      null -> observer.assertResult()
      else -> observer.assertResult(expected)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun scalarColumnCases() = ScalarColumnCatalog.cases
  }
}
