package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SubqueryContractTest {
  @Test
  fun `scalar subqueries, query membership and EXISTS keep the physical value types`() {
    PlainKotlinCompilation
      .compile(
        fileName = "SubqueryConsumer.kt",
        contents = """
          package downstream.subquery

          import com.siimkinks.sqlitemagic.AND
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EXISTS
          import com.siimkinks.sqlitemagic.IN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.NOT_EXISTS
          import com.siimkinks.sqlitemagic.NOT_IN
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SET
          import com.siimkinks.sqlitemagic.ScalarSelection
          import com.siimkinks.sqlitemagic.SelectFromStage
          import com.siimkinks.sqlitemagic.SelectProjectionStage
          import com.siimkinks.sqlitemagic.SelectQuery
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPDATE
          import com.siimkinks.sqlitemagic.asScalar
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.readAs
          import typewitness.Exact
          import typewitness.exactType

          class Author
          class Book
          data class AuthorId(val value: Long)

          fun verify(
            author: Table<Author>,
            book: Table<Book>,
            authorId: SqlExpression<Long, Required, Long>,
            bookAuthor: SqlExpression<Long, Required, Long>,
            title: SqlExpression<String, Required, String>,
            pages: SqlExpression<Int, SqlNullable, Int?>,
            publisher: Column<Author, Long, SqlNullable, Long?, Long?, SqlNullable, Long?>
          ) {
            val ids = SELECT
                .COLUMN(authorId)
                .FROM(author)
            val mapped = ids.mapTo(::AuthorId)
            val numbers = ids.readAs(SqlType.NUMBER)
            val scalar = ids.asScalar()
            val mappedScalar = mapped.asScalar()
            val numberScalar = numbers.asScalar()
            val compared = bookAuthor IS mapped.asScalar()
            val members = bookAuthor IN mapped
            val nonMembers = bookAuthor NOT_IN ids
            val widened = pages IN ids
            val exists = EXISTS(SELECT FROM book)
            val notExists = NOT_EXISTS(SELECT COLUMN title FROM book)
            val filtered = (SELECT FROM book).WHERE(EXISTS(mapped) AND (bookAuthor IN ids))
            val selected = SELECT.COLUMN(ids.asScalar())
            val updated = UPDATE TABLE author SET (publisher to ids.asScalar())

            val idsType = ids.exactType()
            val exactIds: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = idsType
            val mappedType = mapped.exactType()
            val exactMapped: Exact<SelectQuery<AuthorId, ScalarSelection<Long, Required, AuthorId>>> =
                mappedType
            val numbersType = numbers.exactType()
            val exactNumbers: Exact<SelectQuery<Number, ScalarSelection<Long, Required, Number>>> = numbersType
            val scalarType = scalar.exactType()
            val exactScalar: Exact<SqlExpression<Long, SqlNullable, Long?>> = scalarType
            val mappedScalarType = mappedScalar.exactType()
            val exactMappedScalar: Exact<SqlExpression<Long, SqlNullable, Long?>> = mappedScalarType
            val numberScalarType = numberScalar.exactType()
            val exactNumberScalar: Exact<SqlExpression<Long, SqlNullable, Long?>> = numberScalarType
            val comparedType = compared.exactType()
            val exactCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = comparedType
            val membersType = members.exactType()
            val exactMembers: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = membersType
            val nonMembersType = nonMembers.exactType()
            val exactNonMembers: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nonMembersType
            val widenedType = widened.exactType()
            val exactWidened: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedType
            val existsType = exists.exactType()
            val exactExists: Exact<SqlExpression<Boolean, Required, Boolean>> = existsType
            val notExistsType = notExists.exactType()
            val exactNotExists: Exact<SqlExpression<Boolean, Required, Boolean>> = notExistsType
            val selectedType = selected.exactType()
            val exactSelected: Exact<SelectProjectionStage<Long?, ScalarSelection<Long, SqlNullable, Long?>>> =
                selectedType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "multi-column scalar | val invalid = SELECT.COLUMNS(authorId, title).FROM(author).asScalar() | asScalar",
      "entity scalar | val invalid = (SELECT FROM author).asScalar() | asScalar",
      "multi-column membership | val invalid = bookAuthor IN SELECT.COLUMNS(authorId, title) | " +
          "None of the following candidates is applicable",
      "incompatible membership | val invalid = title IN ids | None of the following candidates is applicable",
      "membership by the mapped type | val invalid = token IN ids.mapTo(::AuthorId) | " +
          "None of the following candidates is applicable",
      "incompatible scalar comparison | val invalid = title IS ids.asScalar() | Argument type mismatch",
      "postfix EXISTS | val invalid = ids.EXISTS() | EXISTS",
      "EXISTS of a value | val invalid = EXISTS(authorId) | Argument type mismatch",
      "required SET from a scalar subquery | val invalid = UPDATE TABLE author SET (required to ids.asScalar()) | " +
          "None of the following candidates is applicable"
    ]
  )
  fun `nested queries outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedSubquery.kt",
        contents = """
          package downstream.subquery

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EXISTS
          import com.siimkinks.sqlitemagic.IN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SET
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPDATE
          import com.siimkinks.sqlitemagic.asScalar
          import com.siimkinks.sqlitemagic.mapTo

          class Author
          data class AuthorId(val value: Long)

          fun verify(
            author: Table<Author>,
            authorId: SqlExpression<Long, Required, Long>,
            bookAuthor: SqlExpression<Long, Required, Long>,
            title: SqlExpression<String, Required, String>,
            token: SqlExpression<AuthorId, Required, AuthorId>,
            required: Column<Author, Long, Required, Long, Long, Required, Long>
          ) {
            val ids = SELECT
                .COLUMN(authorId)
                .FROM(author)
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
