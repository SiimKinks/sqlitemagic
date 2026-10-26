package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class CteContractTest {
  @Test
  fun `leading WITH hands its lambda a typed handle per input and returns the main query`() {
    PlainKotlinCompilation
      .compile(
        fileName = "CteConsumer.kt",
        contents = """
          package downstream.cte

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.Cte
          import com.siimkinks.sqlitemagic.IN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.QuerySelection
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.Row2
          import com.siimkinks.sqlitemagic.RowSelection
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.ScalarSelection
          import com.siimkinks.sqlitemagic.SelectQuery
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UNION
          import com.siimkinks.sqlitemagic.WITH
          import com.siimkinks.sqlitemagic.mapTo
          import typewitness.Exact
          import typewitness.exactType

          class Author
          class Book

          fun verify(
            author: Table<Author>,
            book: Table<Book>,
            authorId: Column<Author, Long, Required, Long, Long, Required, Long>,
            authorName: Column<Author, String, Required, String, String, Required, String>,
            bookAuthor: Column<Book, Long, Required, Long, Long, Required, Long>,
            bookTitle: Column<Book, String, Required, String, String, Required, String>
          ) {
            val one = WITH(SELECT.COLUMN(authorId).FROM(author)) { ids ->
              SELECT
                  .COLUMN(ids[authorId])
                  .FROM(ids)
            }
            val implicit = WITH(SELECT.COLUMN(authorId).FROM(author)) {
              SELECT FROM it
            }
            val named = WITH(SELECT.COLUMN(authorName).FROM(author) AS "names") { names ->
              SELECT FROM names
            }
            val two = WITH(
              SELECT.COLUMN(authorId).FROM(author),
              SELECT.COLUMNS(bookAuthor, bookTitle).FROM(book)
            ) { ids, titles ->
              SELECT
                  .COLUMN(titles[bookTitle])
                  .FROM(titles)
                  .WHERE(titles[bookAuthor] IN (SELECT.COLUMN(ids[authorId]).FROM(ids)))
            }
            val three = WITH(
              SELECT.COLUMN(authorId).FROM(author),
              SELECT.COLUMN(authorName).FROM(author),
              SELECT.COLUMNS(bookAuthor, bookTitle).FROM(book)
            ) { _, _, titles ->
              SELECT FROM titles
            }
            val ids = SELECT.COLUMN(authorId).FROM(author).asCte()
            val names = (SELECT.COLUMN(authorName).FROM(author) AS "names").asCte()
            val titles = SELECT.COLUMN(bookTitle).FROM(book).asCte()
            val authors = SELECT.COLUMN(bookAuthor).FROM(book).asCte()
            val handles = WITH(ids, names, titles, authors) {
              SELECT
                  .COLUMN(ids[authorId])
                  .FROM(ids)
            }
            val twoHandles = WITH(ids, names) {
              SELECT FROM names
            }
            val reused = WITH(ids) { handle ->
              SELECT FROM handle
            }
            val compound = WITH(SELECT.COLUMN(authorId).FROM(author)) { idsCte ->
              SELECT.COLUMN(idsCte[authorId]).FROM(idsCte) UNION SELECT.COLUMN(bookAuthor).FROM(book)
            }
            val selfJoin = WITH(SELECT.COLUMN(authorId).FROM(author)) { idsCte ->
              val other = idsCte AS "other"
              SELECT
                  .COLUMN(idsCte[authorId])
                  .FROM(idsCte)
                  .INNER_JOIN(other)
                  .ON(other[authorId] IS idsCte[authorId])
            }
            val mapped = one.mapTo { it + 1 }

            val oneType = one.exactType()
            val exactOne: Exact<SelectQuery<Long, ScalarSelection<Long, Required, Long>>> = oneType
            val implicitType = implicit.exactType()
            val exactImplicit: Exact<SelectQuery<Long, QuerySelection<Long>>> = implicitType
            val namedType = named.exactType()
            val exactNamed: Exact<SelectQuery<String, QuerySelection<String>>> = namedType
            val twoType = two.exactType()
            val exactTwo: Exact<SelectQuery<String, ScalarSelection<String, Required, String>>> = twoType
            val threeType = three.exactType()
            val exactThree: Exact<SelectQuery<Row2<Long, String>, QuerySelection<Row2<Long, String>>>> = threeType
            val handleType = ids.exactType()
            val exactHandle: Exact<Cte<Long>> = handleType
            val namesType = names.exactType()
            val exactNames: Exact<Cte<String>> = namesType
            val handlesType = handles.exactType()
            val exactHandles: Exact<SelectQuery<Long, ScalarSelection<Long, Required, Long>>> = handlesType
            val reusedType = reused.exactType()
            val exactReused: Exact<SelectQuery<Long, QuerySelection<Long>>> = reusedType
            val twoHandlesType = twoHandles.exactType()
            val exactTwoHandles: Exact<SelectQuery<String, QuerySelection<String>>> = twoHandlesType
            val rowQuery = WITH(SELECT.COLUMNS(bookAuthor, bookTitle).FROM(book)) { rows ->
              SELECT
                  .COLUMNS(rows[bookAuthor], rows[bookTitle])
                  .FROM(rows)
            }
            val rowQueryType = rowQuery.exactType()
            val exactRows: Exact<SelectQuery<Row2<Long, String>, RowSelection<Row2<Long, String>>>> = rowQueryType
            val results: List<List<Any?>> = listOf(
              compound.execute(),
              selfJoin.execute(),
              mapped.execute()
            )
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "trailing WITH | val invalid = SELECT.COLUMN(authorId).FROM(author).WITH(ids) | " +
          "Unresolved reference 'WITH' on receiver of type 'SelectFromStage",
      "four inline inputs | val invalid = WITH(query, query, query, query) { SELECT FROM ids } | " +
          "actual type is 'SelectFromStage<Long, ScalarSelection<Long, Required, Long>>', but 'Cte<*>' was expected",
      "two handles for one input | val invalid = WITH(query = query) { a, b -> SELECT FROM a } | " +
          "but '(Cte<Long>) -> SelectQuery<Long, QuerySelection<Long>>' was expected",
      "a handle as a value | val invalid = SELECT.COLUMN(ids) | " +
          "actual type is 'Cte<Long>', but 'SqlExpression<uninferred R"
    ]
  )
  fun `WITH outside its contract does not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedCte.kt",
        contents = """
          package downstream.cte

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.WITH

          class Author

          fun verify(
            author: Table<Author>,
            authorId: Column<Author, Long, Required, Long, Long, Required, Long>
          ) {
            val query = SELECT.COLUMN(authorId).FROM(author)
            val ids = query.asCte()
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
