package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.CountQueryCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarColumnCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class ScalarCountQueryTest(
  private val countQueryCase: CountQueryCase
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedCount() {
    countQueryCase.seed()

    assertThat(countQueryCase.execute())
      .isEqualTo(countQueryCase.expectedCount)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedCount() {
    countQueryCase.seed()

    countQueryCase
      .observeOnce()
      .test()
      .assertResult(countQueryCase.expectedCount)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun countQueryCases() = ScalarColumnCatalog.countCases
  }
}
