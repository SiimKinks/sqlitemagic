package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryObservationScenario
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SubqueryQueryTest(
  private val queryCase: QueryObservationCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExactResult() {
    assertExactResult(scenario = queryCase.newScenario())
  }

  private fun assertExactResult(scenario: QueryObservationScenario<*>) {
    assertThat(scenario.query().execute())
      .isEqualTo(scenario.initial)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun queryCases() = QueryObservationCatalog.subqueryCases
  }
}
