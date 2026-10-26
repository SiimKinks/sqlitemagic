package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

internal class RelationshipSelectTest {
  private val bookFields = """"book"."id", "book"."title", "book"."author""""

  private fun authorFields(qualifier: String) =
    """"$qualifier"."id", "$qualifier"."name", "$qualifier"."publisher""""

  private fun publisherFields(qualifier: String) = """"$qualifier"."id", "$qualifier"."name""""

  @Test
  fun `shallow reads join only targets that their IDs cannot build`() {
    val books = SELECT FROM BOOK ORDER_BY BOOK.ID.ASC()
    val authors = SELECT FROM AUTHOR ORDER_BY AUTHOR.ID.ASC()

    assertThat(listOf(books, authors).map { it.compile().sql }).isEqualTo(
      listOf(
        """SELECT $bookFields, ${authorFields("author")} FROM "main"."book" """ +
            """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" ORDER BY "book"."id" ASC""",
        """SELECT ${authorFields("author")} FROM "main"."author" ORDER BY "author"."id" ASC"""
      )
    )
    assertThat(books.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true))
    assertThat(authors.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true).map(Book::author))
  }

  @Test
  fun `queryDeep follows every recursive relationship transitively`() {
    val deep = (SELECT FROM BOOK ORDER_BY BOOK.ID.ASC()).queryDeep()

    assertThat(deep.compile().sql).isEqualTo(
      """SELECT $bookFields, ${authorFields("author")}, ${publisherFields("publisher")} FROM "main"."book" """ +
          """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" """ +
          """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id" """ +
          """ORDER BY "book"."id" ASC"""
    )
    assertThat(deep.observedTables()).isEqualTo(listOf("book", "author", "publisher"))
    assertThat(deep.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = false))
    assertThat(
      (SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS "Linus"))
        .queryDeep()
        .rowsIn(::libraryDatabase)
    ).isEqualTo(listOf(linusAuthor))
  }

  @Test
  fun `counts read no values so they join no relationships and take outer-joined fields`() {
    val deep = (SELECT FROM BOOK).queryDeep()
    val titles = SELECT.COLUMN(BOOK.TITLE)
      .FROM(AUTHOR)
      .LEFT_JOIN(BOOK)
      .ON(AUTHOR.ID IS BOOK.AUTHOR)

    assertThat(observedSnapshot(deep.count())).isEqualTo(
      ObservedPlanSnapshot(
        sql = """SELECT count(*) FROM (SELECT $bookFields FROM "main"."book")""",
        arguments = emptyList(),
        sources = listOf("book")
      )
    )
    assertThat(
      libraryDatabase().use { database ->
        database.execSQL("INSERT INTO author VALUES (4, 'Mary', NULL)")
        listOf(deep, titles).map { query ->
          query
            .usingConnection(database.dbConnection())
            .count()
            .execute()
        }
      }
    ).isEqualTo(listOf(3L, 4L))
  }

  @Test
  fun `repeated targets take path names and suffixes where names are used`() {
    val (dune, emma, kernel) = books(shallow = false)
    val classics = Series(
      id = 1L,
      name = "Classics",
      firstBook = dune,
      lastBook = emma
    )
    val solo = Series(
      id = 2L,
      name = "Solo",
      firstBook = kernel,
      lastBook = null
    )
    val deep = (SELECT FROM SERIES ORDER_BY SERIES.ID.ASC()).queryDeep()

    assertThat(deep.compile().sql).isEqualTo(
      """SELECT "series"."id", "series"."name", "series"."first_book", "series"."last_book", """ +
          """$bookFields, "last_book"."id", "last_book"."title", "last_book"."author", """ +
          "${authorFields("author")}, ${authorFields("last_book_author")}, ${publisherFields("publisher")}, " +
          """${publisherFields("last_book_author_publisher")} FROM "main"."series" """ +
          """LEFT JOIN "main"."book" ON "series"."first_book" = "book"."id" """ +
          """LEFT JOIN "main"."book" AS "last_book" ON "series"."last_book" = "last_book"."id" """ +
          """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" """ +
          """LEFT JOIN "main"."author" AS "last_book_author" ON "last_book"."author" = """ +
          """"last_book_author"."id" """ +
          """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id" """ +
          """LEFT JOIN "main"."publisher" AS "last_book_author_publisher" """ +
          """ON "last_book_author"."publisher" = "last_book_author_publisher"."id" """ +
          """ORDER BY "series"."id" ASC"""
    )
    assertThat(deep.rowsIn(::libraryDatabase)).isEqualTo(listOf(classics, solo))
    assertThat(
      listOf(SERIES AS "book", SERIES AS "last_book").map { root ->
        Regex("""JOIN "main"\."[a-z]+"(?: AS "([a-z_0-9]+)")?""")
          .findAll((SELECT FROM root).compile().sql)
          .map { match -> match.groupValues[1].ifEmpty { "-" } }
          .toList()
      }
    ).isEqualTo(
      listOf(
        listOf("first_book", "last_book", "-", "last_book_author"),
        listOf("-", "last_book_2", "-", "last_book_author")
      )
    )
  }

  @Test
  fun `unjoined fields join along their single relationship path`() {
    val publishers = SELECT
      .COLUMNS(BOOK.TITLE, PUBLISHER.NAME)
      .FROM(BOOK)
      .ORDER_BY(BOOK.ID.ASC())
    val adas = SELECT COLUMN AUTHOR.NAME FROM BOOK WHERE (AUTHOR.NAME IS "Ada")
    val placed = SELECT.COLUMN(BOOK.TITLE)
      .FROM(BOOK)
      .INNER_JOIN(PERSON)
      .ON(PERSON.NAME IS AUTHOR.NAME)
      .ORDER_BY(BOOK.ID.ASC())
    val aliased = AUTHOR AS "a"
    val throughUserJoin = SELECT.COLUMN(PUBLISHER.NAME)
      .FROM(BOOK)
      .INNER_JOIN(aliased)
      .ON(aliased.ID IS BOOK.AUTHOR)
      .ORDER_BY(BOOK.ID.ASC())
    val authorJoin = """LEFT JOIN "main"."author" ON "book"."author" = "author"."id""""

    assertThat(listOf(publishers, adas, placed, throughUserJoin).map { it.compile().sql }).isEqualTo(
      listOf(
        """SELECT "book"."title", "publisher"."name" FROM "main"."book" $authorJoin """ +
            """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id" """ +
            """ORDER BY "book"."id" ASC""",
        """SELECT "author"."name" FROM "main"."book" $authorJoin WHERE "author"."name" = ?""",
        """SELECT "book"."title" FROM "main"."book" $authorJoin INNER JOIN "main"."person" """ +
            """ON "person"."name" = "author"."name" ORDER BY "book"."id" ASC""",
        """SELECT "publisher"."name" FROM "main"."book" INNER JOIN "main"."author" AS "a" """ +
            """ON "a"."id" = "book"."author" """ +
            """LEFT JOIN "main"."publisher" ON "a"."publisher" = "publisher"."id" """ +
            """ORDER BY "book"."id" ASC"""
      )
    )
    assertThat(publishers.observedTables()).isEqualTo(listOf("book", "author", "publisher"))
    assertThat(publishers.rowsIn(::libraryDatabase)).isEqualTo(
      listOf(
        Row2("Dune", "Tor"),
        Row2("Emma", null),
        Row2("Kernel", null)
      )
    )
    assertThat(adas.rowsIn(::libraryDatabase)).isEqualTo(listOf("Ada"))
    assertThat(placed.rowsIn(::libraryDatabase)).isEqualTo(listOf("Dune", "Emma", "Kernel"))
    assertThat(throughUserJoin.rowsIn(::libraryDatabase)).isEqualTo(listOf("Tor", null, null))
    assertThat(
      (SELECT COLUMN PUBLISHER.ID.nullable() FROM AUTHOR ORDER_BY AUTHOR.ID.ASC()).rowsIn(::libraryDatabase)
    ).isEqualTo(listOf(1L, null, 2L))
  }

  @Test
  fun `ambiguous or missing relationship paths reject before SQL`() {
    assertThat(
      listOf(
        SELECT COLUMN BOOK.TITLE FROM SERIES,
        SELECT COLUMN AUTHOR.NAME FROM SERIES,
        SELECT COLUMN PERSON.NAME FROM BOOK,
        SELECT COLUMN (AUTHOR AS "a").NAME FROM BOOK,
        SELECT COLUMN PUBLISHER.ID FROM AUTHOR
      ).map { query ->
        assertThrows(IllegalArgumentException::class.java, query::compile).message
      }
    ).isEqualTo(
      listOf(
        "Several relationships lead to book: series.first_book, series.last_book; join the one you mean",
        "Several relationships lead to author: series.first_book.author, series.last_book.author; " +
            "join the one you mean",
        "A field requires the exact table occurrence of its FROM, UPDATE or DELETE",
        "A field requires the exact table occurrence of its FROM, UPDATE or DELETE",
        "publisher.id is NOT NULL but an outer join can leave it NULL; select the field with .nullable()"
      )
    )
  }

  @Test
  fun `exact user joins supply relationships and other joins stay beside`() {
    val adas = SELECT FROM BOOK INNER_JOIN AUTHOR ON (BOOK.AUTHOR IS AUTHOR.ID) WHERE (AUTHOR.NAME IS "Ada")
    val aliased = AUTHOR AS "a"
    val reversed = SELECT.FROM(BOOK)
      .LEFT_JOIN(aliased)
      .ON(aliased.ID IS BOOK.AUTHOR)
      .ORDER_BY(BOOK.ID.ASC())
    val filtered = SELECT.FROM(BOOK)
      .LEFT_JOIN(AUTHOR)
      .ON((BOOK.AUTHOR IS AUTHOR.ID) AND (AUTHOR.NAME IS "Ada"))
      .ORDER_BY(BOOK.ID.ASC())

    assertThat(listOf(adas, reversed, filtered).map { it.compile().sql }).isEqualTo(
      listOf(
        """SELECT $bookFields, ${authorFields("author")} FROM "main"."book" INNER JOIN "main"."author" """ +
            """ON "book"."author" = "author"."id" WHERE "author"."name" = ?""",
        """SELECT $bookFields, ${authorFields("a")} FROM "main"."book" LEFT JOIN "main"."author" AS "a" """ +
            """ON "a"."id" = "book"."author" ORDER BY "book"."id" ASC""",
        """SELECT $bookFields, ${authorFields("author_2")} FROM "main"."book" LEFT JOIN "main"."author" """ +
            """ON ("book"."author" = "author"."id") AND ("author"."name" = ?) """ +
            """LEFT JOIN "main"."author" AS "author_2" ON "book"."author" = "author_2"."id" """ +
            """ORDER BY "book"."id" ASC"""
      )
    )
    assertThat(adas.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true).take(1))
    assertThat(reversed.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true))
    assertThat(filtered.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = true))
  }

  @Test
  fun `field joins and deep reads share relationship occurrences`() {
    val tors = (SELECT FROM BOOK WHERE (PUBLISHER.NAME IS "Tor")).queryDeep()

    assertThat(tors.compile().sql).isEqualTo(
      """SELECT $bookFields, ${authorFields("author")}, ${publisherFields("publisher")} FROM "main"."book" """ +
          """LEFT JOIN "main"."author" ON "book"."author" = "author"."id" """ +
          """LEFT JOIN "main"."publisher" ON "author"."publisher" = "publisher"."id" """ +
          """WHERE "publisher"."name" = ?"""
    )
    assertThat(tors.rowsIn(::libraryDatabase)).isEqualTo(books(shallow = false).take(1))
  }
}
