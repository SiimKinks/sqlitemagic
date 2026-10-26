package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class DerivedSourceContractTest {
  @Test
  fun `derived sources export typed values and read the inner complete result`() {
    PlainKotlinCompilation
      .compile(
        fileName = "DerivedSourceConsumer.kt",
        contents = """
          package downstream.derived

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.DerivedField
          import com.siimkinks.sqlitemagic.DerivedSource
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.NullableEncodableExpression
          import com.siimkinks.sqlitemagic.QuerySelection
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.Row3
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SelectFromStage
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.plus
          import typewitness.Exact
          import typewitness.exactType

          class Author
          class Book
          data class AuthorId(val value: Long)

          fun verify(
            author: Table<Author>,
            book: Table<Book>,
            authorId: Column<Author, Long, Required, Long, Long, Required, Long>,
            authorName: Column<Author, String, Required, String, String, Required, String>,
            bookAuthor: Column<Book, Long, Required, Long, Long, Required, Long>,
            bookTitle: Column<Book, String, Required, String, String, Required, String>
          ) {
            val nextId = (authorId + 1L) AS "next_id"
            val rows = SELECT
                .COLUMNS(authorId, authorName, nextId)
                .FROM(author)
            val ids = SELECT
                .COLUMN(authorId)
                .FROM(author)
                .mapTo(::AuthorId)
            val published = rows AS "published"
            val defaultRows = SELECT FROM published
            val defaultIds = SELECT FROM ids
            val distinctIds = SELECT.DISTINCT FROM ids
            val exportedId = published[authorId]
            val exportedName = published[authorName]
            val exportedNext = published[nextId]
            val unaliasedId = ids[authorId]
            val compared = exportedName IS "Ada"
            val within = exportedName IN listOf("Ada", "Grace")
            val greater = exportedId GREATER_THAN 1L
            val lifted = exportedName.nullable()
            val liftedNext = exportedNext.nullable()
            val nextCompared = exportedNext GREATER_THAN 1L
            val joined = SELECT COLUMN bookTitle FROM book INNER_JOIN published ON (bookAuthor IS published[authorId])
            val unaliasedJoin = SELECT COLUMN bookTitle FROM book INNER_JOIN ids ON (bookAuthor IS ids[authorId])
            val leftJoined = SELECT
                .COLUMNS(bookTitle, published[authorName].nullable())
                .FROM(book)
                .LEFT_JOIN(published)
                .ON(bookAuthor IS published[authorId])
            val projected = SELECT
                .COLUMN(published[nextId])
                .FROM(published)
                .WHERE(published[authorId] GREATER_THAN 1L)
            val renamed = published AS "other"

            val publishedType = published.exactType()
            val exactPublished: Exact<DerivedSource<Row3<Long, String, Number>>> = publishedType
            val defaultRowsType = defaultRows.exactType()
            val exactDefaultRows:
                Exact<SelectFromStage<Row3<Long, String, Number>, QuerySelection<Row3<Long, String, Number>>>> =
                defaultRowsType
            val defaultIdsType = defaultIds.exactType()
            val exactDefaultIds: Exact<SelectFromStage<AuthorId, QuerySelection<AuthorId>>> = defaultIdsType
            val distinctIdsType = distinctIds.exactType()
            val exactDistinctIds: Exact<SelectFromStage<AuthorId, QuerySelection<AuthorId>>> = distinctIdsType
            val idType = exportedId.exactType()
            val exactId: Exact<DerivedField<Long, Long, Required, Long>> = idType
            val nameType = exportedName.exactType()
            val exactName: Exact<DerivedField<String, String, Required, String>> = nameType
            val nextType = exportedNext.exactType()
            val exactNext: Exact<DerivedField<Number, Number, Required, Number>> = nextType
            val unaliasedIdType = unaliasedId.exactType()
            val exactUnaliasedId: Exact<DerivedField<Long, Long, Required, Long>> = unaliasedIdType
            val comparedType = compared.exactType()
            val exactCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = comparedType
            val withinType = within.exactType()
            val exactWithin: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = withinType
            val greaterType = greater.exactType()
            val exactGreater: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = greaterType
            val liftedType = lifted.exactType()
            val exactLifted: Exact<NullableEncodableExpression<String, String, String>> = liftedType
            val liftedNextType = liftedNext.exactType()
            val exactLiftedNext: Exact<NullableEncodableExpression<Number, Number, Number>> = liftedNextType
            val nextComparedType = nextCompared.exactType()
            val exactNextCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nextComparedType
            val renamedType = renamed.exactType()
            val exactRenamed: Exact<DerivedSource<Row3<Long, String, Number>>> = renamedType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      """UPDATE of a derived source | val invalid = UPDATE TABLE published SET (authorName to "Ada") | """ +
          "actual type is 'DerivedSource<Row3<Long, String, Number>>', but 'Table<uninferred P",
      "DELETE of a derived source | val invalid = DELETE FROM published | " +
          "actual type is 'DerivedSource<Row3<Long, String, Number>>', but 'Table<uninferred P",
      """export as a SET target | val invalid = UPDATE TABLE author SET (published[authorName] to "Ada") | """ +
          "None of the following candidates is applicable",
      """export parameter | val invalid = published[authorId].asParameter("id") | """ +
          "Unresolved reference 'asParameter' on receiver of type 'DerivedField",
      """read-only export with a host value | val one = "1".expr(SqlType.NUMBER) AS "one"; """ +
          """val ones = (SELECT COLUMN one FROM author) AS "ones"; val invalid = ones[one] GREATER_THAN 1L | """ +
          "actual type is 'Long', but 'SqlExpression<Number?, *, *>' was expected",
      "table mapping onto a derived source | val invalid = rows.mapTo(published) | " +
          "None of the following candidates is applicable",
      "deep read of a derived source | val invalid = (SELECT FROM published).queryDeep() | " +
          "is inapplicable because of a receiver type mismatch"
    ]
  )
  fun `derived sources outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedDerivedSource.kt",
        contents = """
          package downstream.derived

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.DELETE
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SET
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPDATE
          import com.siimkinks.sqlitemagic.expr
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.plus
          import com.siimkinks.sqlitemagic.queryDeep

          class Author

          fun verify(
            author: Table<Author>,
            authorId: Column<Author, Long, Required, Long, Long, Required, Long>,
            authorName: Column<Author, String, Required, String, String, Required, String>
          ) {
            val nextId = (authorId + 1L) AS "next_id"
            val rows = SELECT
                .COLUMNS(authorId, authorName, nextId)
                .FROM(author)
            val published = rows AS "published"
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
