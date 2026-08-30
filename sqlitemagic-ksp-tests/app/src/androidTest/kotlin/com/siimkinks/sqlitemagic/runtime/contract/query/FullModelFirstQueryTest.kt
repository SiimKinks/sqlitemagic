package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class FullModelFirstQueryTest(
  private val modelCase: InsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsFirstExpectedModel() {
    assertFirstExecute(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsFirstExpectedModel() {
    assertFirstObserved(modelCase = modelCase)
  }

  @Test
  fun executeReturnsNullForEmptyTable() {
    assertEmptyFirstExecute(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTable() {
    assertEmptyFirstObserved(modelCase = modelCase)
  }

  private fun <T> assertFirstExecute(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    ).single()

    assertThat(
      Select
        .from(modelCase.table)
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstObserved(modelCase: InsertModelCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    ).single()
    Select
      .from(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  private fun <T> assertEmptyFirstExecute(modelCase: InsertModelCase<T>) {
    assertThat(
      Select
        .from(modelCase.table)
        .takeFirst()
        .execute()
    ).isNull()
  }

  private fun <T> assertEmptyFirstObserved(modelCase: InsertModelCase<T>) {
    Select
      .from(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.directModelQueryCases
  }
}
