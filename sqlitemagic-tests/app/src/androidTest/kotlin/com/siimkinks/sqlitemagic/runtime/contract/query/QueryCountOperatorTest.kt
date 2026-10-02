package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class QueryCountOperatorTest : RuntimeDatabaseTest() {
  @Test
  fun emptyCountIsZero() {
    Select
      .from(IMMUTABLE_VALUE_WITH_FIELDS)
      .count()
      .observe()
      .isZero()
      .take(1)
      .test()
      .assertResult(true)
  }

  @Test
  fun emptyCountIsNotZero() {
    Select
      .from(IMMUTABLE_VALUE_WITH_FIELDS)
      .count()
      .observe()
      .isNotZero()
      .take(1)
      .test()
      .assertResult(false)
  }

  @Test
  fun nonEmptyCountIsZero() {
    QueryPredicateCatalog.seed()

    Select
      .from(IMMUTABLE_VALUE_WITH_FIELDS)
      .count()
      .observe()
      .isZero()
      .take(1)
      .test()
      .assertResult(false)
  }

  @Test
  fun nonEmptyCountIsNotZero() {
    QueryPredicateCatalog.seed()

    Select
      .from(IMMUTABLE_VALUE_WITH_FIELDS)
      .count()
      .observe()
      .isNotZero()
      .take(1)
      .test()
      .assertResult(true)
  }
}
