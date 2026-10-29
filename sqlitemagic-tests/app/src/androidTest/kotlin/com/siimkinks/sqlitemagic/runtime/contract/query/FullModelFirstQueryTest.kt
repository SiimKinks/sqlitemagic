package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
internal class FullModelFirstQueryTest(
  private val modelCase: InsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsFirstExpectedModel() = assertFirstExecute(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsFirstExpectedModel() = assertFirstObserved(modelCase = modelCase)

  @Test
  fun executeReturnsNullForEmptyTable() {
    assertThat(
      SELECT.FROM(modelCase.table)
        .takeFirst()
        .execute()
    ).isNull()
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTable() {
    SELECT.FROM(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult()
  }

  private fun <T> assertFirstExecute(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    ).single()

    assertThat(
      SELECT.FROM(modelCase.table)
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstObserved(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    ).single()
    SELECT.FROM(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
