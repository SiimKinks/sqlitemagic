package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

private data class AuthorWithCount(
  val author: Author,
  val bookCount: Long
)

internal class EntityValueSelectTest {
  private val shallowBooks = books(shallow = true)
  private val deepBooks = books(shallow = false)
  private val shallowAuthors = shallowBooks.map(Book::author)
  private val shallowAda = shallowAuthors[0]
  private val shallowLinus = shallowAuthors[2]
  private val authorColumns = authorColumns(qualifier = "author")
  private val bookColumns = bookColumns(qualifier = "book")
  private val publisherColumns = """"publisher"."id", "publisher"."name""""
  private val publisherJoin = """INNER JOIN "main"."publisher" ON "publisher"."id" = "author"."publisher""""

  private fun authorColumns(qualifier: String) =
    """"$qualifier"."id", "$qualifier"."name", "$qualifier"."publisher""""

  private fun bookColumns(qualifier: String) = """"$qualifier"."id", "$qualifier"."title", "$qualifier"."author""""

  @Test
  fun `entity values read whole entities beside other values`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val sql: String,
      val rows: List<Any?>
    )

    val counts = """SELECT $authorColumns, COUNT("book"."id") FROM "main"."author" LEFT JOIN "main"."book" """ +
        """ON "book"."author" = "author"."id" GROUP BY "author"."id" ORDER BY "author"."id" ASC"""
    val countRows = listOf(
      Row2(shallowAda, 2L),
      Row2(graceAuthor, 1L),
      Row2(shallowLinus, 1L)
    )
    val other = PUBLISHER AS "other"
    val cases = listOf(
      Case(
        label = "an entity with an aggregate per group",
        query = SELECT
          .COLUMNS(AUTHOR, COUNT(BOOK.ID))
          .FROM(AUTHOR)
          .LEFT_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS AUTHOR.ID)
          .GROUP_BY(AUTHOR.ID)
          .ORDER_BY(AUTHOR.ID.ASC()),
        sql = counts,
        rows = countRows
      ),
      Case(
        label = "an entity with an aggregate per group in infix syntax",
        query = (SELECT.COLUMNS(AUTHOR, COUNT(BOOK.ID)) FROM AUTHOR LEFT_JOIN BOOK ON (BOOK.AUTHOR IS AUTHOR.ID)
            GROUP_BY AUTHOR.ID ORDER_BY AUTHOR.ID.ASC()),
        sql = counts,
        rows = countRows
      ),
      Case(
        label = "two entities of a join",
        query = SELECT
          .COLUMNS(AUTHOR, PUBLISHER)
          .FROM(AUTHOR)
          .INNER_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
          .ORDER_BY(AUTHOR.ID.ASC()),
        sql = """SELECT $authorColumns, "publisher"."id", "publisher"."name" FROM "main"."author" $publisherJoin """ +
            """ORDER BY "author"."id" ASC""",
        rows = listOf(
          Row2(shallowAda, tor),
          Row2(shallowLinus, unnamedPublisher)
        )
      ),
      Case(
        label = "columns after an entity",
        query = SELECT
          .COLUMNS(AUTHOR.NAME, PUBLISHER, AUTHOR.ID)
          .FROM(AUTHOR)
          .INNER_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
          .ORDER_BY(AUTHOR.ID.ASC()),
        sql = """SELECT "author"."name", "publisher"."id", "publisher"."name", "author"."id" FROM "main"."author" """ +
            """$publisherJoin ORDER BY "author"."id" ASC""",
        rows = listOf(
          Row3("Ada", tor, 1L),
          Row3("Linus", unnamedPublisher, 3L)
        )
      ),
      Case(
        label = "two occurrences of one table",
        query = SELECT
          .COLUMNS(PUBLISHER, other)
          .FROM(PUBLISHER)
          .INNER_JOIN(other)
          .ON(other.ID GREATER_THAN PUBLISHER.ID),
        sql = """SELECT "publisher"."id", "publisher"."name", "other"."id", "other"."name" FROM "main"."publisher" """ +
            """INNER JOIN "main"."publisher" AS "other" ON "other"."id" > "publisher"."id"""",
        rows = listOf(Row2(tor, unnamedPublisher))
      ),
      Case(
        label = "an entity mapped with its count",
        query = SELECT
          .COLUMNS(AUTHOR, COUNT(BOOK.ID))
          .FROM(AUTHOR)
          .LEFT_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS AUTHOR.ID)
          .GROUP_BY(AUTHOR.ID)
          .ORDER_BY(AUTHOR.ID.ASC())
          .mapTo(::AuthorWithCount),
        sql = counts,
        rows = countRows.map { (author, count) -> AuthorWithCount(author, count) }
      )
    )

    libraryDatabaseWithNotes().use { database ->
      cases.forEach { case ->
        assertWithMessage(case.label)
          .that(case.query.compile().sql to case.query.rows(database))
          .isEqualTo(case.sql to case.rows)
      }
    }
  }

  @Test
  fun `entity values join and read their relationships`() {
    data class Case(
      val label: String,
      val query: SelectQuery<*, *>,
      val sql: String,
      val rows: List<Any?>,
      val observed: List<String>
    )

    val authorJoin = """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
    val publisherJoin = """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id""""
    val byBook = """ORDER BY "book"."id" ASC"""
    val mary = Author(
      id = 4L,
      name = "Mary",
      publisher = null
    )
    val orbit = Publisher(
      id = 3L,
      name = "Orbit"
    )
    val writer = AUTHOR AS "writer"
    val writerColumns = authorColumns(qualifier = "writer")
    val bookAuthor = AUTHOR AS "book_author"
    val booksWithAuthors = SELECT
      .COLUMNS(BOOK, AUTHOR)
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
    val authorsWithBooks = SELECT
      .COLUMNS(AUTHOR, BOOK.nullable())
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON(BOOK.AUTHOR IS AUTHOR.ID)
      .ORDER_BY(BOOK.ID.ASC())
    val bookJoin = """LEFT JOIN "main"."book" ON "book"."author" = "author"."id""""
    val cases = listOf(
      Case(
        label = "an always-joined relationship",
        query = SELECT
          .COLUMNS(BOOK, BOOK.TITLE)
          .FROM(BOOK)
          .ORDER_BY(BOOK.ID.ASC()),
        sql = """SELECT $bookColumns, $authorColumns, "book"."title" FROM "main"."book" $authorJoin $byBook""",
        rows = shallowBooks.map { book -> Row2(book, book.title) },
        observed = listOf("book", "author")
      ),
      Case(
        label = "queryDeep() reads deep relationships in full",
        query = (SELECT.COLUMNS(BOOK, BOOK.TITLE) FROM BOOK ORDER_BY BOOK.ID.ASC()).queryDeep(),
        sql = """SELECT $bookColumns, $authorColumns, $publisherColumns, "book"."title" FROM "main"."book" """ +
            """$authorJoin $publisherJoin $byBook""",
        rows = deepBooks.map { book -> Row2(book, book.title) },
        observed = listOf("book", "author", "publisher")
      ),
      Case(
        label = "a value and its relationship's target share one join",
        query = booksWithAuthors,
        sql = """SELECT $bookColumns, $authorColumns, $authorColumns FROM "main"."book" $authorJoin $byBook""",
        rows = shallowBooks.map { book -> Row2(book, book.author) },
        observed = listOf("book", "author")
      ),
      Case(
        label = "a value and its relationship's target share one join with queryDeep()",
        query = booksWithAuthors.queryDeep(),
        sql = """SELECT $bookColumns, $authorColumns, $publisherColumns, $authorColumns, $publisherColumns """ +
            """FROM "main"."book" $authorJoin $publisherJoin $byBook""",
        rows = deepBooks.map { book -> Row2(book, book.author) },
        observed = listOf("book", "author", "publisher")
      ),
      Case(
        label = "a joined value reads its relationship from the FROM table",
        query = authorsWithBooks,
        sql = """SELECT $authorColumns, $bookColumns, $authorColumns FROM "main"."author" $bookJoin $byBook""",
        rows = listOf(Row2(mary, null)) + shallowBooks.map { book -> Row2(book.author, book) },
        observed = listOf("author", "book")
      ),
      Case(
        label = "a joined value reads its relationship from the FROM table with queryDeep()",
        query = authorsWithBooks.queryDeep(),
        sql = """SELECT $authorColumns, $publisherColumns, $bookColumns, $authorColumns, $publisherColumns """ +
            """FROM "main"."author" $bookJoin $publisherJoin $byBook""",
        rows = listOf(Row2(mary, null)) + deepBooks.map { book -> Row2(book.author, book) },
        observed = listOf("author", "book", "publisher")
      ),
      Case(
        label = "a join of the relationship wins over the FROM table",
        query = SELECT
          .COLUMNS(AUTHOR, BOOK.nullable())
          .FROM(AUTHOR)
          .LEFT_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS AUTHOR.ID)
          .LEFT_JOIN(bookAuthor)
          .ON(BOOK.AUTHOR IS bookAuthor.ID)
          .ORDER_BY(BOOK.ID.ASC()),
        sql = """SELECT $authorColumns, $bookColumns, ${authorColumns(qualifier = "book_author")} """ +
            """FROM "main"."author" $bookJoin LEFT JOIN "main"."author" AS "book_author" """ +
            """ON "book"."author" = "book_author"."id" $byBook""",
        rows = listOf(Row2(mary, null)) + shallowBooks.map { book -> Row2(book.author, book) },
        observed = listOf("author", "book")
      ),
      Case(
        label = "an aliased join supplies the relationship",
        query = SELECT
          .COLUMNS(BOOK, writer.nullable())
          .FROM(BOOK)
          .LEFT_JOIN(writer)
          .ON(writer.ID IS BOOK.AUTHOR)
          .ORDER_BY(BOOK.ID.ASC()),
        sql = """SELECT $bookColumns, $writerColumns, $writerColumns FROM "main"."book" """ +
            """LEFT JOIN "main"."author" AS "writer" ON "writer"."id" = "book"."author" $byBook""",
        rows = shallowBooks.map { book -> Row2(book, book.author) },
        observed = listOf("book", "author")
      ),
      Case(
        label = "relationships two levels down",
        query = SELECT
          .COLUMNS(SERIES, SERIES.NAME)
          .FROM(SERIES)
          .ORDER_BY(SERIES.ID.ASC()),
        sql = """SELECT "series"."id", "series"."name", "series"."first_book", "series"."last_book", """ +
            """$bookColumns, ${bookColumns(qualifier = "last_book")}, $authorColumns, """ +
            """${authorColumns(qualifier = "last_book_author")}, "series"."name" FROM "main"."series" """ +
            """LEFT JOIN "main"."book" ON "series"."first_book" = "book"."id" """ +
            """LEFT JOIN "main"."book" AS "last_book" ON "series"."last_book" = "last_book"."id" $authorJoin """ +
            """LEFT JOIN "main"."author" AS "last_book_author" ON "last_book"."author" = "last_book_author"."id" """ +
            """ORDER BY "series"."id" ASC""",
        rows = listOf(
          Row2(
            Series(
              id = 1L,
              name = "Classics",
              firstBook = shallowBooks[0],
              lastBook = shallowBooks[1]
            ),
            "Classics"
          ),
          Row2(
            Series(
              id = 2L,
              name = "Solo",
              firstBook = shallowBooks[2],
              lastBook = null
            ),
            "Solo"
          )
        ),
        observed = listOf("series", "book", "author")
      ),
      Case(
        label = "a value that its relationship path joins outer",
        query = SELECT
          .COLUMNS(AUTHOR, PUBLISHER.nullable())
          .FROM(AUTHOR)
          .ORDER_BY(AUTHOR.ID.ASC()),
        sql = """SELECT $authorColumns, $publisherColumns FROM "main"."author" $publisherJoin """ +
            """ORDER BY "author"."id" ASC""",
        rows = listOf(
          Row2(shallowAda, tor),
          Row2(graceAuthor, null),
          Row2(shallowLinus, unnamedPublisher),
          Row2(mary, null)
        ),
        observed = listOf("author", "publisher")
      ),
      Case(
        label = "nullable() of an occurrence that no outer join leaves NULL",
        query = SELECT
          .COLUMNS(BOOK.TITLE, AUTHOR.nullable())
          .FROM(BOOK)
          .INNER_JOIN(AUTHOR)
          .ON(AUTHOR.ID IS BOOK.AUTHOR)
          .ORDER_BY(BOOK.ID.ASC()),
        sql = """SELECT "book"."title", $authorColumns FROM "main"."book" INNER JOIN "main"."author" """ +
            """ON "author"."id" = "book"."author" $byBook""",
        rows = shallowBooks.map { book -> Row2(book.title, book.author) },
        observed = listOf("book", "author")
      ),
      Case(
        label = "RIGHT JOIN",
        query = SELECT
          .COLUMNS(AUTHOR.nullable(), PUBLISHER)
          .FROM(AUTHOR)
          .RIGHT_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
          .ORDER_BY(PUBLISHER.ID.ASC()),
        sql = """SELECT $authorColumns, $publisherColumns FROM "main"."author" RIGHT JOIN "main"."publisher" """ +
            """ON "publisher"."id" = "author"."publisher" ORDER BY "publisher"."id" ASC""",
        rows = listOf(
          Row2(shallowAda, tor),
          Row2(shallowLinus, unnamedPublisher),
          Row2(null, orbit)
        ),
        observed = listOf("author", "publisher")
      ),
      Case(
        label = "FULL JOIN",
        query = SELECT
          .COLUMNS(AUTHOR.nullable(), PUBLISHER.nullable())
          .FROM(AUTHOR)
          .FULL_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
          .ORDER_BY(AUTHOR.ID.ASC()),
        sql = """SELECT $authorColumns, $publisherColumns FROM "main"."author" FULL JOIN "main"."publisher" """ +
            """ON "publisher"."id" = "author"."publisher" ORDER BY "author"."id" ASC""",
        rows = listOf(
          Row2(null, orbit),
          Row2(shallowAda, tor),
          Row2(graceAuthor, null),
          Row2(shallowLinus, unnamedPublisher),
          Row2(mary, null)
        ),
        observed = listOf("author", "publisher")
      )
    )

    libraryDatabase().use { database ->
      database.execSQL("INSERT INTO author VALUES (4, 'Mary', NULL)")
      database.execSQL("INSERT INTO publisher VALUES (3, 'Orbit')")
      cases.forEach { case ->
        assertWithMessage(case.label)
          .that(Triple(case.query.compile().sql, case.query.rows(database), case.query.observedTables()))
          .isEqualTo(Triple(case.sql, case.rows, case.observed))
      }
    }
  }

  @Test
  fun `joined relationships whose target row is missing fail or fall back to the stored ID`() {
    data class Reads(
      val required: String?,
      val deep: List<Row2<Author, String>>
    )

    val reads = Reads(
      required = sqlExceptionMessage {
        SELECT
          .COLUMNS(BOOK, BOOK.TITLE)
          .FROM(BOOK)
          .rowsIn { libraryDatabase().apply { execSQL("DELETE FROM author WHERE id = 1") } }
      },
      deep = (SELECT.COLUMNS(AUTHOR, AUTHOR.NAME) FROM AUTHOR WHERE (AUTHOR.NAME IS "Ada"))
        .queryDeep()
        .rowsIn { libraryDatabase().apply { execSQL("DELETE FROM publisher WHERE id = 1") } }
    )

    assertThat(reads).isEqualTo(
      Reads(
        required = """Required relationship "author" referenced a missing row""",
        deep = listOf(Row2(shallowAda, "Ada"))
      )
    )
  }

  @Test
  fun `selected rows read entities by their table and fields selected themselves`() {
    data class Read(
      val owned: List<Pair<Author, String?>>,
      val live: List<Pair<Author, String?>>,
      val nullableOwned: List<Pair<String, Publisher?>>,
      val nullableLive: List<Pair<String, Publisher?>>,
      val entityField: String?,
      val unselectedEntity: String?,
      val nullableEntity: String?,
      val notNullableEntity: String?
    )

    val query = SELECT
      .COLUMNS(AUTHOR, PUBLISHER.all())
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
      .ORDER_BY(AUTHOR.ID.ASC())
    val nullableQuery = SELECT
      .COLUMNS(AUTHOR.all(), PUBLISHER.nullable())
      .FROM(AUTHOR)
      .ORDER_BY(AUTHOR.ID.ASC())

    val read = libraryDatabase().use { database ->
      val rows = query.rows(database)
      val nullableRows = nullableQuery.rows(database)
      Read(
        owned = rows.map { row -> row[AUTHOR] to row[PUBLISHER.NAME] },
        live = query
          .mapTo { row -> row[AUTHOR] to row[PUBLISHER.NAME] }
          .rows(database),
        nullableOwned = nullableRows.map { row -> row[AUTHOR.NAME] to row[PUBLISHER.nullable()] },
        nullableLive = nullableQuery
          .mapTo { row -> row[AUTHOR.NAME] to row[PUBLISHER.nullable()] }
          .rows(database),
        entityField = assertThrows(IllegalArgumentException::class.java) { rows.first()[AUTHOR.NAME] }.message,
        unselectedEntity = assertThrows(IllegalArgumentException::class.java) { rows.first()[BOOK] }.message,
        nullableEntity = assertThrows(IllegalArgumentException::class.java) { nullableRows.first()[PUBLISHER] }.message,
        notNullableEntity = assertThrows(IllegalArgumentException::class.java) {
          rows.first()[AUTHOR.nullable()]
        }.message
      )
    }

    val authors = listOf(shallowAda to "Tor", shallowLinus to null)
    val publishers = listOf("Ada" to tor, "Grace" to null, "Linus" to unnamedPublisher)
    assertThat(read).isEqualTo(
      Read(
        owned = authors,
        live = authors,
        nullableOwned = publishers,
        nullableLive = publishers,
        entityField = """"author"."name" is only part of the selected author entity; select it to read it""",
        unselectedEntity = "The book entity was not selected",
        nullableEntity = "The publisher entity is selected as nullable; read it with .nullable()",
        notNullableEntity = "The author entity is selected as not nullable; read it without .nullable()"
      )
    )
  }

  @Test
  fun `compiled entity selections keep their layout across parameter sets and observe their sources`() {
    val minimum = AUTHOR.ID.asParameter("minimum")
    val query = SELECT
      .COLUMNS(PUBLISHER.NAME, AUTHOR)
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
      .WHERE(AUTHOR.ID GREATER_THAN minimum)
      .ORDER_BY(AUTHOR.ID.ASC())

    val executions = libraryDatabase().use { database ->
      val compiled = query
        .usingConnection(database.connection)
        .compile()
      listOf(0L, 1L).map { value ->
        compiled
          .bind(minimum TO value)
          .execute()
      }
    }

    assertThat(executions to query.observedTables()).isEqualTo(
      listOf(
        listOf(Row2("Tor", shallowAda), Row2(null, shallowLinus)),
        listOf(Row2(null, shallowLinus))
      ) to listOf("author", "publisher")
    )
  }

  @Test
  fun `entity values compose with set operations, derived sources and existence checks`() {
    data class Case(
      val label: String,
      val rows: (TestDatabase) -> List<Any?>,
      val expected: List<Any?>
    )

    val joined = SELECT
      .COLUMNS(AUTHOR, PUBLISHER.NAME)
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
    val joinedRows = listOf(Row2(shallowAda, "Tor"), Row2(shallowLinus, null))
    val cases = listOf(
      Case(
        label = "INTERSECT",
        rows = (joined INTERSECT joined)::rows,
        expected = joinedRows
      ),
      Case(
        label = "EXCEPT",
        rows = (joined EXCEPT joined.WHERE(AUTHOR.ID IS 1L))::rows,
        expected = listOf(Row2(shallowLinus, null))
      ),
      Case(
        label = "DISTINCT",
        rows = SELECT
          .DISTINCT(AUTHOR, PUBLISHER.NAME)
          .FROM(BOOK)
          .INNER_JOIN(AUTHOR)
          .ON(AUTHOR.ID IS BOOK.AUTHOR)
          .INNER_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)::rows,
        expected = joinedRows
      ),
      Case(
        label = "SELECT FROM a derived source",
        rows = SELECT.FROM(joined AS "joined")::rows,
        expected = joinedRows
      ),
      Case(
        label = "SELECT FROM a common table expression",
        rows = WITH(joined, SELECT::FROM)::rows,
        expected = joinedRows
      ),
      Case(
        label = "count()",
        rows = { database ->
          listOf(
            joined
              .usingConnection(database.connection)
              .count()
              .execute()
          )
        },
        expected = listOf(2L)
      ),
      Case(
        label = "EXISTS",
        rows = SELECT.COLUMN(EXISTS(joined))::rows,
        expected = listOf(true)
      )
    )

    libraryDatabase().use { database ->
      cases.forEach { case ->
        assertWithMessage(case.label)
          .that(case.rows(database))
          .containsExactlyElementsIn(case.expected)
      }
    }
  }

  @Test
  fun `entity values join their relationships where they are read and select only their own columns in checks`() {
    data class Case(
      val label: String,
      val run: (TestDatabase) -> Pair<String, List<Any?>>,
      val sql: String,
      val rows: List<Any?>
    )

    fun SelectQuery<*, *>.sqlAndRows(database: TestDatabase) = compile().sql to rows(database)

    val books = SELECT
      .COLUMNS(BOOK, BOOK.TITLE)
      .FROM(BOOK)
    val authorJoin = """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""
    val publisherJoin = """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id""""
    val bookRows = shallowBooks.map { book -> Row2(book, book.title) }
    val deepArm = """SELECT $bookColumns, $authorColumns, $publisherColumns, "book"."title" FROM "main"."book" """ +
        """$authorJoin $publisherJoin"""
    val cases = listOf(
      Case(
        label = "DISTINCT",
        run = SELECT
          .DISTINCT(BOOK, BOOK.TITLE)
          .FROM(BOOK)::sqlAndRows,
        sql = """SELECT DISTINCT $bookColumns, $authorColumns, "book"."title" FROM "main"."book" $authorJoin""",
        rows = bookRows
      ),
      Case(
        label = "queryDeep() of a compound",
        run = (books INTERSECT books).queryDeep()::sqlAndRows,
        sql = "$deepArm INTERSECT $deepArm",
        rows = deepBooks.map { book -> Row2(book, book.title) }
      ),
      Case(
        label = "SELECT FROM a derived source",
        run = SELECT.FROM(books AS "books")::sqlAndRows,
        sql = """SELECT "books"."book.id", "books"."book.title", "books"."book.author", "books"."author.id", """ +
            """"books"."author.name", "books"."author.publisher", "books"."book.title" FROM (SELECT "book"."id" """ +
            """AS "book.id", "book"."title" AS "book.title", "book"."author" AS "book.author", "author"."id" """ +
            """AS "author.id", "author"."name" AS "author.name", "author"."publisher" AS "author.publisher", """ +
            """"book"."title" AS "book.title" FROM "main"."book" $authorJoin) AS "books"""",
        rows = bookRows
      ),
      Case(
        label = "count()",
        run = { database ->
          val count = books
            .LIMIT(2)
            .usingConnection(database.connection)
            .count()
          count.sql to listOf(count.execute())
        },
        sql = """SELECT count(*) FROM (SELECT $bookColumns, "book"."title" FROM "main"."book" LIMIT 2)""",
        rows = listOf(2L)
      ),
      Case(
        label = "EXISTS",
        run = SELECT.COLUMN(EXISTS(books))::sqlAndRows,
        sql = """SELECT EXISTS (SELECT $bookColumns, "book"."title" FROM "main"."book")""",
        rows = listOf(true)
      )
    )

    val results = libraryDatabase().use { database ->
      cases.map { case -> case.label to case.run(database) }
    }

    assertThat(results).isEqualTo(cases.map { it.label to (it.sql to it.rows) })
  }

  @Test
  fun `entity values outside their contracts reject before SQL`() {
    val aliased = AUTHOR AS "a"
    val joinedPublishers = SELECT
      .COLUMNS(AUTHOR, PUBLISHER.NAME)
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
    val failures = listOf(
      {
        SELECT
          .COLUMNS(AUTHOR.ID, PUBLISHER)
          .FROM(AUTHOR)
          .LEFT_JOIN(PUBLISHER)
          .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
          .compile()
      },
      {
        SELECT
          .COLUMNS(AUTHOR, PUBLISHER)
          .FROM(AUTHOR)
          .compile()
      },
      {
        SELECT
          .COLUMNS(aliased.NAME, AUTHOR)
          .FROM(aliased)
          .LEFT_JOIN(BOOK)
          .ON(BOOK.AUTHOR IS aliased.ID)
          .compile()
      },
      {
        SELECT
          .COLUMNS(BOOK.TITLE, SERIES)
          .FROM(BOOK)
          .compile()
      },
      {
        SELECT
          .COLUMNS(BOOK.TITLE, AUTHOR AS "writer")
          .FROM(BOOK)
          .compile()
      },
      {
        SELECT
          .COLUMNS(SERIES.NAME, BOOK)
          .FROM(SERIES)
          .compile()
      },
      {
        SELECT
          .COLUMNS(BOOK.TITLE, AUTHOR.NAME)
          .FROM(BOOK)
          .queryDeep()
      },
      { joinedPublishers.mapTo(AUTHOR) },
      { joinedPublishers UNION joinedPublishers }
    ).map { failure -> assertThrows(IllegalArgumentException::class.java) { failure() }.message }

    assertThat(failures).isEqualTo(
      listOf(
        "An outer join can leave the publisher entity NULL; select publisher.nullable()",
        "An outer join can leave the publisher entity NULL; select publisher.nullable()",
        "An outer join can leave the author entity NULL; select author.nullable()",
        "The series entity is not a source of this query; name it in FROM or a join",
        "The writer entity is not a source of this query; name it in FROM or a join",
        "Several relationships lead to book: series.first_book, series.last_book; join the one you mean",
        "queryDeep() deepens entity values, and this selection has none",
        "mapTo(author) reads plain fields, but selected value 1 is an entity",
        "UNION and derived exports take plain values, but this query selects the author entity"
      )
    )
  }
}
