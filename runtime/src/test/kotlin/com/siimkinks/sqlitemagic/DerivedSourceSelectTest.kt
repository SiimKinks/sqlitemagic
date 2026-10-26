package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test

internal class DerivedSourceSelectTest {
  @Test
  fun `derived sources render as written, name every body output and bind in SQL order`() {
    val nextId = (AUTHOR.ID + 1L) AS "next_id"
    val rows = SELECT
      .COLUMNS(AUTHOR.ID, AUTHOR.NAME, nextId)
      .FROM(AUTHOR)
      .WHERE(AUTHOR.NAME IS_NOT "Grace")
    val published = rows AS "published"
    val aliased = SELECT
      .COLUMNS(published[AUTHOR.NAME], published[nextId])
      .FROM(published)
      .WHERE(published[AUTHOR.ID] GREATER_THAN 1L)
      .ORDER_BY(published[AUTHOR.ID].ASC())
    val plusTen = AUTHOR.ID + 10L
    val computed = SELECT
      .COLUMNS(AUTHOR.NAME, plusTen)
      .FROM(AUTHOR)
    val positional = SELECT
      .COLUMN(computed[plusTen])
      .FROM(computed)
      .ORDER_BY(computed[AUTHOR.NAME].DESC())

    assertThat(
      listOf(
        aliased.compile(),
        positional.compile()
      )
        .map(::snapshot)
    ).isEqualTo(
      listOf(
        PlanSnapshot(
          sql = """SELECT "published"."author.name", "published"."next_id" FROM (SELECT "author"."id" AS """ +
              """"author.id", "author"."name" AS "author.name", "author"."id" + ? AS "next_id" """ +
              """FROM "main"."author" WHERE "author"."name" != ?) AS "published" """ +
              """WHERE "published"."author.id" > ? ORDER BY "published"."author.id" ASC""",
          arguments = listOf(NativeValue.Integer(1L), NativeValue.Text("Grace"), NativeValue.Integer(1L))
        ),
        PlanSnapshot(
          sql = """SELECT "_2" FROM (SELECT "author"."name" AS "author.name", "author"."id" + ? AS "_2" """ +
              """FROM "main"."author") ORDER BY "author.name" DESC""",
          arguments = listOf(NativeValue.Integer(10L))
        )
      )
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          aliased.rows(database),
          positional.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(Row2("Linus", 4L)),
          listOf(13L, 12L, 11L)
        )
      )
    }
  }

  @Test
  fun `selecting a derived source reads its query's complete result with the query's reader`() {
    var mapped = 0
    val ids = SELECT
      .COLUMN(AUTHOR.ID)
      .FROM(AUTHOR)
      .mapTo { id ->
        mapped++
        MappedId(id)
      }
    val published = SELECT
      .COLUMNS(AUTHOR.ID, AUTHOR.NAME)
      .FROM(AUTHOR)
      .WHERE(AUTHOR.PUBLISHER.IS_NOT_NULL()) AS "published"
    val books = SELECT FROM BOOK AS "shelf"
    val defaultIds = (SELECT FROM ids).ORDER_BY(ids[AUTHOR.ID].DESC())
    val defaultRows = SELECT FROM published
    val defaultBooks = SELECT FROM books
    val repeated = SELECT FROM (SELECT FROM published AS "again")

    assertThat(
      listOf(
        defaultIds.compile().sql,
        defaultRows.compile().sql,
        defaultBooks.compile().sql,
        repeated.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "author.id" FROM (SELECT "author"."id" AS "author.id" FROM "main"."author") """ +
            """ORDER BY "author.id" DESC""",
        """SELECT "published"."author.id", "published"."author.name" FROM (SELECT "author"."id" AS """ +
            """"author.id", "author"."name" AS "author.name" FROM "main"."author" """ +
            """WHERE "author"."publisher" IS NOT NULL) AS "published"""",
        """SELECT "shelf"."book.id", "shelf"."book.title", "shelf"."book.author", """ +
            """"shelf"."author.id", "shelf"."author.name", "shelf"."author.publisher" FROM """ +
            """(SELECT "book"."id" AS "book.id", "book"."title" AS "book.title", "book"."author" AS """ +
            """"book.author", "author"."id" AS "author.id", "author"."name" AS "author.name", """ +
            """"author"."publisher" AS "author.publisher" FROM "main"."book" """ +
            """LEFT JOIN "main"."author" ON "book"."author" = "author"."id") AS "shelf"""",
        """SELECT "again"."author.id", "again"."author.name" FROM (SELECT "published"."author.id" AS """ +
            """"author.id", "published"."author.name" AS "author.name" FROM (SELECT "author"."id" AS """ +
            """"author.id", "author"."name" AS "author.name" FROM "main"."author" WHERE """ +
            """"author"."publisher" IS NOT NULL) AS "published") AS "again""""
      )
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          defaultIds.rows(database),
          (SELECT.DISTINCT FROM ids).rows(database),
          defaultRows.rows(database),
          defaultBooks.rows(database),
          repeated.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(MappedId(3L), MappedId(2L), MappedId(1L)),
          listOf(MappedId(1L), MappedId(2L), MappedId(3L)),
          listOf(
            Row2(1L, "Ada"),
            Row2(3L, "Linus")
          ),
          books(shallow = true),
          listOf(
            Row2(1L, "Ada"),
            Row2(3L, "Linus")
          )
        )
      )
    }
    assertThat(mapped).isEqualTo(6)
  }

  @Test
  fun `exports join, compare and lift on the null-extended side`() {
    val nextId = (AUTHOR.ID + 1L) AS "next_id"
    val published = SELECT
      .COLUMNS(AUTHOR.ID, AUTHOR.NAME, nextId)
      .FROM(AUTHOR)
      .WHERE(AUTHOR.PUBLISHER.IS_NOT_NULL()) AS "published"
    val other = published AS "other"
    val joined = SELECT
      .COLUMNS(BOOK.TITLE, published[AUTHOR.NAME])
      .FROM(BOOK)
      .INNER_JOIN(published)
      .ON(BOOK.AUTHOR IS published[AUTHOR.ID])
      .WHERE(published[AUTHOR.NAME] IN listOf("Ada", "Grace"))
    val lifted = SELECT
      .COLUMNS(BOOK.TITLE, published[AUTHOR.NAME].nullable(), published[nextId].nullable())
      .FROM(BOOK)
      .LEFT_JOIN(published)
      .ON(BOOK.AUTHOR IS published[AUTHOR.ID])
      .ORDER_BY(BOOK.ID.ASC())
    val pairs = SELECT
      .COLUMNS(published[AUTHOR.NAME], other[AUTHOR.NAME])
      .FROM(published)
      .INNER_JOIN(other)
      .ON(other[AUTHOR.ID] GREATER_THAN published[AUTHOR.ID])
    val unlifted = SELECT
      .COLUMNS(BOOK.TITLE, published[AUTHOR.NAME])
      .FROM(BOOK)
      .LEFT_JOIN(published)
      .ON(BOOK.AUTHOR IS published[AUTHOR.ID])
    val rightJoined = SELECT FROM published RIGHT_JOIN BOOK ON (BOOK.AUTHOR IS published[AUTHOR.ID])

    assertThat(
      listOf(
        joined.compile().sql,
        pairs.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "book"."title", "published"."author.name" FROM "main"."book" INNER JOIN (SELECT """ +
            """"author"."id" AS "author.id", "author"."name" AS "author.name", "author"."id" + ? AS """ +
            """"next_id" FROM "main"."author" WHERE "author"."publisher" IS NOT NULL) AS "published" """ +
            """ON "book"."author" = "published"."author.id" WHERE "published"."author.name" IN (?, ?)""",
        """SELECT "published"."author.name", "other"."author.name" FROM (SELECT "author"."id" AS """ +
            """"author.id", "author"."name" AS "author.name", "author"."id" + ? AS "next_id" FROM """ +
            """"main"."author" WHERE "author"."publisher" IS NOT NULL) AS "published" INNER JOIN (SELECT """ +
            """"author"."id" AS "author.id", "author"."name" AS "author.name", "author"."id" + ? AS """ +
            """"next_id" FROM "main"."author" WHERE "author"."publisher" IS NOT NULL) AS "other" """ +
            """ON "other"."author.id" > "published"."author.id""""
      )
    )
    assertThat(
      listOf(
        assertThrows(IllegalArgumentException::class.java, unlifted::compile).message,
        assertThrows(IllegalArgumentException::class.java, rightJoined::compile).message
      )
    ).isEqualTo(
      listOf(
        "published.author.name is NOT NULL but an outer join can leave it NULL; select the field with .nullable()",
        "An outer join can leave the result of published NULL; select its values with .nullable()"
      )
    )
    libraryDatabase().use { database ->
      assertThat(
        listOf(
          joined.rows(database),
          lifted.rows(database),
          pairs.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf(Row2("Dune", "Ada")),
          listOf(
            Row3("Dune", "Ada", 2L),
            Row3("Emma", null, null),
            Row3("Kernel", "Linus", 4L)
          ),
          listOf(Row2("Ada", "Linus"))
        )
      )
    }
  }

  @Test
  fun `exports name only the instances their query selects`() {
    val rows = SELECT
      .COLUMNS(AUTHOR.ID, (AUTHOR.ID + 1L) AS "next_id")
      .FROM(AUTHOR)
    val published = rows AS "published"

    assertThat(
      listOf(
        assertThrows(IllegalArgumentException::class.java) { published[(AUTHOR.ID + 1L) AS "next_id"] }.message,
        assertThrows(IllegalArgumentException::class.java) { published[AUTHOR.NAME] }.message,
        assertThrows(IllegalArgumentException::class.java) { rows[AUTHOR.ID + 1L] }.message
      )
    ).isEqualTo(
      listOf(
        "published exports only the values its query selects; use the selected instance",
        "published exports only the values its query selects; use the selected instance",
        "A derived source exports only the values its query selects; use the selected instance"
      )
    )
  }

  @Test
  fun `derived bodies see enclosing queries but not their siblings`() {
    val ownBooks = SELECT
      .COLUMN(BOOK.ID)
      .FROM(BOOK)
      .WHERE(BOOK.AUTHOR IS AUTHOR.ID) AS "own"
    val writing = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
      .WHERE(EXISTS(SELECT FROM ownBooks))
      .ORDER_BY(AUTHOR.ID.ASC())
    val dune = SELECT
      .COLUMN(BOOK.AUTHOR)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS "Dune") AS "dune"
    val lateral = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .INNER_JOIN(SELECT COLUMN PUBLISHER.NAME FROM PUBLISHER WHERE (PUBLISHER.ID IS BOOK.ID))
    val sameAuthor = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .INNER_JOIN(dune)
      .ON(BOOK.AUTHOR IS dune[BOOK.AUTHOR])
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(
      listOf(
        writing.compile().sql,
        sameAuthor.compile().sql
      )
    ).isEqualTo(
      listOf(
        """SELECT "author"."name" FROM "main"."author" WHERE EXISTS (SELECT "own"."book.id" FROM """ +
            """(SELECT "book"."id" AS "book.id" FROM "main"."book" WHERE "book"."author" = """ +
            """"author"."id") AS "own") ORDER BY "author"."id" ASC""",
        """SELECT "book"."title" FROM "main"."book" INNER JOIN (SELECT "book"."author" AS """ +
            """"book.author" FROM "main"."book" WHERE "book"."title" = ?) AS "dune" ON """ +
            """"book"."author" = "dune"."book.author" ORDER BY "book"."id" ASC"""
      )
    )
    assertThat(assertThrows(IllegalArgumentException::class.java, lateral::compile))
      .hasMessageThat()
      .isEqualTo("A field requires the exact table occurrence of its FROM, UPDATE or DELETE")
    libraryDatabaseWithNotes().use { database ->
      assertThat(
        listOf(
          writing.rows(database),
          sameAuthor.rows(database)
        )
      ).isEqualTo(
        listOf(
          listOf("Ada", "Grace", "Linus"),
          listOf("Dune", "Notes")
        )
      )
    }
  }

  @Test
  fun `derived sources bind their parameters and view definitions inline their values`() {
    val title = BOOK.TITLE.asParameter("title")
    val titled = SELECT
      .COLUMNS(BOOK.AUTHOR, BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS_NOT title) AS "titled"
    val writers = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
      .INNER_JOIN(titled)
      .ON(titled[BOOK.AUTHOR] IS AUTHOR.ID)
      .ORDER_BY(AUTHOR.ID.ASC())
    val published = SELECT
      .COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS_NOT "Dune") AS "published"
    val view = GeneratedView(
      viewName = "published_title",
      temporary = false,
      query = {
        SELECT
          .COLUMN(published[BOOK.TITLE])
          .FROM(published)
      },
      columns = listOf("published.book.title")
    )
    val defaultView = GeneratedView(
      viewName = "published_row",
      temporary = false,
      query = { SELECT FROM published },
      columns = listOf("published.book.title")
    )

    assertThat(
      listOf(
        view.definition.sql,
        defaultView.definition.sql
      )
    ).isEqualTo(
      List(2) {
        """SELECT "published"."book.title" AS "published.book.title" FROM (SELECT "book"."title" AS """ +
            """"book.title" FROM "main"."book" WHERE "book"."title" != 'Dune') AS "published""""
      }
    )
    libraryDatabase().use { database ->
      val connection = database.dbConnection()
      database.createViews(view, defaultView)
      val compiled = writers
        .usingConnection(connection)
        .compile()

      assertThat(
        listOf("Dune", "Emma").map {
          compiled.bind(title TO it)
            .execute()
        }
      ).isEqualTo(
        listOf(
          listOf("Grace", "Linus"),
          listOf("Ada", "Linus")
        )
      )
      assertThat(
        listOf("published_title", "published_row").map { viewName ->
          SELECT
            .RAW("""SELECT "published.book.title" FROM $viewName""")
            .readAs(STRING)
            .usingConnection(connection)
            .execute()
        }
      ).isEqualTo(List(2) { listOf("Emma", "Kernel") })
    }
  }
}
