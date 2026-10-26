package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.runtime.model.QueryPredicateCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class QueryPredicateQueryTest(
  private val queryCase: QueryPredicateCase
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedValues() {
    NonNullQueryFixture.seed()

    assertThat(
      SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
        .WHERE(queryCase.predicate())
        .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
        .execute()
    ).isEqualTo(queryCase.expectedValues)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun queryCases() = QueryPredicateCatalog.cases
  }
}
