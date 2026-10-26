package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
internal class ModelProjectionQueryTest(
  private val modelCase: SuccessfulModelProjectionCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedProjectedModels() = assertExecutedModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedProjectedModels() = assertObservedModels(modelCase = modelCase)

  @Test
  fun executeTakeFirstReturnsExpectedProjectedModel() = assertFirstExecute(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceTakeFirstReturnsExpectedProjectedModel() = assertFirstObserved(modelCase = modelCase)

  private fun <T> assertExecutedModels(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    ).map(modelCase::expectedAfterProjection)

    assertThat(
      modelCase
        .projection()
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedModels(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    ).map(modelCase::expectedAfterProjection)

    assertThat(
      modelCase
        .projection()
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertFirstExecute(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    )
      .map(modelCase::expectedAfterProjection)
      .single()

    assertThat(
      modelCase
        .projection()
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstObserved(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 1
    )
      .map(modelCase::expectedAfterProjection)
      .single()

    modelCase
      .projection()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.successfulModelProjectionCases
  }
}
