package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class MultiTableQueryObservationTest(
  private val queryCase: QueryObservationCase<*>,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun eachDependencyRefreshesAndUnrelatedWriteDoesNot() = assertEachDependencyRefreshes(queryCase)

  @Test
  fun disposalStopsRefresh() = assertDisposalStopsRefresh(queryCase)

  private fun <T> assertEachDependencyRefreshes(queryCase: QueryObservationCase<T>) {
    val scenario = queryCase.newScenario()
    val observer = adapter
      .runQuery(scenario.query())
      .record()
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

  private fun <T> assertDisposalStopsRefresh(queryCase: QueryObservationCase<T>) {
    val scenario = queryCase.newScenario()
    val observer = adapter
      .runQuery(scenario.query())
      .record()
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
    @Parameters(name = "{0} {1}")
    fun queryCases() = withReactiveAdapters(QueryObservationCatalog.cases)
  }
}
