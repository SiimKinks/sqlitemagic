package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.QueryPredicateCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class QueryPredicateQueryTest(
  private val queryCase: QueryPredicateCase
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .where(queryCase.predicate())
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(queryCase.expectedValues)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .where(queryCase.predicate())
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).isEqualTo(queryCase.expectedValues)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun queryCases() = QueryPredicateCatalog.cases
  }
}
