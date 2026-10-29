package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.ViewObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ViewIntegrationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class ViewObservationContractTest(
  private val observationCase: ViewObservationCase<*>,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun observesInsertAndUpdateThenStopsAfterDisposal() = assertObservation(observationCase)

  private fun <T> assertObservation(observationCase: ViewObservationCase<T>) {
    val observer = adapter
      .runQuery(SELECT.FROM(observationCase.table))
      .record()
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
    @Parameters(name = "{0} {1}")
    fun observationCases() = withReactiveAdapters(ViewIntegrationCatalog.observationCases)
  }
}
