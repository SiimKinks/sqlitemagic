package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Test

internal class NestedQueryArgumentTest {
  private fun SelectQuery<*, *>.snapshot() = snapshot(compile())

  @Test
  fun `nested query arguments bind where their SQL renders in every clause`() {
    fun authorOf(title: String) = SELECT
      .COLUMN(BOOK.AUTHOR)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS title)
      .asScalar()

    val emma = authorOf("Emma")
    val selected = emma AS "emma_author"
    val titled = SELECT
      .COLUMN(BOOK.AUTHOR)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS "Emma") AS "titled"
    val names = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
    val cases = listOf(
      "WHERE" to names.WHERE(emma IS 2L.asValue()),
      "HAVING" to names
        .GROUP_BY(AUTHOR.NAME)
        .HAVING(emma IS 2L.asValue()),
      "ON" to names
        .INNER_JOIN(BOOK)
        .ON((BOOK.AUTHOR IS AUTHOR.ID) AND (emma IS 2L.asValue())),
      "IN list" to names.WHERE(emma.IN(2L.asValue(), 3L.asValue())),
      "BETWEEN" to names.WHERE(emma BETWEEN authorOf("Dune") AND authorOf("Kernel")),
      "derived JOIN and ON" to names
        .INNER_JOIN(titled)
        .ON((titled[BOOK.AUTHOR] IS AUTHOR.ID) AND (AUTHOR.NAME IS_NOT "Ada")),
      "GROUP BY" to names.GROUP_BY(emma),
      "GROUP BY a selected alias" to SELECT
        .COLUMN(selected)
        .FROM(AUTHOR)
        .GROUP_BY(selected),
      "ORDER BY" to names.ORDER_BY(emma.ASC()),
      "ORDER BY a selected alias" to SELECT
        .COLUMN(selected)
        .FROM(AUTHOR)
        .ORDER_BY(selected.DESC())
    )
    val authors = """SELECT "author"."name" FROM "main"."author""""
    val emmaSql = """(SELECT "book"."author" FROM "main"."book" WHERE "book"."title" = ?)"""

    assertThat(cases.map { (label, query) -> label to query.snapshot() }).isEqualTo(
      listOf(
        "WHERE" to PlanSnapshot(
          sql = "$authors WHERE $emmaSql = ?",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Integer(2L))
        ),
        "HAVING" to PlanSnapshot(
          sql = """$authors GROUP BY "author"."name" HAVING $emmaSql = ?""",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Integer(2L))
        ),
        "ON" to PlanSnapshot(
          sql = """$authors INNER JOIN "main"."book" ON ("book"."author" = "author"."id") AND """ +
              "($emmaSql = ?)",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Integer(2L))
        ),
        "IN list" to PlanSnapshot(
          sql = "$authors WHERE $emmaSql IN (?, ?)",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Integer(2L), NativeValue.Integer(3L))
        ),
        "BETWEEN" to PlanSnapshot(
          sql = "$authors WHERE $emmaSql BETWEEN $emmaSql AND $emmaSql",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Text("Dune"), NativeValue.Text("Kernel"))
        ),
        "derived JOIN and ON" to PlanSnapshot(
          sql = """$authors INNER JOIN (SELECT "book"."author" AS "book.author" FROM "main"."book" """ +
              """WHERE "book"."title" = ?) AS "titled" ON ("titled"."book.author" = "author"."id") AND """ +
              """("author"."name" != ?)""",
          arguments = listOf(NativeValue.Text("Emma"), NativeValue.Text("Ada"))
        ),
        "GROUP BY" to PlanSnapshot(
          sql = "$authors GROUP BY $emmaSql",
          arguments = listOf(NativeValue.Text("Emma"))
        ),
        "GROUP BY a selected alias" to PlanSnapshot(
          sql = """SELECT $emmaSql AS "emma_author" FROM "main"."author" GROUP BY $emmaSql""",
          arguments = List(2) { NativeValue.Text("Emma") }
        ),
        "ORDER BY" to PlanSnapshot(
          sql = "$authors ORDER BY $emmaSql ASC",
          arguments = listOf(NativeValue.Text("Emma"))
        ),
        "ORDER BY a selected alias" to PlanSnapshot(
          sql = """SELECT $emmaSql AS "emma_author" FROM "main"."author" ORDER BY $emmaSql DESC""",
          arguments = List(2) { NativeValue.Text("Emma") }
        )
      )
    )
  }

  @Test
  fun `compound arms bind between the arguments of every enclosing plan and compile alone`() {
    val adults = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(PERSON.AGE GREATER_THAN 20)
    val owners = SELECT
      .COLUMN(PET.OWNER)
      .FROM(PET)
      .WHERE(PET.NAME IS_NOT "Kit")
    val members = adults.UNION(owners)
    val derived = members AS "m"
    val nameLengths = SELECT
      .COLUMN(LENGTH(PET.NAME) AS "n")
      .FROM(PET)
      .WHERE(PET.NAME IS_NOT "Rex")
    val cases = listOf(
      "IN between outer arguments" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .WHERE((PERSON.AGE LESS_THAN 90) AND (PERSON.ID IN members) AND (PERSON.NAME IS_NOT "Ada")),
      "a compound later arm" to SELECT
        .COLUMN(PERSON.ID)
        .FROM(PERSON)
        .WHERE(PERSON.AGE LESS_THAN 90)
        .EXCEPT(members),
      "a derived source" to SELECT
        .COLUMN(derived[PERSON.ID])
        .FROM(derived)
        .WHERE(derived[PERSON.ID] GREATER_THAN 1L),
      "a selected scalar" to SELECT
        .COLUMNS(PERSON.NAME, members.asScalar())
        .FROM(PERSON)
        .WHERE(PERSON.AGE LESS_THAN 90),
      "an aliased computed arm" to adults.UNION(nameLengths),
      "that arm alone" to nameLengths
    )
    val membersSql = """SELECT "person"."id" FROM "main"."person" WHERE "person"."age" > ? UNION """ +
        """SELECT "pet"."owner" FROM "main"."pet" WHERE "pet"."name" != ?"""
    val nameLengthsSql = """SELECT LENGTH("pet"."name") AS "n" FROM "main"."pet" WHERE "pet"."name" != ?"""

    assertThat(cases.map { (label, query) -> label to query.snapshot() }).isEqualTo(
      listOf(
        "IN between outer arguments" to PlanSnapshot(
          sql = """SELECT "person"."name" FROM "main"."person" WHERE (("person"."age" < ?) AND """ +
              """("person"."id" IN ($membersSql))) AND ("person"."name" != ?)""",
          arguments = listOf(
            NativeValue.Integer(90L),
            NativeValue.Integer(20L),
            NativeValue.Text("Kit"),
            NativeValue.Text("Ada")
          )
        ),
        "a compound later arm" to PlanSnapshot(
          sql = """SELECT "person"."id" FROM "main"."person" WHERE "person"."age" < ? EXCEPT """ +
              "SELECT * FROM ($membersSql)",
          arguments = listOf(NativeValue.Integer(90L), NativeValue.Integer(20L), NativeValue.Text("Kit"))
        ),
        "a derived source" to PlanSnapshot(
          sql = """SELECT "m"."person.id" FROM (SELECT "person"."id" AS "person.id" FROM "main"."person" """ +
              """WHERE "person"."age" > ? UNION SELECT "pet"."owner" FROM "main"."pet" """ +
              """WHERE "pet"."name" != ?) AS "m" WHERE "m"."person.id" > ?""",
          arguments = listOf(NativeValue.Integer(20L), NativeValue.Text("Kit"), NativeValue.Integer(1L))
        ),
        "a selected scalar" to PlanSnapshot(
          sql = """SELECT "person"."name", ($membersSql) FROM "main"."person" WHERE "person"."age" < ?""",
          arguments = listOf(NativeValue.Integer(20L), NativeValue.Text("Kit"), NativeValue.Integer(90L))
        ),
        "an aliased computed arm" to PlanSnapshot(
          sql = """SELECT "person"."id" FROM "main"."person" WHERE "person"."age" > ? UNION $nameLengthsSql""",
          arguments = listOf(NativeValue.Integer(20L), NativeValue.Text("Rex"))
        ),
        "that arm alone" to PlanSnapshot(
          sql = nameLengthsSql,
          arguments = listOf(NativeValue.Text("Rex"))
        )
      )
    )
  }
}
