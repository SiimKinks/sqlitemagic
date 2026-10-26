package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class ScalarFunctionSelectTest {
  @Test
  fun `declared functions render quoted names, bind arguments and read through their descriptor`() {
    val instr = scalarFunction("instr").readAs(LONG.nullable)
    val hex = scalarFunction("HEX").readAs(STRING)
    val typeOf = scalarFunction("typeof").readAs(STRING)
    val lowerToken = scalarFunction("lower").readAs(TOKEN_TYPE)
    val version = scalarFunction("sqlite_version").readAs(STRING)
    val query = SELECT
      .COLUMNS(instr(PERSON.NAME, "a"), hex(PERSON.AGE), typeOf(PERSON.FLAG), lowerToken(PERSON.TOKEN))
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val engine = SELECT.COLUMN(version())

    assertThat(
      listOf(
        query.compile(),
        engine.compile()
      )
        .map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """SELECT "instr"("person"."name", ?), "HEX"("person"."age"), """ +
              """"typeof"("person"."flag"), "lower"("person"."token") FROM "main"."person" """ +
              """ORDER BY "person"."id" ASC""",
          arguments = listOf(NativeValue.Text("a"))
        ),
        PlanSnapshot(
          sql = """SELECT "sqlite_version"()""",
          arguments = emptyList()
        )
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(
        listOf(
          Row4(3L, "3336", "integer", Token("a")),
          Row4(3L, "3835", "integer", Token("g")),
          Row4(null, "3137", "null", Token("n")),
          Row4(0L, "3137", "integer", Token("l"))
        )
      )
      assertThat(engine.rows(database).single()).startsWith("3.")
    }
  }

  @Test
  fun `host values compare through the descriptor encoder and the descriptor owns SQL NULL`() {
    val lowerToken = scalarFunction("lower").readAs(TOKEN_TYPE)
    val upper = scalarFunction("upper").readAs(STRING.nullable)
    val instr = scalarFunction("instr").readAs(LONG.nullable)
    val nullIf = scalarFunction("nullif").readAs(LONG)
    val ifNull = scalarFunction("ifnull").readAs(STRING)
    val byToken = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(lowerToken(PERSON.TOKEN) IS Token("g"))
    val byName = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(upper(PERSON.NAME) IN listOf("ADA", "LINUS"))
      .ORDER_BY(PERSON.ID.ASC())
    val containing = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(instr(PERSON.NAME, "a") GREATER_THAN 0L)
      .ORDER_BY(PERSON.ID.ASC())
    val petNames = SELECT
      .COLUMNS(PERSON.ID, ifNull(PET.NAME, "none"))
      .FROM(PERSON)
      .LEFT_JOIN(PET)
      .ON(PERSON.ID IS PET.OWNER)
      .ORDER_BY(PERSON.ID.ASC(), PET.ID.ASC())
    val required = SELECT
      .COLUMN(nullIf(PERSON.ID, 3L))
      .FROM(PERSON)
    val plan = byToken.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."id" FROM "main"."person" WHERE "lower"("person"."token") = ?""",
        arguments = listOf(NativeValue.Text("db:g"))
      )
    )
    petDatabase().use { database ->
      assertThat(
        listOf(
          byToken.rows(database),
          byName.rows(database),
          containing.rows(database),
          petNames.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(2L),
          listOf(1L, 4L),
          listOf(1L, 2L),
          listOf(
            Row2(1L, "Rex"),
            Row2(1L, "Tom"),
            Row2(2L, "Kit"),
            Row2(3L, "none"),
            Row2(4L, "none")
          )
        )
      )
      assertThat(sqlExceptionMessage { required.rows(database) }).isEqualTo("Required SQL result returned SQL NULL")
    }
  }

  @Test
  fun `SQLite reports unknown functions, wrong arity and names it cannot read`() {
    val unknown = SELECT
      .COLUMN(scalarFunction("""say"hi""").readAs(LONG)(PERSON.ID))
      .FROM(PERSON)
    val arity = SELECT
      .COLUMN(scalarFunction("instr").readAs(LONG)(PERSON.NAME))
      .FROM(PERSON)
    val nul = SELECT
      .COLUMN(scalarFunction("bad\u0000name").readAs(LONG)(PERSON.ID))
      .FROM(PERSON)

    assertThat(unknown.compile().sql).isEqualTo("""SELECT "say""hi"("person"."id") FROM "main"."person"""")
    personDatabase().use { database ->
      assertThat(
        listOf(
          unknown,
          arity,
          nul
        ).map { query -> sqliteErrorMessage { query.rows(database) } }
      ).isEqualTo(
        listOf(
          """no such function: say"hi""",
          "wrong number of arguments to function instr()",
          """unrecognized token: ""bad""""
        )
      )
    }
  }

  @Test
  fun `observes adds the declared tables to every calling query`() {
    val lower = scalarFunction("lower").readAs(STRING.nullable)
    val lookup = lower.observes(PET)
    val firstPet = SELECT
      .COLUMN(PET.NAME)
      .FROM(PET)
      .WHERE(PET.OWNER IS PERSON.ID)
      .ORDER_BY(PET.ID.ASC())
    val queries = listOf(
      SELECT
        .COLUMN(lower(PERSON.NAME))
        .FROM(PERSON),
      SELECT
        .COLUMN(lookup(PERSON.NAME))
        .FROM(PERSON),
      SELECT
        .COLUMN(lookup.observes(BOOK)(PERSON.NAME))
        .FROM(PERSON),
      SELECT
        .COLUMN(lower(firstPet.asScalar()))
        .FROM(PERSON)
        .ORDER_BY(PERSON.ID.ASC())
    )

    assertThat(queries.map(SelectQuery<*, *>::observedTables)).isEqualTo(
      listOf(
        listOf("person"),
        listOf("pet", "person"),
        listOf("book", "person"),
        listOf("pet", "person")
      )
    )
    petDatabase().use { database ->
      assertThat(queries.last().rows(database)).isEqualTo(listOf("rex", "kit", null, null))
    }
  }
}
