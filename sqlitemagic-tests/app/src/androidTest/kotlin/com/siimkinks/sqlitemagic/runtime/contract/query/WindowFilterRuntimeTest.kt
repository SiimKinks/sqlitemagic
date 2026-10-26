package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.CURRENT_ROW
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ORDER_BY
import com.siimkinks.sqlitemagic.PARTITION_BY
import com.siimkinks.sqlitemagic.PRECEDING
import com.siimkinks.sqlitemagic.ROW_NUMBER
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SUM
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import org.junit.Rule
import org.junit.Test

/**
 * Window functions need SQLite 3.25.0 and aggregate FILTER 3.30.0; an older engine rejects them when it prepares the
 * statement.
 */
internal class WindowFilterRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun windowsAndFiltersRunWhereTheEngineSupportsThem() {
    NonNullQueryFixture.seed()
    val table = IMMUTABLE_VALUE_WITH_FIELDS
    val windowed = SELECT
      .COLUMNS(
        table.INTEGER,
        ROW_NUMBER() OVER PARTITION_BY(table.A_BOOLEAN).ORDER_BY(table.INTEGER.ASC()),
        SUM(table.INTEGER) OVER ORDER_BY(table.INTEGER.ASC()).ROWS_BETWEEN(1.PRECEDING).AND(CURRENT_ROW)
      )
      .FROM(table)
      .ORDER_BY(table.INTEGER.ASC())
    val filtered = SELECT
      .COLUMN(COUNT() FILTER_WHERE (table.INTEGER GREATER_THAN 150))
      .FROM(table)
    val version = sqliteVersion()

    when {
      supportsSqlite(
        version = version,
        minor = 25
      ) -> assertThat(windowed.execute()).isEqualTo(
        listOf(
          Row3(101, 1L, 101L),
          Row3(202, 1L, 303L),
          Row3(303, 2L, 505L)
        )
      )
      else -> assertSqliteRejects(containing = "syntax error") { windowed.execute() }
    }
    when {
      supportsSqlite(
        version = version,
        minor = 30
      ) -> assertThat(filtered.execute()).isEqualTo(listOf(2L))
      else -> assertSqliteRejects(containing = "syntax error") { filtered.execute() }
    }
  }
}
