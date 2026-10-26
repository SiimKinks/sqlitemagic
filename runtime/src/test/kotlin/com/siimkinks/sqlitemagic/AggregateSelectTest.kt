package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class AggregateSelectTest {
  private fun summary(where: SqlExpression<Boolean, *, *>) = SELECT
    .COLUMNS(
      COUNT(),
      COUNT(PERSON.NAME),
      COUNT(DISTINCT(PERSON.AGE)),
      MIN(PERSON.NAME),
      MAX(PERSON.TOKEN),
      AVG(PERSON.AGE),
      SUM(PERSON.AGE),
      TOTAL(PERSON.AGE),
      GROUP_CONCAT(PERSON.NAME, ", ")
    )
    .FROM(PERSON)
    .WHERE(where)

  @Test
  fun `aggregates read SQLite's result types and no-row results`() {
    val all = summary(PERSON.ID GREATER_THAN 0L)
    val none = summary(PERSON.ID GREATER_THAN 100L)
    val plan = all.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT COUNT(*), COUNT("person"."name"), COUNT(DISTINCT "person"."age"), """ +
            """MIN("person"."name"), MAX("person"."token"), AVG("person"."age"), SUM("person"."age"), """ +
            """TOTAL("person"."age"), GROUP_CONCAT("person"."name", ?) FROM "main"."person" """ +
            """WHERE "person"."id" > ?""",
        arguments = listOf(NativeValue.Text(", "), NativeValue.Integer(0L))
      )
    )
    personDatabase().use { database ->
      assertThat(
        listOf(
          all.rows(database),
          none.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(Row9(4L, 3L, 3L, "Ada", Token("n"), 38.75, 155L, 155.0, "Ada, Grace, Linus")),
          listOf(Row9(0L, 0L, 0L, null, null, null, null, 0.0, null))
        )
      )
    }
  }

  @Test
  fun `DISTINCT arguments render inside their aggregate and SUM follows SQLite`() {
    val query = SELECT
      .COLUMNS(
        SUM(DISTINCT(PERSON.AGE)),
        TOTAL(DISTINCT(PERSON.AGE)),
        AVG(DISTINCT(PERSON.AGE)),
        MIN(DISTINCT(PERSON.AGE)),
        MAX(DISTINCT(PERSON.NAME)),
        GROUP_CONCAT(DISTINCT(PERSON.AGE)),
        SUM(CAST(PERSON.AGE AS REAL))
      )
      .FROM(PERSON)
    val overflow = SELECT
      .COLUMN(SUM(9223372036854775807L.asValue()))
      .FROM(PERSON)
    val safe = SELECT
      .COLUMN(TOTAL(9223372036854775807L.asValue()))
      .FROM(PERSON)

    assertThat(query.compile().sql).isEqualTo(
      """SELECT SUM(DISTINCT "person"."age"), TOTAL(DISTINCT "person"."age"), """ +
          """AVG(DISTINCT "person"."age"), MIN(DISTINCT "person"."age"), MAX(DISTINCT "person"."name"), """ +
          """GROUP_CONCAT(DISTINCT "person"."age"), SUM(CAST("person"."age" AS REAL)) FROM "main"."person""""
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(Row7(138L, 138.0, 46.0, 17, "Linus", "36,85,17", 155.0))
      )
      assertThat(sqliteErrorMessage { overflow.rows(database) }).contains("integer overflow")
      assertThat(safe.rows(database)).isEqualTo(listOf(3.6893488147419103E19))
    }
  }

  @Test
  fun `aggregates work in HAVING, ordering and host comparisons`() {
    val grouped = SELECT
      .COLUMNS(PERSON.AGE, COUNT(), GROUP_CONCAT(PERSON.ID))
      .FROM(PERSON)
      .GROUP_BY(PERSON.AGE)
      .HAVING((COUNT() GREATER_OR_EQUAL 1L) AND (AVG(PERSON.ID) LESS_THAN 4.0))
      .ORDER_BY(COUNT().DESC(), PERSON.AGE.ASC())
    val plan = grouped.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."age", COUNT(*), GROUP_CONCAT("person"."id") FROM "main"."person" """ +
            """GROUP BY "person"."age" HAVING (COUNT(*) >= ?) AND (AVG("person"."id") < ?) """ +
            """ORDER BY COUNT(*) DESC, "person"."age" ASC""",
        arguments = listOf(NativeValue.Integer(1L), NativeValue.Real(4.0))
      )
    )
    personDatabase().use { database ->
      assertThat(grouped.rows(database)).isEqualTo(
        listOf(
          Row3(17, 2L, "3,4"),
          Row3(36, 1L, "1"),
          Row3(85, 1L, "2")
        )
      )
    }
  }

  @Test
  fun `aggregates nest in queries`() {
    val booksPerAuthor = SELECT
      .COLUMNS(AUTHOR.NAME, (SELECT COLUMN COUNT() FROM BOOK WHERE (BOOK.AUTHOR IS AUTHOR.ID)).asScalar())
      .FROM(AUTHOR)
      .ORDER_BY(AUTHOR.ID.ASC())
    val prolific = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
      .WHERE(
        AUTHOR.ID IN SELECT
          .COLUMN(BOOK.AUTHOR)
          .FROM(BOOK)
          .GROUP_BY(BOOK.AUTHOR)
          .HAVING(COUNT() GREATER_THAN 1L)
      )

    libraryDatabaseWithNotes().use { database ->
      assertThat(
        listOf(
          booksPerAuthor.rows(database),
          prolific.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(
            Row2("Ada", 2L),
            Row2("Grace", 1L),
            Row2("Linus", 1L)
          ),
          listOf("Ada")
        )
      )
    }
  }

  @Test
  fun `aggregate misuse is SQLite's error`() {
    val misused = SELECT
      .COLUMN(PERSON.NAME)
      .FROM(PERSON)
      .WHERE(COUNT() GREATER_THAN 1L)

    assertThat(misused.compile().sql).isEqualTo(
      """SELECT "person"."name" FROM "main"."person" WHERE COUNT(*) > ?"""
    )
    personDatabase().use { database ->
      assertThat(sqliteErrorMessage { misused.rows(database) }).contains("misuse of aggregate")
    }
  }
}
