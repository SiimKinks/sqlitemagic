package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class CastRowValueSelectTest {
  @Test
  fun `casts convert with SQLite and read the target type without the source transformer`() {
    val query = SELECT
      .COLUMNS(
        CAST(PERSON.ID AS TEXT),
        CAST(PERSON.NAME AS INTEGER),
        CAST(PERSON.AGE AS REAL),
        CAST(PERSON.TOKEN AS TEXT)
      )
      .FROM(PERSON)
      .ORDER_BY(PERSON.ID.ASC())
    val numeric = SELECT
      .COLUMNS(
        CAST("7".asValue() AS NUMERIC),
        CAST("3.5".asValue() AS NUMERIC)
      )
    val bytes = SELECT
      .COLUMN(CAST(PERSON.NAME AS BLOB))
      .FROM(PERSON)
      .WHERE(PERSON.NAME.IS_NOT_NULL())
      .ORDER_BY(PERSON.ID.ASC())
      .mapTo { it?.decodeToString() }

    assertThat(query.compile().sql).isEqualTo(
      """SELECT CAST("person"."id" AS TEXT), CAST("person"."name" AS INTEGER), """ +
          """CAST("person"."age" AS REAL), CAST("person"."token" AS TEXT) FROM "main"."person" """ +
          """ORDER BY "person"."id" ASC"""
    )
    personDatabase().use { database ->
      assertThat(
        listOf(
          query.rows(database),
          numeric.rows(database),
          bytes.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(
            Row4("1", 0L, 36.0, "db:a"),
            Row4("2", 0L, 85.0, "db:g"),
            Row4("3", null, 17.0, "db:n"),
            Row4("4", 0L, 17.0, "db:l")
          ),
          listOf(Row2(7L, 3.5)),
          listOf("Ada", "Grace", "Linus")
        )
      )
    }
  }

  @Test
  fun `cast results compare with host values through their target's native encoder`() {
    val query = SELECT
      .COLUMN(PERSON.ID)
      .FROM(PERSON)
      .WHERE(
        (CAST(PERSON.AGE AS TEXT) IN listOf("17", "85")) AND
            (CAST(PERSON.AGE AS REAL) GREATER_THAN 16.5) AND
            (CAST(PERSON.AGE AS NUMERIC) LESS_THAN 80) AND
            (CAST(PERSON.ID AS TEXT) IS_NOT "3")
      )
      .ORDER_BY(PERSON.ID.ASC())
    val plan = query.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "person"."id" FROM "main"."person" """ +
            """WHERE (((CAST("person"."age" AS TEXT) IN (?, ?)) AND (CAST("person"."age" AS REAL) > ?)) AND """ +
            """(CAST("person"."age" AS NUMERIC) < ?)) AND (CAST("person"."id" AS TEXT) != ?) """ +
            """ORDER BY "person"."id" ASC""",
        arguments = listOf(
          NativeValue.Text("17"),
          NativeValue.Text("85"),
          NativeValue.Real(16.5),
          NativeValue.Integer(80L),
          NativeValue.Text("3")
        )
      )
    )
    personDatabase().use { database ->
      assertThat(query.rows(database)).isEqualTo(listOf(4L))
    }
  }

  @Test
  fun `casts keep their source's nullness and outer join lifting`() {
    val lifted = SELECT
      .COLUMNS(AUTHOR.NAME, CAST(BOOK.ID.nullable() AS TEXT))
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON((BOOK.AUTHOR IS AUTHOR.ID) AND (BOOK.TITLE IS "Dune"))
      .ORDER_BY(AUTHOR.ID.ASC())
    val unlifted = SELECT
      .COLUMN(CAST(BOOK.ID AS TEXT))
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON(BOOK.AUTHOR IS AUTHOR.ID)

    assertThat(assertThrows(IllegalArgumentException::class.java, unlifted::compile))
      .hasMessageThat()
      .isEqualTo("book.id is NOT NULL but an outer join can leave it NULL; select the field with .nullable()")
    libraryDatabase().use { database ->
      assertThat(lifted.rows(database)).isEqualTo(
        listOf(
          Row2("Ada", "1"),
          Row2("Grace", null),
          Row2("Linus", null)
        )
      )
    }
  }

  @Test
  fun `row values compare tuples in SQL order for keyset pagination`() {
    val lastId = BOOK.ID.asParameter("lastId")
    val after = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(row(BOOK.AUTHOR, BOOK.ID) GREATER_THAN row(1L, 1L))
      .ORDER_BY(BOOK.AUTHOR.ASC(), BOOK.ID.ASC())
      .LIMIT(2)
    val upTo = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(row(BOOK.AUTHOR, BOOK.TITLE, BOOK.ID) LESS_OR_EQUAL row(2L, "Emma".asValue(), lastId))
      .ORDER_BY(BOOK.AUTHOR.ASC(), BOOK.ID.ASC())
    val from = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE((row(BOOK.AUTHOR, BOOK.ID) GREATER_OR_EQUAL row(2L, 2L)) AND (row(BOOK.ID, 0) LESS_THAN row(4L, 0)))
      .ORDER_BY(BOOK.ID.ASC())
    val plan = after.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "book"."title" FROM "main"."book" """ +
            """WHERE ("book"."author", "book"."id") > (?, ?) """ +
            """ORDER BY "book"."author" ASC, "book"."id" ASC LIMIT 2""",
        arguments = listOf(NativeValue.Integer(1L), NativeValue.Integer(1L))
      )
    )
    assertThat(upTo.compile().sql).isEqualTo(
      """SELECT "book"."title" FROM "main"."book" WHERE ("book"."author", "book"."title", """ +
          """"book"."id") <= (?, ?, ?) ORDER BY "book"."author" ASC, "book"."id" ASC"""
    )
    libraryDatabaseWithNotes().use { database ->
      assertThat(
        listOf(
          after.rows(database),
          upTo
            .usingConnection(database.dbConnection())
            .compile()
            .bind(lastId TO 2L)
            .execute(),
          from.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf("Notes", "Emma"),
          listOf("Dune", "Notes", "Emma"),
          listOf("Emma", "Kernel")
        )
      )
    }
  }

  @Test
  fun `row values reject non-native hosts and leave width mismatches to SQLite`() {
    val mismatched = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(row(BOOK.AUTHOR, BOOK.ID) GREATER_THAN row(1L, 1L, 1L))

    assertThat(
      listOf(
        assertThrows(IllegalArgumentException::class.java) {
          row(BOOK.ID, Token("a"))
        }.message,
        assertThrows(IllegalArgumentException::class.java) {
          row(
            BOOK.ID,
            SELECT
              .COLUMN(BOOK.ID)
              .FROM(BOOK)
          )
        }.message
      )
    ).isEqualTo(
      listOf(
        "Unsupported native SQL argument: com.siimkinks.sqlitemagic.Token",
        "SQL expressions and queries are not host values"
      )
    )
    libraryDatabase().use { database ->
      assertThat(sqliteErrorMessage { mismatched.rows(database) }).contains("row value misused")
    }
  }
}
