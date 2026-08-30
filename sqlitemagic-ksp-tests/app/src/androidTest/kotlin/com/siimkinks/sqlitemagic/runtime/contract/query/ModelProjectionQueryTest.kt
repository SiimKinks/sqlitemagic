package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class ModelProjectionQueryTest(
  private val modelCase: SuccessfulModelProjectionCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedProjectedModels() {
    assertExecutedModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedProjectedModels() {
    assertObservedModels(modelCase = modelCase)
  }

  @Test
  fun executeTakeFirstReturnsExpectedProjectedModel() {
    assertFirstExecute(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceTakeFirstReturnsExpectedProjectedModel() {
    assertFirstObserved(modelCase = modelCase)
  }

  private fun <T> assertExecutedModels(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    ).map(modelCase::expectedAfterProjection)

    assertThat(
      Select
        .columns(*modelCase.projectionColumns.toTypedArray())
        .from(modelCase.table)
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedModels(modelCase: SuccessfulModelProjectionCase<T>) {
    val expected = seedExpectedRows(
      modelCase = modelCase,
      count = 3
    ).map(modelCase::expectedAfterProjection)

    assertThat(
      Select
        .columns(*modelCase.projectionColumns.toTypedArray())
        .from(modelCase.table)
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
      Select
        .columns(*modelCase.projectionColumns.toTypedArray())
        .from(modelCase.table)
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

    Select
      .columns(*modelCase.projectionColumns.toTypedArray())
      .from(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.successfulModelProjectionCases
  }
}
