package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CASE
import com.siimkinks.sqlitemagic.COALESCE
import com.siimkinks.sqlitemagic.ELSE
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.NULLIF
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.THEN
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test

internal class ConditionalRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun mixedNativeNumbersReadEachStoredValueAndHostMatchesUseTheBaseEncoder() {
    NonNullQueryFixture.seed()
    val table = IMMUTABLE_VALUE_WITH_FIELDS
    val picked = CASE
      .WHEN(table.A_BOOLEAN IS true)
      .THEN(
        COALESCE(
          NULLIF(table.INTEGER, 101),
          9007199254740993L.asValue()
        )
      )
      .ELSE(table.A_DOUBLE)
      .END()
    val label = (CASE(table.TRANSFORMABLE_OBJECT) WHEN TransformableObject(1002) THEN "second".asValue()).END()

    assertThat(
      SELECT
        .COLUMNS(picked, label)
        .FROM(table)
        .ORDER_BY(table.ID.ASC())
        .execute()
    ).isEqualTo(
      listOf(
        Row2(9007199254740993L, null),
        Row2(2.5, "second"),
        Row2(303L, null)
      )
    )
  }
}
