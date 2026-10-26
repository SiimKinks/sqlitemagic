package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import io.reactivex.observers.TestObserver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class MultiTableQueryObservationTest(
  private val queryCase: QueryObservationCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun eachDependencyRefreshesAndUnrelatedWriteDoesNot() {
    val scenario = queryCase.newScenario()
    val observer = scenario
      .query()
      .observe()
      .runQuery()
      .subscribeWith(TestObserver())
    try {
      observer.assertValuesOnly(scenario.initial)
      NonNullQueryFixture.seed()
      observer
        .withTag("${queryCase.name}: unrelated write")
        .assertValuesOnly(scenario.initial)
      val expected = mutableListOf(scenario.initial)
      scenario.mutations.forEach { mutation ->
        mutation.mutate()
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
        mutation.mutate()
        observer
          .withTag("${queryCase.name}: ${mutation.name} after disposal")
          .assertValuesOnly(scenario.initial)
      }
    } finally {
      observer.dispose()
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun queryCases() = QueryObservationCatalog.cases
  }
}
