package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class ConditionalSelectTest {
  @Test
  fun `COALESCE and IFNULL render their alternatives in SQL order and read contextual null`() {
    val display = COALESCE(PERSON.NAME, "unknown".asValue())
    val tenth: SqlExpression<String, SqlNullable, String?> = COALESCE(
      PERSON.NAME, PERSON.NAME, PERSON.NAME, PERSON.NAME, PERSON.NAME, PERSON.NAME, PERSON.NAME, PERSON.NAME,
      PERSON.NAME, "tenth".asValue()
    )
    val query = SELECT
      .COLUMNS(display, IFNULL(PERSON.PARENT_ID, PERSON.ID), COALESCE(PERSON.PARENT_ID, NULL), tenth)
      .FROM(PERSON)
      .WHERE(COALESCE(PERSON.FLAG, true.asValue()))
      .ORDER_BY(display.DESC())
    val plan = query.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT COALESCE("person"."name", ?), IFNULL("person"."parent_id", "person"."id"), """ +
            """COALESCE("person"."parent_id", NULL), COALESCE("person"."name", "person"."name", """ +
            """"person"."name", "person"."name", "person"."name", "person"."name", """ +
            """"person"."name", "person"."name", "person"."name", ?) FROM "main"."person" """ +
            """WHERE COALESCE("person"."flag", ?) ORDER BY COALESCE("person"."name", ?) DESC""",
        arguments = listOf(
          NativeValue.Text("unknown"),
          NativeValue.Text("tenth"),
          NativeValue.Integer(1L),
          NativeValue.Text("unknown")
        )
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row4("unknown", 2L, 2L, "tenth"),
          Row4("Linus", 4L, 4L, "Linus"),
          Row4("Ada", 1L, null, "Ada")
        )
      )
    }
  }

  @Test
  fun `NULLIF reads its first operand's decoder and null on equality`() {
    val query = SELECT
      .COLUMNS(NULLIF(PERSON.AGE, 17), NULLIF(PERSON.PARENT_ID, PERSON.ID), NULLIF(PERSON.TOKEN, Token("a")))
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val plan = query.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT NULLIF("person"."age", ?), NULLIF("person"."parent_id", "person"."id"), """ +
            """NULLIF("person"."token", ?) FROM "main"."person" ORDER BY "person"."id" ASC""",
        arguments = listOf(NativeValue.Integer(17L), NativeValue.Text("db:a"))
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row3(36, null, null),
          Row3(85, 1L, Token("g")),
          Row3(null, 2L, Token("n")),
          Row3(null, null, Token("l"))
        )
      )
    }
  }

  @Test
  fun `NULLIF results compare with host values through their first operand's encoder`() {
    val query = SELECT.COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(NULLIF(PERSON.TOKEN, Token("a")) IS Token("g"))

    assertThat(snapshot(query.compile())).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."id" FROM "main"."person" WHERE NULLIF("person"."token", ?) = ?""",
        arguments = listOf(
          NativeValue.Text("db:a"),
          NativeValue.Text("db:g")
        )
      )
    )
    assertThat(query.rowsIn(::personDatabase)).isEqualTo(listOf(2L))
  }

  @Test
  fun `CASE renders its branches in SQL order and reads null without a matching branch`() {
    val stage = CASE
      .WHEN(PERSON.AGE GREATER_OR_EQUAL 80)
      .THEN("senior".asValue())
      .WHEN(PERSON.AGE GREATER_OR_EQUAL 18)
      .THEN("adult".asValue())
      .ELSE("minor".asValue())
      .END()
    val flagged = (CASE(PERSON.FLAG) WHEN true THEN "yes".asValue() WHEN false THEN "no".asValue()).END()
    val parent = CASE(PERSON.PARENT_ID)
      .WHEN(PERSON.ID)
      .THEN("self".asValue())
      .ELSE(PERSON.NAME)
      .END()
    val query = SELECT
      .COLUMNS(stage, flagged, parent)
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val plan = query.compile()
    val adult = CASE
      .WHEN(PERSON.AGE GREATER_OR_EQUAL 18)
      .THEN(PERSON.AGE)
    val reused = listOf(
      adult
        .ELSE(0.asValue())
        .END(),
      adult
        .WHEN(PERSON.NAME.IS_NULL())
        .THEN(PERSON.ID)
        .END()
    ).map { expression ->
      SELECT
        .COLUMN(expression)
        .FROM(PERSON)
        .compile()
        .sql
    }

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT CASE WHEN "person"."age" >= ? THEN ? WHEN "person"."age" >= ? THEN ? ELSE ? END, """ +
            """CASE "person"."flag" WHEN ? THEN ? WHEN ? THEN ? END, """ +
            """CASE "person"."parent_id" WHEN "person"."id" THEN ? ELSE "person"."name" END """ +
            """FROM "main"."person" ORDER BY "person"."id" ASC""",
        arguments = listOf(
          NativeValue.Integer(80L),
          NativeValue.Text("senior"),
          NativeValue.Integer(18L),
          NativeValue.Text("adult"),
          NativeValue.Text("minor"),
          NativeValue.Integer(1L),
          NativeValue.Text("yes"),
          NativeValue.Integer(0L),
          NativeValue.Text("no"),
          NativeValue.Text("self")
        )
      )
    )
    assertThat(reused).isEqualTo(
      listOf(
        """SELECT CASE WHEN "person"."age" >= ? THEN "person"."age" ELSE ? END FROM "main"."person"""",
        """SELECT CASE WHEN "person"."age" >= ? THEN "person"."age" WHEN "person"."name" IS NULL """ +
            """THEN "person"."id" END FROM "main"."person""""
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row3("adult", "yes", "Ada"),
          Row3("senior", "no", "Grace"),
          Row3("minor", null, null),
          Row3("minor", "yes", "self")
        )
      )
    }
  }

  @Test
  fun `mixed native numbers read each value as its stored Long or Double`() {
    val query = SELECT
      .COLUMNS(
        COALESCE(PERSON.PARENT_ID, 2.5.asValue()),
        CASE
          .WHEN(PERSON.ID IS 1L)
          .THEN(9007199254740993L.asValue())
          .WHEN(PERSON.ID IS 2L)
          .THEN(PERSON.AGE)
          .ELSE(0.25.asValue())
          .END(),
        IFNULL(PERSON.PARENT_ID + 1L, PERSON.AGE)
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())

    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row3(2.5, 9007199254740993L, 36L),
          Row3(1L, 85L, 2L),
          Row3(2L, 0.25, 3L),
          Row3(4L, 0.25, 5L)
        )
      )
    }
  }

  @Test
  fun `values that decode differently reject when built`() {
    val adult = PERSON.AGE GREATER_OR_EQUAL 18

    assertThat(
      listOf(
        assertThrows(IllegalArgumentException::class.java) { COALESCE(PERSON.NAME, PERSON.ID) }.message,
        assertThrows(IllegalArgumentException::class.java) {
          IFNULL(PERSON.TOKEN, "a".asValue())
        }.message,
        assertThrows(IllegalArgumentException::class.java) {
          NULLIF(PERSON.NAME, PERSON.AGE)
        }.message,
        assertThrows(IllegalArgumentException::class.java) {
          CASE
            .WHEN(adult)
            .THEN(PERSON.FLAG)
            .ELSE(1.asValue())
        }.message
      )
    ).isEqualTo(
      listOf(
        "COALESCE values must decode alike: one type with one transformer, or native numbers read as Number",
        "IFNULL values must decode alike: one type with one transformer, or native numbers read as Number",
        "NULLIF values must decode alike: one type with one transformer, or native numbers read as Number",
        "CASE values must decode alike: one type with one transformer, or native numbers read as Number"
      )
    )
    personDatabase().use { database ->
      assertThat(
        SELECT
          .COLUMN(COALESCE(PERSON.TOKEN, Token("z").asValue(TOKEN_TYPE)))
          .FROM(PERSON)
          .ORDER_BY(PERSON.ID.ASC())
          .rows(database)
      ).isEqualTo(listOf(Token("a"), Token("g"), Token("n"), Token("l")))
    }
  }

  @Test
  fun `conditionals work in every expression position`() {
    val title = COALESCE(BOOK.TITLE.nullable(), "none".asValue())
    val dune = SELECT
      .COLUMNS(AUTHOR.NAME, title)
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON((BOOK.AUTHOR IS AUTHOR.ID) AND (BOOK.TITLE IS "Dune"))
      .ORDER_BY(AUTHOR.ID.ASC())
    val unlifted = SELECT
      .COLUMN(COALESCE(BOOK.TITLE, "none".asValue()))
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON(BOOK.AUTHOR IS AUTHOR.ID)
    val stage = (CASE WHEN (PERSON.AGE GREATER_OR_EQUAL 18) THEN "adult".asValue() ELSE "minor".asValue()).END()
    val counted = SELECT
      .COLUMN(stage)
      .FROM(PERSON)
      .GROUP_BY(stage)
      .ORDER_BY(stage.ASC())

    assertThat(dune.compile().sql).isEqualTo(
      """SELECT "author"."name", COALESCE("book"."title", ?) FROM "main"."author" LEFT JOIN """ +
          """"main"."book" ON ("book"."author" = "author"."id") AND ("book"."title" = ?) """ +
          """ORDER BY "author"."id" ASC"""
    )
    assertThat(assertThrows(IllegalArgumentException::class.java, unlifted::compile))
      .hasMessageThat()
      .isEqualTo("book.title is NOT NULL but an outer join can leave it NULL; select the field with .nullable()")
    libraryDatabase().use { database ->
      val connection = database.dbConnection()
      val renamed = UPDATE
        .TABLE(PERSON)
        .SET(PERSON.NAME to COALESCE(PERSON.NAME, "anonymous".asValue()))
        .WHERE(NULLIF(PERSON.AGE, 17).IS_NULL())
        .usingConnection(connection)
        .execute()

      assertThat(
        listOf(
          dune.rows(database),
          counted.rows(database),
          listOf(renamed),
          SELECT
            .COLUMN(PERSON.NAME)
            .FROM(PERSON)
            .ORDER_BY(PERSON.ID.ASC())
            .rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(
            Row2("Ada", "Dune"),
            Row2("Grace", "none"),
            Row2("Linus", "none")
          ),
          listOf("adult", "minor"),
          listOf(2),
          listOf("Ada", "Grace", "anonymous", "Linus")
        )
      )
    }
  }
}
