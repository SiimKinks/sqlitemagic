package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.ViewObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ViewIntegrationCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ViewObservationContractTest(
  private val observationCase: ViewObservationCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun observesInsertAndUpdateThenStopsAfterDisposal() = assertObservation(observationCase)

  private fun <T> assertObservation(observationCase: ViewObservationCase<T>) {
    val observer = Select
      .from(observationCase.table)
      .observe()
      .runQuery()
      .test()
    try {
      observer.assertValuesOnly(emptyList())
      assertSeedInserted(
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
      assertSeedInserted(
        result = observationCase.insertAfterDisposal(),
        modelName = observationCase.name
      )
      observer.assertValuesOnly(
        emptyList(),
        observationCase.insertedExpected,
        observationCase.updatedExpected
      )
      assertThat(observer.isDisposed).isTrue()
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun observationCases() = ViewIntegrationCatalog.observationCases
  }
}
