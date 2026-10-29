package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import org.junit.Test

internal class NestedQueryDependencyTest {
  private fun viewDependencies(
    name: String,
    column: String,
    temporary: Boolean = false,
    query: () -> SelectQuery<*, *>
  ) = GeneratedView(
    viewName = name,
    temporary = temporary,
    query = query,
    columns = listOf(column)
  ).definition.queryDependencies

  private fun tables(vararg names: String) = DependencySnapshot(
    observed = names.asList(),
    direct = names.map { name ->
      SqliteQuerySource(
        schema = MAIN,
        name = name
      )
    }
  )

  @Test
  fun `enclosing queries depend on every table their nested queries and arms read`() {
    val writers = SELECT
      .COLUMN(AUTHOR.ID)
      .FROM(AUTHOR)
      .WHERE(AUTHOR.ID IN (SELECT COLUMN BOOK.AUTHOR FROM BOOK))
    val firstBooks = SELECT
      .COLUMN(SERIES.FIRST_BOOK)
      .FROM(SERIES)
      .UNION(writers)
    val members = firstBooks AS "members"
    val session = testTable<Any>(
      name = "author",
      temporary = true
    )
    val emmasAuthor = SELECT
      .COLUMN(BOOK.AUTHOR)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS "Emma")
      .asScalar()
    val titled = SELECT
      .COLUMNS(BOOK.AUTHOR, BOOK.TITLE)
      .FROM(BOOK)
      .WHERE(BOOK.TITLE IS_NOT "Dune") AS "titled"
    val cases = listOf(
      "two levels in WHERE" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .WHERE(PERSON.ID IN writers),
      "two levels in a selected scalar" to SELECT
        .COLUMNS(PUBLISHER.NAME, writers.asScalar())
        .FROM(PUBLISHER),
      "two levels in a later arm" to firstBooks,
      "a compound later arm" to SELECT
        .COLUMN(PERSON.ID)
        .FROM(PERSON)
        .EXCEPT(firstBooks),
      "a compound in IN" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .WHERE(PERSON.ID IN firstBooks),
      "a compound derived source" to SELECT
        .COLUMN(members[SERIES.FIRST_BOOK])
        .FROM(members),
      "a nested query in ON" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .INNER_JOIN(PUBLISHER)
        .ON(PUBLISHER.ID IN writers),
      "a nested query in HAVING" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .GROUP_BY(PERSON.NAME)
        .HAVING(EXISTS(writers)),
      "an automatic join in a nested query" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .WHERE(EXISTS(SELECT COLUMN BOOK.ID FROM BOOK WHERE (AUTHOR.NAME IS PERSON.NAME))),
      "an automatic join in a later arm" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .UNION(SELECT COLUMN BOOK.TITLE FROM BOOK WHERE (AUTHOR.NAME IS "Ada")),
      "a nested query in GROUP BY" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .GROUP_BY(emmasAuthor),
      "a nested query in ORDER BY" to SELECT
        .COLUMN(PERSON.NAME)
        .FROM(PERSON)
        .ORDER_BY(emmasAuthor.ASC()),
      "a derived source in JOIN" to SELECT
        .COLUMN(AUTHOR.NAME)
        .FROM(AUTHOR)
        .INNER_JOIN(titled)
        .ON(titled[BOOK.AUTHOR] IS AUTHOR.ID),
      "a temporary table in a later arm" to SELECT
        .COLUMN(AUTHOR.ID)
        .FROM(AUTHOR)
        .UNION(SELECT COLUMN 1L.asLiteral() FROM session)
    )
    val views = listOf(
      "a nested query in a view definition" to viewDependencies(
        name = "writing_author",
        column = "author.name"
      ) {
        (SELECT COLUMN AUTHOR.NAME FROM AUTHOR)
          .WHERE(AUTHOR.ID IN (SELECT COLUMN BOOK.AUTHOR FROM BOOK WHERE (BOOK.TITLE IS_NOT "Dune")))
      },
      "a derived source in a view definition" to viewDependencies(
        name = "titled_book",
        column = "titled.book.title"
      ) {
        SELECT
          .COLUMN(titled[BOOK.TITLE])
          .FROM(titled)
      },
      "a temporary table in a later arm of a temporary view" to viewDependencies(
        name = "session_author",
        column = "author.id",
        temporary = true
      ) {
        SELECT
          .COLUMN(AUTHOR.ID)
          .FROM(AUTHOR)
          .UNION(SELECT COLUMN 1L.asLiteral() FROM session)
      }
    )

    assertThat(cases.map { (label, query) -> label to query.observedTables() }).isEqualTo(
      listOf(
        "two levels in WHERE" to listOf("person", "author", "book"),
        "two levels in a selected scalar" to listOf("author", "book", "publisher"),
        "two levels in a later arm" to listOf("series", "author", "book"),
        "a compound later arm" to listOf("person", "series", "author", "book"),
        "a compound in IN" to listOf("person", "series", "author", "book"),
        "a compound derived source" to listOf("series", "author", "book"),
        "a nested query in ON" to listOf("person", "publisher", "author", "book"),
        "a nested query in HAVING" to listOf("person", "author", "book"),
        "an automatic join in a nested query" to listOf("person", "book", "author"),
        "an automatic join in a later arm" to listOf("person", "book", "author"),
        "a nested query in GROUP BY" to listOf("person", "book"),
        "a nested query in ORDER BY" to listOf("person", "book"),
        "a derived source in JOIN" to listOf("author", "book"),
        "a temporary table in a later arm" to listOf("author")
      )
    )
    assertThat(views.map { (label, dependencies) -> label to dependencySnapshot(dependencies) }).isEqualTo(
      listOf(
        "a nested query in a view definition" to tables("author", "book"),
        "a derived source in a view definition" to tables("book"),
        "a temporary table in a later arm of a temporary view" to DependencySnapshot(
          observed = listOf("author"),
          direct = listOf(
            SqliteQuerySource(
              schema = MAIN,
              name = "author"
            ),
            SqliteQuerySource(
              schema = TEMPORARY,
              name = "author"
            )
          )
        )
      )
    )
  }
}
