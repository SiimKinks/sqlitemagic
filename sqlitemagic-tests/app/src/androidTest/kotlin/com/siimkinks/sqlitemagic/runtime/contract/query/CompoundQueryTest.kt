package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.UNION
import com.siimkinks.sqlitemagic.WITH
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import org.junit.Rule
import org.junit.Test

internal class CompoundQueryTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private val table = IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS

  @Test
  fun modelUnionRemovesOverlappingRow() {
    val rows = seedRows()
    val upTo = SELECT FROM table WHERE (table.INTEGER LESS_OR_EQUAL 202)
    val from = SELECT FROM table WHERE (table.INTEGER GREATER_OR_EQUAL 202)

    assertThat(
      (upTo UNION from)
        .ORDER_BY(table.ID.ASC())
        .execute()
    ).isEqualTo(rows)
  }

  @Test
  fun partialEntityUnionReadsTheSelectedFields() {
    val expected = seedRows().map {
      it.copy(
        aBoolean = null,
        integer = null
      )
    }
    val upTo = SELECT
      .COLUMNS(table.ID, table.STRING)
      .FROM(table)
      .WHERE(table.INTEGER LESS_OR_EQUAL 202)
      .mapTo(table)
    val from = SELECT
      .COLUMNS(table.ID, table.STRING)
      .FROM(table)
      .WHERE(table.INTEGER GREATER_OR_EQUAL 202)
      .mapTo(table)

    assertThat(
      (upTo UNION from)
        .ORDER_BY(table.ID.ASC())
        .execute()
    ).isEqualTo(expected)
  }

  @Test
  fun withDeclarationsRunOnTheEngineAndReadThroughTheirQueries() {
    val rows = seedRows()
    val upTo = WITH(SELECT FROM table WHERE (table.INTEGER LESS_OR_EQUAL 202) AS "low") { low ->
      (SELECT FROM low).ORDER_BY(low[table.ID].ASC())
    }
    val high = SELECT
      .COLUMN(table.ID)
      .FROM(table)
      .WHERE(table.INTEGER GREATER_OR_EQUAL 202)
      .asCte()
    val highStrings = SELECT
      .COLUMN(table.STRING)
      .FROM(table)
      .WHERE(table.ID IN SELECT.COLUMN(high[table.ID]).FROM(high))
      .asCte()
    val chained = WITH(high, highStrings) {
      (SELECT.COLUMN(highStrings[table.STRING]).FROM(highStrings) UNION SELECT.COLUMN(table.STRING).FROM(table))
        .ORDER_BY(highStrings[table.STRING].ASC())
    }

    assertThat(
      listOf(
        upTo.execute(),
        chained.execute()
      )
    ).isEqualTo(
      listOf(
        rows.take(2),
        listOf("compound-1", "compound-2", "compound-3")
      )
    )
  }

  private fun seedRows() = listOf("compound-1" to 101, "compound-2" to 202, "compound-3" to 303)
    .map { (string, integer) ->
      val row = ImmutableValueWithNullableFields(
        id = null,
        string = string,
        aBoolean = integer % 2 == 0,
        integer = integer
      )
      row.copy(
        id = insertedRowId(
          result = row
            .insert()
            .execute(),
          modelName = "compound query seed"
        )
      )
    }
}
