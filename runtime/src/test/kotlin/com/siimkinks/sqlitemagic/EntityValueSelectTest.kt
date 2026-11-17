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
  private val shallowAuthors = books(shallow = true).map(Book::author)
  private val shallowAda = shallowAuthors[0]
  private val shallowLinus = shallowAuthors[2]
  private val authorColumns = """"author"."id", "author"."name", "author"."publisher""""
  private val publisherJoin = """INNER JOIN "main"."publisher" ON "publisher"."id" = "author"."publisher""""

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
  fun `selected rows read entities by their table and fields selected themselves`() {
    data class Read(
      val owned: List<Pair<Author, String?>>,
      val live: List<Pair<Author, String?>>,
      val entityField: String?,
      val unselectedEntity: String?
    )

    val query = SELECT
      .COLUMNS(AUTHOR, PUBLISHER.all())
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
      .ORDER_BY(AUTHOR.ID.ASC())

    val read = libraryDatabase().use { database ->
      val rows = query.rows(database)
      Read(
        owned = rows.map { row -> row[AUTHOR] to row[PUBLISHER.NAME] },
        live = query
          .mapTo { row -> row[AUTHOR] to row[PUBLISHER.NAME] }
          .rows(database),
        entityField = assertThrows(IllegalArgumentException::class.java) { rows.first()[AUTHOR.NAME] }.message,
        unselectedEntity = assertThrows(IllegalArgumentException::class.java) { rows.first()[BOOK] }.message
      )
    }

    val authors = listOf(shallowAda to "Tor", shallowLinus to null)
    assertThat(read).isEqualTo(
      Read(
        owned = authors,
        live = authors,
        entityField = """"author"."name" is only part of the selected author entity; select it to read it""",
        unselectedEntity = "The book entity was not selected"
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
  fun `entity values outside their contracts reject before SQL`() {
    val joinedPublishers = SELECT
      .COLUMNS(AUTHOR, PUBLISHER.NAME)
      .FROM(AUTHOR)
      .INNER_JOIN(PUBLISHER)
      .ON(PUBLISHER.ID IS AUTHOR.PUBLISHER)
    val failures = listOf(
      {
        SELECT
          .COLUMNS(BOOK, BOOK.TITLE)
          .FROM(BOOK)
          .compile()
      },
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
          .COLUMNS(BOOK.TITLE, PUBLISHER)
          .FROM(BOOK)
          .compile()
      },
      {
        SELECT
          .COLUMNS(BOOK.TITLE, AUTHOR AS "writer")
          .FROM(BOOK)
          .compile()
      },
      { joinedPublishers.mapTo(AUTHOR) },
      { joinedPublishers UNION joinedPublishers }
    ).map { failure -> assertThrows(IllegalArgumentException::class.java) { failure() }.message }

    assertThat(failures).isEqualTo(
      listOf(
        "The book entity reads book.author through a join, which a selected entity value cannot plan yet; select it " +
            "with SELECT FROM book",
        "An outer join can leave the publisher entity NULL; select its fields with .nullable()",
        "The publisher entity is not a source of this query; name it in FROM or a join",
        "The writer entity is not a source of this query; name it in FROM or a join",
        "mapTo(author) reads plain fields, but selected value 1 is an entity",
        "UNION and derived exports take plain values, but this query selects the author entity"
      )
    )
  }
}
