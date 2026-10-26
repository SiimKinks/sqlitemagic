package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CAST
import com.siimkinks.sqlitemagic.INTEGER
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TEXT
import com.siimkinks.sqlitemagic.row
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import org.junit.Rule
import org.junit.Test

/** Row values need SQLite 3.15.0; an older engine rejects them when it prepares the statement. */
internal class RowValueCastRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun castsConvertNativelyAndRowValuesCompareWhereTheEngineSupportsThem() {
    NonNullQueryFixture.seed()
    val table = IMMUTABLE_VALUE_WITH_FIELDS
    val casts = SELECT
      .COLUMNS(CAST(table.INTEGER AS TEXT), CAST(table.A_DOUBLE AS INTEGER))
      .FROM(table)
      .WHERE(CAST(table.INTEGER AS TEXT) IS "202")
    val keyset = SELECT
      .COLUMN(table.INTEGER)
      .FROM(table)
      .WHERE(row(table.STRING_VALUE, table.INTEGER) GREATER_THAN row("non-null-string-2", 202))
      .ORDER_BY(table.INTEGER.ASC())
    val version = sqliteVersion()

    assertThat(casts.execute()).isEqualTo(
      listOf(Row2("202", 2L))
    )
    when {
      supportsSqlite(
        version = version,
        minor = 15
      ) -> assertThat(keyset.execute()).isEqualTo(listOf(303))
      else -> assertSqliteRejects(containing = "syntax error") { keyset.execute() }
    }
  }
}
