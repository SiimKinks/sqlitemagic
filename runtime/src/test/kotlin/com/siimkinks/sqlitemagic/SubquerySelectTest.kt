package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class SubquerySelectTest {
  @Test
  fun `scalar subqueries render in place, bind in SQL order and read contextual null`() {
    val emma = SELECT
      .COLUMN(BOOK.ID)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS "Emma")
    val query = SELECT
      .COLUMNS(AUTHOR.NAME, emma.asScalar())
      .FROM(AUTHOR)
      .WHERE(AUTHOR.ID GREATER_OR_EQUAL emma.asScalar())
      .ORDER_BY(AUTHOR.ID.ASC())
    val missing = SELECT
      .COLUMN(BOOK.ID)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS "missing")
    val titles = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.DESC())
    val edges = SELECT
      .COLUMNS(missing.asScalar(), titles.asScalar())
    val plan = query.compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT "author"."name", (SELECT "book"."id" FROM "main"."book" WHERE "book"."title" = """ +
            """?) FROM "main"."author" WHERE "author"."id" >= """ +
            """(SELECT "book"."id" FROM "main"."book" WHERE "book"."title" = ?) ORDER BY "author"."id" """ +
            "ASC",
        arguments = listOf(NativeValue.Text("Emma"), NativeValue.Text("Emma"))
      )
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          query.rows(database),
          edges.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(
            Row2("Grace", 2L),
            Row2("Linus", 2L)
          ),
          listOf(Row2(null, "Kernel"))
        )
      )
    }
  }

  @Test
  fun `nested queries use their stored values and never run their mappers`() {
    var mapped = 0
    val published = SELECT
      .COLUMN(AUTHOR.ID)
      .FROM(AUTHOR)
      .WHERE(AUTHOR.PUBLISHER.IS_NOT_NULL())
      .mapTo { id ->
        mapped++
        MappedId(id)
      }
    val texts = SELECT
      .COLUMN(AUTHOR.ID)
      .FROM(AUTHOR)
      .ORDER_BY(AUTHOR.ID.DESC())
      .readAs(STRING)
    val members = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(BOOK.AUTHOR IN published)
      .ORDER_BY(BOOK.ID.ASC())
    val nonMembers = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(BOOK.AUTHOR NOT_IN published)
    val publishers = SELECT
      .COLUMN(AUTHOR.PUBLISHER)
      .FROM(AUTHOR)

    assertThat(members.compile().sql).isEqualTo(
      """SELECT "book"."title" FROM "main"."book" WHERE "book"."author" IN """ +
          """(SELECT "author"."id" FROM "main"."author" WHERE "author"."publisher" IS NOT NULL) """ +
          """ORDER BY "book"."id" ASC"""
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          members.rows(database),
          nonMembers.rows(database),
          SELECT
            .COLUMN(texts.asScalar())
            .rows(database),
          (SELECT COLUMN AUTHOR.NAME FROM AUTHOR WHERE (AUTHOR.ID IN publishers)).rows(database),
          (SELECT COLUMN AUTHOR.NAME FROM AUTHOR WHERE (AUTHOR.ID NOT_IN publishers)).rows(database),
          published.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf("Dune", "Kernel"),
          listOf("Emma"),
          listOf(3L),
          listOf("Ada", "Grace"),
          emptyList(),
          listOf(MappedId(1L), MappedId(3L))
        )
      )
    }
    assertThat(mapped).isEqualTo(2)
  }

  @Test
  fun `nested fields name the nearest enclosing occurrence and otherwise join automatically`() {
    val writtenAfterDune = SELECT
      .COLUMN(BOOK.ID)
      .FROM(BOOK)
      .WHERE((BOOK.AUTHOR IS AUTHOR.ID) AND (BOOK.TITLE IS_NOT "Dune"))
    val writing = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
      .WHERE(EXISTS(writtenAfterDune))
      .ORDER_BY(AUTHOR.ID.ASC())
    val idle = SELECT COLUMN AUTHOR.NAME FROM AUTHOR WHERE NOT_EXISTS(writtenAfterDune)
    val other = BOOK AS "other"
    val sharedAuthor = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(
        EXISTS(
          SELECT COLUMN other.ID FROM other WHERE ((other.AUTHOR IS BOOK.AUTHOR) AND (other.ID IS_NOT BOOK.ID))
        )
      )
      .ORDER_BY(BOOK.ID.ASC())
    val innermost = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(EXISTS(SELECT COLUMN BOOK.ID FROM BOOK WHERE (BOOK.TITLE IS "Notes")))
      .ORDER_BY(BOOK.ID.ASC())
    val publishedAda = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(
        (AUTHOR.NAME IS "Ada") AND
            EXISTS(SELECT COLUMN PUBLISHER.ID FROM PUBLISHER WHERE (PUBLISHER.ID IS AUTHOR.PUBLISHER))
      )
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(
      listOf(
        writing.compile().sql,
        writtenAfterDune.compile().sql,
        sharedAuthor.compile().sql,
        publishedAda.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "author"."name" FROM "main"."author" WHERE EXISTS (SELECT "book"."id" """ +
            """FROM "main"."book" WHERE ("book"."author" = "author"."id") AND ("book"."title" != ?)) """ +
            """ORDER BY "author"."id" ASC""",
        """SELECT "book"."id" FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = """ +
            """"author"."id" WHERE ("book"."author" = "author"."id") AND ("book"."title" != ?)""",
        """SELECT "book"."title" FROM "main"."book" WHERE EXISTS (SELECT "other"."id" """ +
            """FROM "main"."book" AS "other" WHERE ("other"."author" = "book"."author") AND """ +
            """("other"."id" != "book"."id")) ORDER BY "book"."id" ASC""",
        """SELECT "book"."title" FROM "main"."book" LEFT JOIN "main"."author" ON "book"."author" = """ +
            """"author"."id" WHERE ("author"."name" = ?) AND (EXISTS (SELECT "publisher"."id" FROM """ +
            """"main"."publisher" WHERE "publisher"."id" = "author"."publisher")) """ +
            """ORDER BY "book"."id" ASC"""
      )
    )
    libraryDatabaseWithNotes().use { database ->
      assertThat(
        listOf(
          writing.rows(database),
          idle.rows(database),
          sharedAuthor.rows(database),
          innermost.rows(database),
          publishedAda.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf("Ada", "Grace", "Linus"),
          emptyList(),
          listOf("Dune", "Notes"),
          listOf("Dune", "Emma", "Kernel", "Notes"),
          listOf("Dune", "Notes")
        )
      )
    }
  }

  @Test
  fun `nested queries consume outputs in SQL without read checks`() {
    val existing = EXISTS(SELECT FROM BOOK)
    val titled = SELECT
      .COLUMNS(AUTHOR.NAME, (SELECT COLUMN BOOK.TITLE).asScalar())
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON((BOOK.AUTHOR IS AUTHOR.ID) AND (BOOK.TITLE IS "Dune"))
      .ORDER_BY(AUTHOR.ID.ASC())

    assertThat(
      listOf(
        SELECT
          .COLUMN(existing)
          .compile()
          .sql,
        titled.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT EXISTS (SELECT "book"."id", "book"."title", "book"."author" FROM "main"."book")""",
        """SELECT "author"."name", (SELECT "book"."title") FROM "main"."author" """ +
            """LEFT JOIN "main"."book" ON ("book"."author" = "author"."id") AND ("book"."title" = ?) """ +
            """ORDER BY "author"."id" ASC"""
      )
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          SELECT
            .COLUMN(existing)
            .rows(database),
          titled.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(true),
          listOf(
            Row2("Ada", "Dune"),
            Row2("Grace", null),
            Row2("Linus", null)
          )
        )
      )
    }
  }

  @Test
  fun `statements take correlated scalar values and query membership`() {
    libraryDatabase().use { database ->
      val connection = database.dbConnection()
      val lastIsFirst = UPDATE TABLE SERIES SET
          (SERIES.LAST_BOOK to (SELECT COLUMN BOOK.ID FROM BOOK WHERE (BOOK.ID IS SERIES.FIRST_BOOK)).asScalar())
      val unpublished = DELETE FROM BOOK WHERE
          (BOOK.AUTHOR IN (SELECT COLUMN AUTHOR.ID FROM AUTHOR WHERE AUTHOR.PUBLISHER.IS_NULL()))

      assertThat(
        listOf(
          lastIsFirst.compile().sql,
          unpublished.compile().sql
        )
      ).isEqualTo(
        listOf(
          """UPDATE "main"."series" SET "last_book" = (SELECT "book"."id" FROM "main"."book" WHERE """ +
              """"book"."id" = "series"."first_book")""",
          """DELETE FROM "main"."book" WHERE "book"."author" IN (SELECT "author"."id" """ +
              """FROM "main"."author" WHERE "author"."publisher" IS NULL)"""
        )
      )
      assertThat(
        listOf(
          lastIsFirst
            .usingConnection(connection)
            .execute(),
          unpublished
            .usingConnection(connection)
            .execute()
        )
      ).isEqualTo(listOf(2, 1))
      assertThat(
        listOf(
          SELECT
            .RAW("SELECT last_book FROM series ORDER BY id")
            .readAs(LONG)
            .usingConnection(connection)
            .execute(),
          SELECT
            .RAW("SELECT title FROM book ORDER BY id")
            .readAs(STRING)
            .usingConnection(connection)
            .execute()
        )
      ).isEqualTo(
        listOf(
          listOf(1L, 3L),
          listOf("Dune", "Kernel")
        )
      )
    }
  }

  @Test
  fun `a nested query cannot use a connection`() {
    libraryDatabase().use { database ->
      val connected = SELECT
        .COLUMN(AUTHOR.ID)
        .FROM(AUTHOR)
        .usingConnection(database.dbConnection())
      val failures = listOf(
        { BOOK.AUTHOR IN connected },
        connected::asScalar,
        { EXISTS(connected) },
        { connected AS "connected" }
      ).map { nesting ->
        assertThrows(IllegalArgumentException::class.java) { nesting() }.message
      }

      assertThat(failures).isEqualTo(
        List(4) { "A nested query cannot use a connection; choose it on the outermost query" }
      )
    }
  }
}
