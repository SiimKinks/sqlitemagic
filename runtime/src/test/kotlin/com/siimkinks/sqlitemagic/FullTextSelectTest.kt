package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import org.junit.Assert.assertThrows
import org.junit.Test

internal class FullTextSelectTest {
  private val bookFields = """"book"."id", "book"."title", "book"."author""""
  private val authorFields = """"author"."id", "author"."name", "author"."publisher""""
  private val authorJoin = """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
  private val indexJoin = """INNER JOIN "main"."book_fts" ON "book_fts"."rowid" = "book"."id""""
  private val match = """"book_fts"."book_fts" MATCH ?"""

  @Test
  fun `searches join the index after its table or read it as a source`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val sql: String,
      val rows: List<Any?>
    )

    val shelf = BOOK AS "shelf"
    val found = BOOK_FTS AS "found"
    val cases = listOf(
      Case(
        label = "entity read with a relationship",
        query = SELECT
          .FROM(BOOK)
          .WHERE(BOOK_FTS MATCH "dune"),
        sql = """SELECT $bookFields, $authorFields FROM "main"."book" $indexJoin $authorJoin WHERE $match""",
        rows = books(shallow = true).take(1)
      ),
      Case(
        label = "column filter ordered by rank",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .WHERE(BOOK_FTS.TITLE MATCH "emma OR kernel")
          .ORDER_BY(BOOK_FTS.RANK.ASC(), BOOK.ID.ASC()),
        sql = """SELECT "book"."title" FROM "main"."book" $indexJoin WHERE "book_fts"."title" MATCH ? """ +
            """ORDER BY "book_fts"."rank" ASC, "book"."id" ASC""",
        rows = listOf("Emma", "Kernel")
      ),
      Case(
        label = "table joined by the query",
        query = SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .INNER_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS AUTHOR.ID)
          .WHERE(BOOK_FTS MATCH "emma"),
        sql = """SELECT "author"."name" FROM "main"."author" INNER JOIN "main"."book" ON "book"."author" = """ +
            """"author"."id" $indexJoin WHERE $match""",
        rows = listOf("Grace")
      ),
      Case(
        label = "aliased table",
        query = SELECT
          .COLUMN(shelf.TITLE)
          .FROM(shelf)
          .WHERE(BOOK_FTS MATCH "kernel"),
        sql = """SELECT "shelf"."title" FROM "main"."book" AS "shelf" INNER JOIN "main"."book_fts" ON """ +
            """"book_fts"."rowid" = "shelf"."id" WHERE $match""",
        rows = listOf("Kernel")
      ),
      Case(
        label = "index columns from the index alone",
        query = SELECT
          .COLUMNS(BOOK_FTS.ROWID, BOOK_FTS.TITLE)
          .FROM(BOOK_FTS)
          .WHERE(BOOK_FTS MATCH "kernel"),
        sql = """SELECT "book_fts"."rowid", "book_fts"."title" FROM "main"."book_fts" WHERE $match""",
        rows = listOf(Row2(3L, "Kernel"))
      ),
      Case(
        label = "rank of index rows read without a search",
        query = SELECT
          .COLUMNS(BOOK_FTS.ROWID, BOOK_FTS.RANK)
          .FROM(BOOK_FTS)
          .ORDER_BY(BOOK_FTS.ROWID.ASC()),
        sql = """SELECT "book_fts"."rowid", "book_fts"."rank" FROM "main"."book_fts" ORDER BY "book_fts"."rowid" ASC""",
        rows = listOf(Row2(1L, null), Row2(2L, null), Row2(3L, null))
      ),
      Case(
        label = "explicit join",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .INNER_JOIN(BOOK_FTS)
          .ON(BOOK_FTS.ROWID IS BOOK.ID)
          .WHERE(BOOK_FTS MATCH "dune"),
        sql = """SELECT "book"."title" FROM "main"."book" INNER JOIN "main"."book_fts" ON """ +
            """"book_fts"."rowid" = "book"."id" WHERE $match""",
        rows = listOf("Dune")
      ),
      Case(
        label = "explicit join of an aliased index",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .INNER_JOIN(found)
          .ON(found.ROWID IS BOOK.ID)
          .WHERE(found MATCH "dune"),
        sql = """SELECT "book"."title" FROM "main"."book" INNER JOIN "main"."book_fts" AS "found" ON """ +
            """"found"."rowid" = "book"."id" WHERE "found"."book_fts" MATCH ?""",
        rows = listOf("Dune")
      ),
      Case(
        label = "row IDs from a subquery",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .WHERE(BOOK.ID IN (SELECT COLUMN BOOK_FTS.ROWID FROM BOOK_FTS WHERE (BOOK_FTS MATCH "emma"))),
        sql = """SELECT "book"."title" FROM "main"."book" WHERE "book"."id" IN (SELECT "book_fts"."rowid" """ +
            """FROM "main"."book_fts" WHERE $match)""",
        rows = listOf("Emma")
      ),
      Case(
        label = "search in a subquery",
        query = SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .WHERE(AUTHOR.ID IN (SELECT COLUMN BOOK.AUTHOR FROM BOOK WHERE (BOOK_FTS MATCH "kernel"))),
        sql = """SELECT "author"."name" FROM "main"."author" WHERE "author"."id" IN (SELECT "book"."author" """ +
            """FROM "main"."book" $indexJoin WHERE $match)""",
        rows = listOf("Linus")
      ),
      Case(
        label = "search in the query and in its subquery",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .WHERE(
            (BOOK_FTS MATCH "dune OR emma") AND
                (BOOK.ID IN (SELECT COLUMN BOOK.ID FROM BOOK WHERE (BOOK_FTS MATCH "emma OR kernel")))
          )
          .ORDER_BY(BOOK_FTS.RANK.ASC()),
        sql = """SELECT "book"."title" FROM "main"."book" $indexJoin WHERE ($match) AND ("book"."id" IN """ +
            """(SELECT "book"."id" FROM "main"."book" $indexJoin WHERE $match)) ORDER BY "book_fts"."rank" ASC""",
        rows = listOf("Emma")
      ),
      Case(
        label = "subquery of another table reading the index of the query",
        query = SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .WHERE(
            (BOOK_FTS MATCH "kernel") AND
                (BOOK.ID IN (SELECT COLUMN SERIES.FIRST_BOOK FROM SERIES WHERE (SERIES.NAME IS_NOT BOOK_FTS.TITLE)))
          ),
        sql = """SELECT "book"."title" FROM "main"."book" $indexJoin WHERE ($match) AND ("book"."id" IN """ +
            """(SELECT "series"."first_book" FROM "main"."series" WHERE "series"."name" != "book_fts"."title"))""",
        rows = listOf("Kernel")
      )
    )

    val results = fullTextLibraryDatabase().use { database ->
      cases.map { case -> case.label to (case.query.compile().sql to case.query.rows(database)) }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.sql to it.rows) })
  }

  @Test
  fun `compiled searches bind the query string`() {
    val query = BOOK.TITLE.asParameter("query")

    val titles = fullTextLibraryDatabase().use { database ->
      val compiled = SELECT
        .COLUMN(BOOK.TITLE)
        .FROM(BOOK)
        .WHERE(BOOK_FTS MATCH query)
        .ORDER_BY(BOOK.ID.ASC())
        .usingConnection(database.connection)
        .compile()
      listOf("dune", "emma OR kernel").map {
        compiled
          .bind(query TO it)
          .execute()
      }
    }

    assertThat(titles).isEqualTo(
      listOf(
        listOf("Dune"),
        listOf("Emma", "Kernel")
      )
    )
  }

  @Test
  fun `counts keep the index join and searches observe the indexed table`() {
    val search = SELECT
      .FROM(BOOK)
      .WHERE(BOOK_FTS MATCH "dune OR emma")

    val count = fullTextLibraryDatabase().use { database ->
      val count = search
        .usingConnection(database.connection)
        .count()
      count.sql to count.execute()
    }

    assertThat(count).isEqualTo("""SELECT count(*) FROM "main"."book" $indexJoin WHERE $match""" to 2L)
    assertThat(
      listOf(
        search.observedTables(),
        (SELECT COLUMN BOOK_FTS.TITLE FROM BOOK_FTS WHERE (BOOK_FTS MATCH "dune")).observedTables()
      )
    ).isEqualTo(
      listOf(
        listOf("book", "author"),
        listOf("book")
      )
    )
  }

  @Test
  fun `searches whose table is not a single source of the query reject before SQL`() {
    val other = BOOK AS "other"

    assertThat(
      listOf(
        SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .INNER_JOIN(other)
          .ON(other.AUTHOR IS BOOK.AUTHOR)
          .WHERE(BOOK_FTS MATCH "dune"),
        SELECT
          .FROM(SERIES)
          .WHERE(BOOK_FTS MATCH "dune"),
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .WHERE(BOOK_FTS MATCH "dune"),
        SELECT
          .COLUMN(BOOK.TITLE)
          .FROM(BOOK)
          .WHERE(BOOK.ID IN (SELECT COLUMN SERIES.FIRST_BOOK FROM SERIES WHERE (SERIES.NAME IS_NOT BOOK_FTS.TITLE))),
        SELECT
          .COLUMN(other.TITLE)
          .FROM(other)
          .WHERE(other.ID IN (SELECT COLUMN AUTHOR.ID FROM AUTHOR WHERE (BOOK_FTS MATCH "dune")))
      ).map { query ->
        assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(
      listOf(
        "Full-text index book_fts searches book, which is a source of the query 2 times (book, other); " +
            "join book_fts explicitly",
        "Full-text index book_fts searches book, which the query joins only through a relationship; " +
            "join book explicitly with INNER_JOIN(…)",
        "Full-text index book_fts searches book, which is not a source of the query",
        "Full-text index book_fts searches book, which an enclosing query reads without searching book_fts; " +
            "search book_fts in that query",
        "Full-text index book_fts searches book, which an enclosing query reads without searching book_fts; " +
            "search book_fts in that query"
      )
    )
  }

  @Test
  fun `SQLite reports malformed query strings`() {
    val message = fullTextLibraryDatabase().use { database ->
      sqliteErrorMessage {
        SELECT
          .FROM(BOOK)
          .WHERE(BOOK_FTS MATCH "dune AND")
          .rows(database)
      }
    }

    assertThat(message).isEqualTo("""fts5: syntax error near """"")
  }
}

@Suppress("PropertyName", "TestFunctionName")
private class BookFtsTable(alias: String? = null) : Fts5Table(
  name = "book_fts",
  alias = alias,
  contentTable = BOOK,
  contentRowIdColumnName = "id"
) {
  val TITLE = Fts5Column<String, Required, String, String, Required, String>(
    table = fieldTable,
    name = "title",
    position = 0,
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val ROWID = Column<Nothing, Long, Required, Long, Long, Required, Long>(
    table = fieldTable,
    name = "rowid",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )

  override infix fun AS(alias: String) = BookFtsTable(alias)
}

private val BOOK_FTS = BookFtsTable()

private fun fullTextLibraryDatabase() = libraryDatabase().apply {
  execSQL("CREATE VIRTUAL TABLE book_fts USING fts5(title, content='book', content_rowid='id')")
  execSQL("INSERT INTO book_fts(book_fts) VALUES ('rebuild')")
}
