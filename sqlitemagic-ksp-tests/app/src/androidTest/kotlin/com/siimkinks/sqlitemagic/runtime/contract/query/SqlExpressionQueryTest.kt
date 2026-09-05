package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.minus
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.rem
import com.siimkinks.sqlitemagic.replace
import com.siimkinks.sqlitemagic.runtime.model.catalog.QueryPredicateCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.times
import org.junit.Test

class SqlExpressionQueryTest : RuntimeDatabaseTest() {
  @Test
  fun numericAggregatesReturnExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(Select.avg(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(202.0)
    assertThat(
      Select
        .column(Select.sum(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(66.0)
    assertThat(
      Select
        .column(Select.min(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo(101)
    assertThat(
      Select
        .column(Select.max(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .takeFirst()
        .execute()
    ).isEqualTo("non-null-string-3")
  }

  @Test
  fun stringFunctionsReturnExpectedValues() {
    QueryPredicateCatalog.seed()

    assertThat(
      Select
        .column(Select.lower(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(
      listOf(
        "non-null-string-1",
        "non-null-string-2",
        "non-null-string-3"
      )
    )
    assertThat(
      Select
        .column(Select.length(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf(17L, 17L, 17L))
    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE.replace("non-null-", ""))
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf("string-1", "string-2", "string-3"))
  }

  @Test
  fun chainedArithmeticReturnsExpectedValues() {
    QueryPredicateCatalog.seed()
    val expression = ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * 2.0) - 4.0) / 2.0) % 100.0

    assertThat(
      Select
        .column(expression)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(listOf(1.0, 2.0, 3.0))
  }

  @Test
  fun rawTextExpressionReturnsExpectedValues() {
    QueryPredicateCatalog.seed()
    val expression = Select.asRawColumn(
      "printf('%s:%d', ${IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE}, ${IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT})"
    )

    assertThat(
      Select
        .column(expression)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(
      listOf(
        "non-null-string-1:11",
        "non-null-string-2:22",
        "non-null-string-3:33"
      )
    )
  }
}
