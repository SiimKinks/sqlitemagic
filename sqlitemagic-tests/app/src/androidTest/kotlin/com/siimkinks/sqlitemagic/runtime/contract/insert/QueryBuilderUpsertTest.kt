package com.siimkinks.sqlitemagic.runtime.contract.insert

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EXCLUDED
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntityTable.Companion.NO_ID_MULTI_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.UniqueRelatedEntityTable.Companion.UNIQUE_RELATED_ENTITY
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import org.junit.Rule
import org.junit.Test

internal class QueryBuilderUpsertTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private fun seed() = INSERT
    .INTO(NO_ID_MULTI_UNIQUE_ENTITY)
    .COLUMNS(NO_ID_MULTI_UNIQUE_ENTITY.SLUG, NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY, NO_ID_MULTI_UNIQUE_ENTITY.VALUE)
    .VALUES("a", "x", "first")
    .VALUES("b", "y", "second")
    .execute()

  private fun rows() = SELECT
    .COLUMNS(NO_ID_MULTI_UNIQUE_ENTITY.SLUG, NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY, NO_ID_MULTI_UNIQUE_ENTITY.VALUE)
    .FROM(NO_ID_MULTI_UNIQUE_ENTITY)
    .ORDER_BY(NO_ID_MULTI_UNIQUE_ENTITY.SLUG.ASC())
    .execute()

  @Test
  fun executeDoUpdateDoNothingAndInsertSelectUpserts() {
    seed()
    val statements = listOf(
      INSERT
        .INTO(NO_ID_MULTI_UNIQUE_ENTITY)
        .COLUMNS(
          NO_ID_MULTI_UNIQUE_ENTITY.SLUG,
          NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY,
          NO_ID_MULTI_UNIQUE_ENTITY.VALUE
        )
        .VALUES("a", "n", "updated")
        .ON_CONFLICT(NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
        .DO_UPDATE
        .SET(NO_ID_MULTI_UNIQUE_ENTITY.VALUE, NO_ID_MULTI_UNIQUE_ENTITY.VALUE.EXCLUDED)
        .WHERE(NO_ID_MULTI_UNIQUE_ENTITY.VALUE.EXCLUDED IS_NOT NO_ID_MULTI_UNIQUE_ENTITY.VALUE),
      INSERT
        .INTO(NO_ID_MULTI_UNIQUE_ENTITY)
        .COLUMNS(
          NO_ID_MULTI_UNIQUE_ENTITY.SLUG,
          NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY,
          NO_ID_MULTI_UNIQUE_ENTITY.VALUE
        )
        .VALUES("c", "y", "skipped")
        .ON_CONFLICT(NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
        .DO_NOTHING(),
      INSERT
        .INTO(NO_ID_MULTI_UNIQUE_ENTITY)
        .COLUMNS(
          NO_ID_MULTI_UNIQUE_ENTITY.SLUG,
          NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY,
          NO_ID_MULTI_UNIQUE_ENTITY.VALUE
        )
        .SELECT(
          SELECT
            .COLUMNS(
              NO_ID_MULTI_UNIQUE_ENTITY.SLUG,
              NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY,
              NO_ID_MULTI_UNIQUE_ENTITY.VALUE
            )
            .FROM(NO_ID_MULTI_UNIQUE_ENTITY)
        )
        .ON_CONFLICT()
        .DO_NOTHING()
    )

    when {
      supportsSqlite(
        version = sqliteVersion(),
        minor = 24
      ) -> {
        assertThat(statements.map(MutationQuery::execute)).isEqualTo(listOf(1, 0, 0))
        assertThat(rows()).isEqualTo(listOf(Row3("a", "x", "updated"), Row3("b", "y", "second")))
      }
      else -> statements.forEach { statement ->
        assertSqliteRejects(containing = "syntax error") { statement.execute() }
      }
    }
  }

  @Test
  fun executeSeveralUpsertClauses() {
    seed()
    val upsert = INSERT
      .INTO(NO_ID_MULTI_UNIQUE_ENTITY)
      .COLUMNS(NO_ID_MULTI_UNIQUE_ENTITY.SLUG, NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY, NO_ID_MULTI_UNIQUE_ENTITY.VALUE)
      .VALUES("a", "n", "updated")
      .VALUES("c", "y", "skipped")
      .VALUES("d", "z", "inserted")
      .ON_CONFLICT(NO_ID_MULTI_UNIQUE_ENTITY.SLUG)
      .DO_UPDATE
      .SET(NO_ID_MULTI_UNIQUE_ENTITY.VALUE, NO_ID_MULTI_UNIQUE_ENTITY.VALUE.EXCLUDED)
      .ON_CONFLICT(NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)
      .DO_NOTHING()

    when {
      supportsSqlite(
        version = sqliteVersion(),
        minor = 35
      ) -> {
        assertThat(upsert.execute()).isEqualTo(2)
        assertThat(rows())
          .isEqualTo(
            listOf(
              Row3("a", "x", "updated"),
              Row3("b", "y", "second"),
              Row3("d", "z", "inserted")
            )
          )
      }
      else -> assertSqliteRejects(containing = "syntax error") { upsert.execute() }
    }
  }

  @Test
  fun readRowIdThroughDoNothing() {
    val insert = (INSERT INTO UNIQUE_RELATED_ENTITY COLUMNS UNIQUE_RELATED_ENTITY.UNIQUE_VALUE VALUES "u")
      .ON_CONFLICT(UNIQUE_RELATED_ENTITY.UNIQUE_VALUE)
      .DO_NOTHING()
      .readRowId()

    when {
      supportsSqlite(
        version = sqliteVersion(),
        minor = 24
      ) -> {
        val rowIds = List(2) { insert.execute() }

        assertThat(rowIds)
          .isEqualTo(
            listOf(
              SELECT
                .COLUMN(UNIQUE_RELATED_ENTITY.ID)
                .FROM(UNIQUE_RELATED_ENTITY)
                .WHERE(UNIQUE_RELATED_ENTITY.UNIQUE_VALUE IS "u")
                .execute()
                .single(),
              null
            )
          )
      }
      else -> assertSqliteRejects(containing = "syntax error") { insert.execute() }
    }
  }
}
