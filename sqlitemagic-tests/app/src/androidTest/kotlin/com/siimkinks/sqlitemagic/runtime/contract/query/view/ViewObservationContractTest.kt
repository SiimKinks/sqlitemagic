package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.ViewObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ViewIntegrationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class ViewObservationContractTest(
  private val observationCase: ViewObservationCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun observesInsertAndUpdateThenStopsAfterDisposal() = assertObservation(observationCase)

  private fun <T> assertObservation(observationCase: ViewObservationCase<T>) {
    val observer = SELECT
      .FROM(observationCase.table)
      .observe()
      .runQuery()
      .test()
    try {
      observer.assertValuesOnly(emptyList())
      insertedResult(
        result = observationCase.insertSource(),
        modelName = observationCase.name
      )
      observer.assertValuesOnly(emptyList(), observationCase.insertedExpected)

      assertThat(observationCase.updateSource()).isTrue()
      observer.assertValuesOnly(
        emptyList(),
        observationCase.insertedExpected,
        observationCase.updatedExpected
      )

      observer.dispose()
      insertedResult(
        result = observationCase.insertAfterDisposal(),
        modelName = observationCase.name
      )
      observer.assertValuesOnly(
        emptyList(),
        observationCase.insertedExpected,
        observationCase.updatedExpected
      )
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun observationCases() = ViewIntegrationCatalog.observationCases
  }
}
