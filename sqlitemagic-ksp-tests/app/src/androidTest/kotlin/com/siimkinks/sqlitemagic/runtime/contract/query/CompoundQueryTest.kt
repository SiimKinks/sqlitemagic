package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class CompoundQueryTest : RuntimeDatabaseTest() {
  @Test
  fun modelUnionRemovesOverlappingRow() {
    val rows = seedRows()

    assertThat(
      Select
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER LESS_OR_EQUAL 202)
        .union(
          Select
            .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
            .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER GREATER_OR_EQUAL 202)
        )
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(rows)
  }

  @Test
  fun multiColumnUnionMapsProjectedModels() {
    val expected = seedRows()
      .map { it.copy(aBoolean = null, integer = null) }

    assertThat(
      Select
        .columns(
          IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID,
          IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING
        )
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER LESS_OR_EQUAL 202)
        .union(
          Select
            .columns(
              IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID,
              IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING
            )
            .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
            .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER GREATER_OR_EQUAL 202)
        )
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
        .execute()
    ).isEqualTo(expected)
  }

  @Test
  fun scalarUnionRemovesOverlappingValue() {
    seedRows()

    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER LESS_OR_EQUAL 202)
        .union(
          Select
            .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
            .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
            .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER GREATER_OR_EQUAL 202)
        )
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING.asc())
        .execute()
    ).isEqualTo(listOf("compound-1", "compound-2", "compound-3"))
  }

  @Test
  fun scalarUnionAllPreservesOverlappingValue() {
    seedRows()

    assertThat(
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER LESS_OR_EQUAL 202)
        .unionAll(
          Select
            .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
            .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
            .where(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER GREATER_OR_EQUAL 202)
        )
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING.asc())
        .execute()
    ).isEqualTo(listOf("compound-1", "compound-2", "compound-2", "compound-3"))
  }

  private fun seedRows() = listOf(
    row(
      string = "compound-1",
      integer = 101
    ),
    row(
      string = "compound-2",
      integer = 202
    ),
    row(
      string = "compound-3",
      integer = 303
    )
  ).map { row ->
    when (val result = row.insert().execute()) {
      is EntityInsertResult.Inserted -> row.copy(id = checkNotNull(result.rowId))
      EntityInsertResult.Ignored -> error("Compound query seed insert was ignored")
    }
  }

  private fun row(
    string: String,
    integer: Int
  ) = ImmutableValueWithNullableFields(
    id = null,
    string = string,
    aBoolean = integer % 2 == 0,
    integer = integer
  )
}
