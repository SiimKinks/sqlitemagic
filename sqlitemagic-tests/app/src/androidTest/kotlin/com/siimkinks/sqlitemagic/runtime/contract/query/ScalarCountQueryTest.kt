package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.CountQueryCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarColumnCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
internal class ScalarCountQueryTest(
  private val countQueryCase: CountQueryCase
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

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
    @Parameters(name = "{0}")
    fun countQueryCases() = ScalarColumnCatalog.countCases
  }
}
