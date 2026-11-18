package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityValueExportTest {
  private val shallowBooks = books(shallow = true)
  private val deepBooks = books(shallow = false)
  private val shallowAda = shallowBooks[0].author
  private val shallowLinus = shallowBooks[2].author
  private val mary = Author(
    id = 4L,
    name = "Mary",
    publisher = null
  )
  private val orbit = Publisher(
    id = 3L,
    name = "Orbit"
  )
  private val bookCount = COUNT(BOOK.ID) AS "book_count"
  private val totals = SELECT
    .COLUMNS(AUTHOR, bookCount)
    .FROM(AUTHOR)
    .LEFT_JOIN(BOOK)
    .ON(BOOK.AUTHOR IS AUTHOR.ID)
    .GROUP_BY(AUTHOR.ID) AS "totals"
  private val penName = AUTHOR.NAME AS "pen_name"
  private val writers = SELECT.COLUMNS(AUTHOR, penName) FROM AUTHOR AS "writers"
  private val totalsBody = """SELECT "author"."id" AS "author.id", "author"."name" AS "author.name", """ +
      """"author"."publisher" AS "author.publisher", COUNT("book"."id") AS "book_count" FROM "main"."author" """ +
      """LEFT JOIN "main"."book" ON "book"."author" = "author"."id" GROUP BY "author"."id""""
  private val totalsColumns = """"totals"."author.id", "totals"."author.name", "totals"."author.publisher""""
  private val nullableBooks = SELECT
    .COLUMNS(AUTHOR.NAME, BOOK.nullable())
    .FROM(AUTHOR)
    .LEFT_JOIN(BOOK)
    .ON(BOOK.AUTHOR IS AUTHOR.ID) AS "shelves"
  private val wide = SELECT
    .COLUMNS(AUTHOR, PUBLISHER.all())
    .FROM(AUTHOR)
    .INNER_JOIN(PUBLISHER)
    .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
  private val wideNullable = SELECT
    .COLUMNS(AUTHOR.nullable(), PUBLISHER.all())
    .FROM(AUTHOR)
    .INNER_JOIN(PUBLISHER)
    .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)

  @Test
  fun `derived sources and CTEs export entity values that derived entities read`() {
    val totalsAuthor = totals[AUTHOR]
    val totalsCount = totals[bookCount]
    val shelf = (SELECT.COLUMNS(BOOK, AUTHOR.NAME) FROM BOOK).queryDeep() AS "shelf"
    val pairs = SELECT.COLUMNS(BOOK, AUTHOR) FROM BOOK AS "pairs"
    val named = BOOK AS "author"
    val namedTitle = named.TITLE AS "name"
    val renamed = SELECT.COLUMNS(named, namedTitle) FROM named AS "renamed"
    val again = SELECT.COLUMNS(totalsAuthor, totalsCount) FROM totals AS "again"
    val grid = SELECT.COLUMNS(AUTHOR, PUBLISHER.ID) FROM AUTHOR CROSS_JOIN PUBLISHER AS "grid"
    val cases = listOf(
      QueryCase(
        label = "a derived entity beside a derived value",
        query = SELECT
          .COLUMNS(totalsAuthor, totalsCount)
          .FROM(totals)
          .WHERE(totalsCount GREATER_THAN 0L)
          .ORDER_BY(totals[AUTHOR.ID].ASC()),
        sql = """SELECT $totalsColumns, "totals"."book_count" FROM ($totalsBody) AS "totals" """ +
            """WHERE "totals"."book_count" > ? ORDER BY "totals"."author.id" ASC""",
        rows = listOf(
          Row2(shallowAda, 1L),
          Row2(graceAuthor, 1L),
          Row2(shallowLinus, 1L)
        )
      ),
      QueryCase(
        label = "a lone derived entity",
        query = SELECT
          .COLUMN(totalsAuthor)
          .FROM(totals)
          .WHERE(totalsCount GREATER_THAN 0L)
          .ORDER_BY(totals[AUTHOR.ID].ASC()),
        sql = """SELECT $totalsColumns FROM ($totalsBody) AS "totals" WHERE "totals"."book_count" > ? """ +
            """ORDER BY "totals"."author.id" ASC""",
        rows = listOf(shallowAda, graceAuthor, shallowLinus)
      ),
      QueryCase(
        label = "distinct derived entities",
        query = SELECT
          .DISTINCT(grid[AUTHOR])
          .FROM(grid)
          .ORDER_BY(grid[AUTHOR.ID].ASC()),
        sql = """SELECT DISTINCT "grid"."author.id", "grid"."author.name", "grid"."author.publisher" FROM """ +
            """(SELECT "author"."id" AS "author.id", "author"."name" AS "author.name", "author"."publisher" AS """ +
            """"author.publisher", "publisher"."id" AS "publisher.id" FROM "main"."author" CROSS JOIN """ +
            """"main"."publisher") AS "grid" ORDER BY "grid"."author.id" ASC""",
        rows = listOf(shallowAda, graceAuthor, shallowLinus, mary)
      ),
      QueryCase(
        label = "own fields of a nullable entity read with nullable()",
        query = SELECT
          .COLUMNS(nullableBooks[AUTHOR.NAME], nullableBooks[BOOK.TITLE.nullable()])
          .FROM(nullableBooks)
          .ORDER_BY(nullableBooks[AUTHOR.NAME].ASC()),
        sql = """SELECT "shelves"."author.name", "shelves"."book.title" FROM (SELECT "author"."name" AS """ +
            """"author.name", "book"."id" AS "book.id", "book"."title" AS "book.title", "book"."author" AS """ +
            """"book.author", "author"."id" AS "book.author.id", "author"."name" AS "book.author.name", """ +
            """"author"."publisher" AS "book.author.publisher" FROM "main"."author" LEFT JOIN "main"."book" """ +
            """ON "book"."author" = "author"."id") AS "shelves" ORDER BY "shelves"."author.name" ASC""",
        rows = listOf(
          Row2("Ada", "Dune"),
          Row2("Grace", "Emma"),
          Row2("Linus", "Kernel"),
          Row2("Mary", null)
        )
      ),
      QueryCase(
        label = "a derived entity keeps the depth of its query",
        query = SELECT
          .COLUMNS(shelf[BOOK], shelf[AUTHOR.NAME])
          .FROM(shelf)
          .ORDER_BY(shelf[BOOK.ID].ASC()),
        sql = """SELECT "shelf"."book.id", "shelf"."book.title", "shelf"."book.author", "shelf"."book.author.id", """ +
            """"shelf"."book.author.name", "shelf"."book.author.publisher", "shelf"."book.author.publisher.id", """ +
            """"shelf"."book.author.publisher.name", "shelf"."author.name" FROM (SELECT "book"."id" AS "book.id", """ +
            """"book"."title" AS "book.title", "book"."author" AS "book.author", "author"."id" AS """ +
            """"book.author.id", "author"."name" AS "book.author.name", "author"."publisher" AS """ +
            """"book.author.publisher", "publisher"."id" AS "book.author.publisher.id", "publisher"."name" AS """ +
            """"book.author.publisher.name", "author"."name" AS "author.name" FROM "main"."book" """ +
            """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" LEFT JOIN "main"."publisher" """ +
            """ON "author"."publisher" = "publisher"."id") AS "shelf" ORDER BY "shelf"."book.id" ASC""",
        rows = deepBooks.map { book -> Row2(book, book.author.name) }
      ),
      QueryCase(
        label = "two entities of one occurrence in a CTE",
        query = WITH(pairs) { cte -> SELECT.COLUMNS(cte[BOOK], cte[AUTHOR]) FROM cte },
        sql = """WITH "pairs" AS (SELECT "book"."id" AS "book.id", "book"."title" AS "book.title", "book"."author" """ +
            """AS "book.author", "author"."id" AS "book.author.id", "author"."name" AS "book.author.name", """ +
            """"author"."publisher" AS "book.author.publisher", "author"."id" AS "author.id", "author"."name" AS """ +
            """"author.name", "author"."publisher" AS "author.publisher" FROM "main"."book" LEFT JOIN """ +
            """"main"."author" ON "book"."author" = "author"."id") SELECT "pairs"."book.id", "pairs"."book.title", """ +
            """"pairs"."book.author", "pairs"."book.author.id", "pairs"."book.author.name", """ +
            """"pairs"."book.author.publisher", "pairs"."author.id", "pairs"."author.name", """ +
            """"pairs"."author.publisher" FROM "pairs"""",
        rows = shallowBooks.map { book -> Row2(book, book.author) }
      ),
      QueryCase(
        label = "an alias named like a related table",
        query = SELECT.COLUMNS(renamed[named], renamed[namedTitle]) FROM renamed,
        sql = """SELECT "renamed"."author.id", "renamed"."author.title", "renamed"."author.author", """ +
            """"renamed"."author.author.id", "renamed"."author.author.name", "renamed"."author.author.publisher", """ +
            """"renamed"."name" FROM (SELECT "author"."id" AS "author.id", "author"."title" AS "author.title", """ +
            """"author"."author" AS "author.author", "author_2"."id" AS "author.author.id", "author_2"."name" AS """ +
            """"author.author.name", "author_2"."publisher" AS "author.author.publisher", "author"."title" AS """ +
            """"name" FROM "main"."book" AS "author" LEFT JOIN "main"."author" AS "author_2" """ +
            """ON "author"."author" = "author_2"."id") AS "renamed"""",
        rows = shallowBooks.map { book -> Row2(book, book.title) }
      ),
      QueryCase(
        label = "an outer-joined derived entity with nullable()",
        query = SELECT
          .COLUMNS(PUBLISHER, writers[AUTHOR].nullable())
          .FROM(PUBLISHER)
          .LEFT_JOIN(writers)
          .ON(writers[AUTHOR.PUBLISHER] IS PUBLISHER.ID)
          .ORDER_BY(PUBLISHER.ID.ASC()),
        sql = """SELECT "publisher"."id", "publisher"."name", "writers"."author.id", "writers"."author.name", """ +
            """"writers"."author.publisher" FROM "main"."publisher" LEFT JOIN (SELECT "author"."id" AS """ +
            """"author.id", "author"."name" AS "author.name", "author"."publisher" AS "author.publisher", """ +
            """"author"."name" AS "pen_name" FROM "main"."author") AS "writers" """ +
            """ON "writers"."author.publisher" = "publisher"."id" ORDER BY "publisher"."id" ASC""",
        rows = listOf(
          Row2(tor, shallowAda),
          Row2(unnamedPublisher, shallowLinus),
          Row2(orbit, null)
        )
      ),
      QueryCase(
        label = "SELECT FROM a derived source of derived entities",
        query = SELECT.FROM(again).ORDER_BY(again[totalsCount].ASC()),
        sql = """SELECT "again"."totals.author.id", "again"."totals.author.name", """ +
            """"again"."totals.author.publisher", "again"."totals.book_count" FROM (SELECT "totals"."author.id" AS """ +
            """"totals.author.id", "totals"."author.name" AS "totals.author.name", "totals"."author.publisher" AS """ +
            """"totals.author.publisher", """ +
            """"totals"."book_count" AS "totals.book_count" FROM ($totalsBody) AS "totals") AS "again" """ +
            """ORDER BY "again"."totals.book_count" ASC""",
        rows = listOf(
          Row2(mary, 0L),
          Row2(shallowAda, 1L),
          Row2(graceAuthor, 1L),
          Row2(shallowLinus, 1L)
        )
      ),
      QueryCase(
        label = "a derived entity in selected rows",
        query = SELECT
          .COLUMNS(totalsAuthor, PUBLISHER.all())
          .FROM(totals)
          .INNER_JOIN(PUBLISHER)
          .ON(totals[AUTHOR.PUBLISHER] IS PUBLISHER.ID)
          .ORDER_BY(PUBLISHER.ID.ASC())
          .mapTo { row -> row[totalsAuthor] to row[PUBLISHER.NAME] },
        sql = """SELECT $totalsColumns, "publisher"."id", "publisher"."name" FROM ($totalsBody) AS "totals" """ +
            """INNER JOIN "main"."publisher" ON "totals"."author.publisher" = "publisher"."id" """ +
            """ORDER BY "publisher"."id" ASC""",
        rows = listOf(shallowAda to "Tor", shallowLinus to null)
      )
    )

    val results = libraryDatabase().use { database ->
      database.execSQL("INSERT INTO author VALUES (4, 'Mary', NULL)")
      database.execSQL("INSERT INTO publisher VALUES (3, 'Orbit')")
      cases.map { case -> case.actual(database) }
    }

    assertThat(results).isEqualTo(cases.map(QueryCase::expected))
  }

  @Test
  fun `view entity values export their own columns`() {
    val summaryId = AUTHOR_SUMMARY.AUTHOR_ID AS "summary_id"
    val summaries = SELECT.COLUMNS(AUTHOR_SUMMARY, summaryId) FROM AUTHOR_SUMMARY AS "summaries"
    val joinedSummaries = SELECT
      .COLUMNS(AUTHOR.NAME, AUTHOR_SUMMARY.nullable())
      .FROM(AUTHOR)
      .LEFT_JOIN(AUTHOR_SUMMARY)
      .ON(AUTHOR_SUMMARY.AUTHOR_ID IS AUTHOR.ID) AS "joined_summaries"
    val ada = AuthorSummary(
      id = 1L,
      name = "Ada"
    )
    val grace = AuthorSummary(
      id = 2L,
      name = "Grace"
    )
    val cases = listOf(
      QueryCase(
        label = "a view entity and its own field",
        query = SELECT
          .COLUMNS(summaries[AUTHOR_SUMMARY], summaries[AUTHOR_SUMMARY.NAME])
          .FROM(summaries)
          .WHERE(summaries[AUTHOR_SUMMARY.NAME] LIKE "%a%")
          .ORDER_BY(summaries[AUTHOR_SUMMARY.NAME].DESC()),
        sql = """SELECT "summaries"."author_summary.author.id", "summaries"."author_summary.name", """ +
            """"summaries"."author_summary.name" FROM (SELECT "author_summary"."author.id" AS """ +
            """"author_summary.author.id", "author_summary"."name" AS "author_summary.name", """ +
            """"author_summary"."author.id" AS "summary_id" FROM "main"."author_summary") AS "summaries" """ +
            """WHERE "summaries"."author_summary.name" LIKE ? ORDER BY "summaries"."author_summary.name" DESC""",
        rows = listOf(Row2(grace, "Grace"), Row2(ada, "Ada"))
      ),
      QueryCase(
        label = "a nullable view entity and its own field",
        query = SELECT
          .COLUMNS(joinedSummaries[AUTHOR.NAME], joinedSummaries[AUTHOR_SUMMARY.nullable()])
          .FROM(joinedSummaries)
          .ORDER_BY(joinedSummaries[AUTHOR_SUMMARY.NAME.nullable()].DESC()),
        sql = """SELECT "joined_summaries"."author.name", "joined_summaries"."author_summary.author.id", """ +
            """"joined_summaries"."author_summary.name" FROM (SELECT "author"."name" AS "author.name", """ +
            """"author_summary"."author.id" AS "author_summary.author.id", "author_summary"."name" AS """ +
            """"author_summary.name" FROM "main"."author" LEFT JOIN "main"."author_summary" """ +
            """ON "author_summary"."author.id" = "author"."id") AS "joined_summaries" """ +
            """ORDER BY "joined_summaries"."author_summary.name" DESC""",
        rows = listOf(
          Row2("Grace", grace),
          Row2("Ada", ada),
          Row2("Linus", null)
        )
      )
    )

    val results = libraryDatabase().use { database ->
      database.createViews(AUTHOR_SUMMARY_VIEW)
      cases.map { case -> case.actual(database) }
    }

    assertThat(results).isEqualTo(cases.map(QueryCase::expected))
  }

  @Test
  fun `compound arms read entity values of the same table in the same place`() {
    val writer = AUTHOR AS "writer"
    val bookColumns = """"book"."id", "book"."title", "book"."author""""
    val authorColumns = """"author"."id", "author"."name", "author"."publisher""""
    val authorJoin = """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
    val books = SELECT
      .COLUMNS(BOOK, BOOK.TITLE)
      .FROM(BOOK)
    val wideArm = """SELECT $authorColumns, "publisher"."id", "publisher"."name" FROM "main"."author" """ +
        """INNER JOIN "main"."publisher" ON "publisher"."id" = "author"."publisher" WHERE "author"."id" = ?"""
    val cases = listOf(
      QueryCase(
        label = "UNION ALL of entities with a joined relationship",
        query = books.WHERE(BOOK.ID IS 1L) UNION_ALL books.WHERE(BOOK.ID IS 3L),
        sql = """SELECT $bookColumns, $authorColumns, "book"."title" FROM "main"."book" $authorJoin """ +
            """WHERE "book"."id" = ? UNION ALL SELECT $bookColumns, $authorColumns, "book"."title" """ +
            """FROM "main"."book" $authorJoin WHERE "book"."id" = ?""",
        rows = listOf(Row2(shallowBooks[0], "Dune"), Row2(shallowBooks[2], "Kernel"))
      ),
      QueryCase(
        label = "UNION of an entity and an aliased occurrence of its table",
        query = SELECT
          .COLUMNS(AUTHOR, AUTHOR.NAME)
          .FROM(AUTHOR)
          .WHERE(AUTHOR.ID IS 1L) UNION SELECT
          .COLUMNS(writer, writer.NAME)
          .FROM(writer)
          .WHERE(writer.ID IS 1L),
        sql = """SELECT $authorColumns, "author"."name" FROM "main"."author" WHERE "author"."id" = ? UNION """ +
            """SELECT "writer"."id", "writer"."name", "writer"."publisher", "writer"."name" FROM "main"."author" """ +
            """AS "writer" WHERE "writer"."id" = ?""",
        rows = listOf(Row2(shallowAda, "Ada"))
      ),
      QueryCase(
        label = "UNION ALL of a nullable entity and an entity",
        query = SELECT
          .COLUMNS(AUTHOR.NAME, BOOK.nullable())
          .FROM(AUTHOR)
          .LEFT_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS AUTHOR.ID)
          .WHERE(AUTHOR.ID IS 4L) UNION_ALL SELECT
          .COLUMNS(AUTHOR.NAME, BOOK)
          .FROM(BOOK)
          .INNER_JOIN(AUTHOR)
          .ON(AUTHOR.ID IS BOOK.AUTHOR)
          .WHERE(BOOK.ID IS 1L),
        sql = """SELECT "author"."name", $bookColumns, $authorColumns FROM "main"."author" LEFT JOIN "main"."book" """ +
            """ON "book"."author" = "author"."id" WHERE "author"."id" = ? UNION ALL SELECT "author"."name", """ +
            """$bookColumns, $authorColumns FROM "main"."book" INNER JOIN "main"."author" """ +
            """ON "author"."id" = "book"."author" WHERE "book"."id" = ?""",
        rows = listOf(Row2("Mary", null), Row2("Ada", shallowBooks[0]))
      ),
      QueryCase(
        label = "UNION ALL of an entity query and a lone derived entity",
        query = SELECT
          .FROM(AUTHOR)
          .WHERE(AUTHOR.ID IS 1L) UNION_ALL SELECT
          .COLUMN(totals[AUTHOR])
          .FROM(totals)
          .WHERE(totals[bookCount] IS 0L),
        sql = """SELECT $authorColumns FROM "main"."author" WHERE "author"."id" = ? UNION ALL """ +
            """SELECT $totalsColumns FROM ($totalsBody) AS "totals" WHERE "totals"."book_count" = ?""",
        rows = listOf(shallowAda, mary)
      ),
      QueryCase(
        label = "INTERSECT of an entity and a nullable entity",
        query = (wide.WHERE(AUTHOR.ID IS 1L) INTERSECT wideNullable)
          .mapTo { row -> row[AUTHOR] to row[PUBLISHER.NAME] },
        sql = """$wideArm INTERSECT SELECT $authorColumns, "publisher"."id", "publisher"."name" FROM """ +
            """"main"."author" INNER JOIN "main"."publisher" ON "publisher"."id" = "author"."publisher"""",
        rows = listOf(shallowAda to "Tor")
      ),
      QueryCase(
        label = "UNION ALL of selected rows",
        query = (wide.WHERE(AUTHOR.ID IS 1L) UNION_ALL wide.WHERE(AUTHOR.ID IS 3L))
          .mapTo { row -> row[AUTHOR] to row[PUBLISHER.NAME] },
        sql = "$wideArm UNION ALL $wideArm",
        rows = listOf(shallowAda to "Tor", shallowLinus to null)
      )
    )

    val results = libraryDatabase().use { database ->
      database.execSQL("INSERT INTO author VALUES (4, 'Mary', NULL)")
      cases.map { case -> case.actual(database) }
    }

    assertThat(results).isEqualTo(cases.map(QueryCase::expected))
  }

  @Test
  fun `exported entity values outside their contracts reject before SQL`() {
    val named = BOOK AS "author"
    val wideFields = SELECT
      .COLUMNS(AUTHOR.all(), PUBLISHER.all())
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
    val widePublishers = SELECT
      .COLUMNS(PUBLISHER, AUTHOR.all())
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
    val failures = listOf(
      { SELECT.COLUMNS(BOOK, BOOK.TITLE) FROM BOOK AS "books" },
      { WITH(SELECT.COLUMNS(named.ID, AUTHOR.ID) FROM named) { cte -> SELECT.FROM(cte) } },
      { totals[BOOK] },
      { nullableBooks[BOOK] },
      { nullableBooks[BOOK.TITLE] },
      { totals[AUTHOR.nullable()] },
      { (SELECT.COLUMNS(totals[AUTHOR], totals[bookCount]) FROM totals).queryDeep() },
      {
        SELECT
          .COLUMNS(PUBLISHER, writers[AUTHOR])
          .FROM(PUBLISHER)
          .LEFT_JOIN(writers)
          .ON(writers[AUTHOR.PUBLISHER] IS PUBLISHER.ID)
          .compile()
      },
      { SELECT.COLUMNS(AUTHOR) },
      { SELECT.COLUMNS(totals[AUTHOR]) },
      { SELECT.DISTINCT(AUTHOR.nullable()) },
      {
        INSERT
          .INTO(AUTHOR)
          .COLUMNS(*arrayOf(AUTHOR.ID, AUTHOR.NAME))
          .SELECT(SELECT.COLUMNS(AUTHOR, AUTHOR.NAME) FROM AUTHOR)
      },
      { wide UNION wideFields },
      { wide UNION_ALL widePublishers },
      { wide INTERSECT wideFields },
      { wide EXCEPT widePublishers },
      { wide UNION wideNullable }
    ).map { failure -> assertThrows(IllegalArgumentException::class.java) { failure() }.message }

    assertThat(failures).isEqualTo(
      listOf(
        """Derived source books exports two columns named book.title, from selected value 1 (the book entity) """ +
            """and selected value 2 ("book"."title"); alias one of them""",
        """A CTE exports two columns named author.id, from selected value 1 ("author"."id") and selected value 2 """ +
            """("author"."id"); alias one of them""",
        "totals exports only the entities its query selects; use the selected instance",
        "shelves exports the book entity as nullable; read it with .nullable()",
        """shelves exports "book"."title" as nullable; read it with .nullable()""",
        "totals exports the author entity as not nullable; read it without .nullable()",
        "queryDeep() deepens entity values, and this selection has none",
        "An outer join can leave the author entity of writers NULL; select writers[author].nullable()",
        "The author entity is the only selected value; select one entity with SELECT FROM author",
        "The author entity of totals is the only selected value; select it with SELECT COLUMN totals[author]",
        "The author entity is the only selected value; select another value beside it",
        "INSERT … SELECT takes one value per column, but selected value 1 is an entity",
        """Result value 1 is the author entity in the left SELECT but "author"."id" in the connected SELECT; """ +
            "select entities of the same table in the same place",
        "Result value 1 is the author entity in the left SELECT but the publisher entity in the connected SELECT; " +
            "select entities of the same table in the same place",
        """Result value 1 is the author entity in the left SELECT but "author"."id" in the connected SELECT; """ +
            "select entities of the same table in the same place",
        "Result value 1 is the author entity in the left SELECT but the publisher entity in the connected SELECT; " +
            "select entities of the same table in the same place",
        "Result value 1 (author) is NOT NULL in the left SELECT but nullable in the connected SELECT; select it " +
            "with .nullable() in the left SELECT"
      )
    )
  }
}

private data class QueryCase(
  val label: String,
  val query: SelectQuery<*, *>,
  val sql: String,
  val rows: List<Any?>
) {
  fun actual(database: TestDatabase) = label to (query.compile().sql to query.rows(database))

  val expected get() = label to (sql to rows)
}
