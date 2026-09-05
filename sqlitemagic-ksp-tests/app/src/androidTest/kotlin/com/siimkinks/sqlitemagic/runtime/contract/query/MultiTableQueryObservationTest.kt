package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import io.reactivex.observers.TestObserver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MultiTableQueryObservationTest(
  private val queryCase: QueryObservationCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun eachDependencyRefreshes() {
    val scenario = queryCase.newScenario()
    val observer = scenario
      .query()
      .observe()
      .runQuery()
      .subscribeWith(TestObserver())
    try {
      observer.assertValuesOnly(scenario.initial)
      val expected = mutableListOf(scenario.initial)
      scenario.mutations.forEach { mutation ->
        mutation.apply()
        expected += mutation.expected
        observer
          .withTag("${queryCase.name}: ${mutation.name}")
          .assertValuesOnly(*expected.toTypedArray())
      }
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun unrelatedDoesNotRefresh() {
    val scenario = queryCase.newScenario()
    val observer = scenario
      .query()
      .observe()
      .runQuery()
      .subscribeWith(TestObserver())
    try {
      observer.assertValuesOnly(scenario.initial)
      scenario.unrelatedWrite()
      observer.assertValuesOnly(scenario.initial)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun disposalStopsRefresh() {
    val scenario = queryCase.newScenario()
    val observer = scenario
      .query()
      .observe()
      .runQuery()
      .subscribeWith(TestObserver())
    try {
      observer.assertValuesOnly(scenario.initial)
      observer.dispose()
      scenario.mutations.forEach { mutation ->
        mutation.apply()
        observer
          .withTag("${queryCase.name}: ${mutation.name} after disposal")
          .assertValuesOnly(scenario.initial)
      }
      assertThat(observer.isDisposed).isTrue()
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun queryCases() = QueryObservationCatalog.cases
  }
}
