package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class TextFunctionSelectTest {
  @Test
  fun `helpers render uppercase calls, bind their arguments and read native results`() {
    val query = SELECT
      .COLUMNS(
        LOWER(PERSON.NAME),
        UPPER(PERSON.NAME),
        LENGTH(PERSON.NAME),
        SUBSTRING(PERSON.NAME, 2, 3),
        TRIM(PERSON.NAME, "A"),
        REPLACE(PERSON.NAME, "a", "_"),
        FORMAT("%s is %d", PERSON.NAME, PERSON.AGE),
        ABS(PERSON.AGE - 50)
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val plan = query.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT LOWER("person"."name"), UPPER("person"."name"), LENGTH("person"."name"), """ +
            """SUBSTR("person"."name", ?, ?), TRIM("person"."name", ?), REPLACE("person"."name", ?, ?), """ +
            """PRINTF(?, "person"."name", "person"."age"), ABS("person"."age" - ?) FROM """ +
            """"main"."person" ORDER BY "person"."id" ASC""",
        arguments = listOf(
          NativeValue.Integer(2L),
          NativeValue.Integer(3L),
          NativeValue.Text("A"),
          NativeValue.Text("a"),
          NativeValue.Text("_"),
          NativeValue.Text("%s is %d"),
          NativeValue.Integer(50L)
        )
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row8("ada", "ADA", 3L, "da", "da", "Ad_", "Ada is 36", 14L),
          Row8("grace", "GRACE", 5L, "rac", "Grace", "Gr_ce", "Grace is 85", 35L),
          Row8(null, null, null, null, null, null, " is 17", 33L),
          Row8("linus", "LINUS", 5L, "inu", "Linus", "Linus", "Linus is 17", 33L)
        )
      )
    }
  }

  @Test
  fun `CONCAT joins any values as text and is NULL when any part is`() {
    val query = SELECT
      .COLUMNS(PERSON.NAME CONCAT " #" CONCAT PERSON.ID, "Mr " CONCAT PERSON.NAME, PERSON.AGE CONCAT PERSON.FLAG)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val bytes = SELECT
      .COLUMN(SUBSTRING(CAST(PERSON.NAME AS BLOB), 1, 2))
      .FROM(PERSON)
      .WHERE(PERSON.ID IS 1L)
      .mapTo { it?.decodeToString() }

    assertThat(query.compile().sql).isEqualTo(
      """SELECT ("person"."name" || ?) || "person"."id", ? || "person"."name", """ +
          """"person"."age" || "person"."flag" FROM "main"."person" ORDER BY "person"."id" ASC"""
    )
    personDatabase().use { database ->
      assertThat(
        listOf(
          query.rows(database),
          bytes.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(
            Row3("Ada #1", "Mr Ada", "361"),
            Row3("Grace #2", "Mr Grace", "850"),
            Row3(null, null, null),
            Row3("Linus #4", "Mr Linus", "171")
          ),
          listOf("Ad")
        )
      )
    }
  }

  @Test
  fun `COLLATE compares and orders with the collation and SQLite reports unknown ones`() {
    val nocase = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE((PERSON.NAME COLLATE NOCASE) IS "ADA")
    val registered = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE((PERSON.NAME COLLATE collation("NOCASE")) IS "grace")
    val ordered = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(PERSON.NAME.IS_NOT_NULL())
      .ORDER_BY((LOWER(PERSON.NAME) COLLATE BINARY).DESC())
    val unknown = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE((PERSON.NAME COLLATE collation("nope")) IS "x")

    assertThat(
      listOf(
        nocase.compile().sql,
        registered.compile().sql,
        ordered.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "person"."id" FROM "main"."person" WHERE "person"."name" COLLATE NOCASE = ?""",
        """SELECT "person"."id" FROM "main"."person" WHERE "person"."name" COLLATE "NOCASE" = ?""",
        """SELECT "person"."name" FROM "main"."person" WHERE "person"."name" IS NOT NULL """ +
            """ORDER BY LOWER("person"."name") COLLATE BINARY DESC"""
      )
    )
    personDatabase().use { database ->
      assertThat(
        listOf(
          nocase.rows(database),
          registered.rows(database),
          ordered.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(1L),
          listOf(2L),
          listOf("Linus", "Grace", "Ada")
        )
      )
      assertThat(sqliteErrorMessage { unknown.rows(database) }).contains("no such collation sequence: nope")
    }
  }
}
