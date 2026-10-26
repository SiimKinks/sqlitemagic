package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ConditionalContractTest {
  @Test
  fun `conditionals infer their result type and SQL nullness`() {
    PlainKotlinCompilation
      .compile(
        fileName = "ConditionalConsumer.kt",
        contents = """
          package downstream.conditional

          import com.siimkinks.sqlitemagic.CASE
          import com.siimkinks.sqlitemagic.COALESCE
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.ELSE
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IFNULL
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.NULL
          import com.siimkinks.sqlitemagic.NULLIF
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullability
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.THEN
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.WHEN
          import com.siimkinks.sqlitemagic.asValue
          import com.siimkinks.sqlitemagic.mapTo
          import com.siimkinks.sqlitemagic.plus
          import typewitness.Exact
          import typewitness.exactType

          class Book
          enum class Status { DRAFT, DONE }
          data class Pages(val value: Number)

          fun verify(
            book: Table<Book>,
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>,
            pages: Column<Book, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>,
            price: Column<Book, Double, Required, Double, Double, Required, Double>,
            status: Column<Book, Status, Required, Status, Status, Required, Status>,
            erased: SqlExpression<String, SqlNullability, String?>
          ) {
            val requiredFirst = COALESCE(title, subtitle)
            val requiredSecond = COALESCE(subtitle, title, subtitle)
            val requiredNinth = COALESCE(
              subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, title
            )
            val requiredTenth = COALESCE(
              subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, subtitle, title
            )
            val erasedPrefix = COALESCE(erased, title)
            val pairNullable = COALESCE(subtitle, subtitle)
            val allNullable = COALESCE(subtitle, subtitle, subtitle)
            val hostFallback = COALESCE(subtitle, "Untitled".asValue())
            val nullAlternative = COALESCE(subtitle, NULL)
            val mixed = COALESCE(pages, price)
            val widened: SqlExpression<Number, Required, Number> = mixed
            val pagesPlusOne = COALESCE(pages + 1, 0.asValue())
            val ifNullRequired = IFNULL(subtitle, "Untitled".asValue())
            val ifNullRequiredFirst = IFNULL(title, subtitle)
            val ifNullNullable = IFNULL(subtitle, subtitle)
            val nullIfHost = NULLIF(title, "")
            val nullIfExpression = NULLIF(title, subtitle)
            val nullIfNumber = NULLIF(pages + 1, 0)
            val nullIfCompared = NULLIF(title, "") IS "x"
            val nullIfReadOnly = NULLIF(COALESCE(subtitle, title), subtitle)
            val searched = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN("high".asValue())
                .WHEN(price GREATER_THAN 5.0)
                .THEN("mid".asValue())
                .ELSE("low".asValue())
                .END()
            val searchedInfix = (CASE WHEN (price GREATER_THAN 10.0) THEN "high".asValue() ELSE "low".asValue()).END()
            val withoutElse = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN("high".asValue())
                .END()
            val nullableBranch = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN(subtitle)
                .ELSE("low".asValue())
                .END()
            val nullBranch = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN(NULL)
                .ELSE(title)
                .END()
            val mixedBranches = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN(price)
                .ELSE(1.asValue())
                .END()
            val simple = CASE(status)
                .WHEN(Status.DRAFT)
                .THEN("Draft".asValue())
                .WHEN(Status.DONE)
                .THEN("Done".asValue())
                .ELSE("Other".asValue())
                .END()
            val simpleInfix = (CASE(status) WHEN Status.DRAFT THEN "Draft".asValue()).END()
            val simpleExpression = CASE(title)
                .WHEN(subtitle)
                .THEN(1L.asValue())
                .ELSE(0L.asValue())
                .END()
            val simpleNumeric = CASE(price)
                .WHEN(pages)
                .THEN("same".asValue())
                .WHEN(1L.asValue())
                .THEN("one".asValue())
                .END()
            val nested = CASE
                .WHEN(COALESCE(subtitle, title) IS title)
                .THEN(COALESCE(subtitle, "none".asValue()))
                .ELSE(NULLIF(title, "x"))
                .END()
            val selected = SELECT
                .COLUMN(COALESCE(subtitle, title) AS "display")
                .FROM(book)
            val mapped = SELECT
                .COLUMN(mixed)
                .FROM(book)
                .mapTo(::Pages)
            val filtered = SELECT
                .COLUMN(title)
                .FROM(book)
                .WHERE(searched IS "high".asValue())

            val requiredFirstType = requiredFirst.exactType()
            val exactRequiredFirst: Exact<SqlExpression<String, Required, String>> = requiredFirstType
            val requiredSecondType = requiredSecond.exactType()
            val exactRequiredSecond: Exact<SqlExpression<String, Required, String>> = requiredSecondType
            val requiredNinthType = requiredNinth.exactType()
            val exactRequiredNinth: Exact<SqlExpression<String, Required, String>> = requiredNinthType
            val requiredTenthType = requiredTenth.exactType()
            val exactRequiredTenth: Exact<SqlExpression<String, SqlNullable, String?>> = requiredTenthType
            val erasedPrefixType = erasedPrefix.exactType()
            val exactErasedPrefix: Exact<SqlExpression<String, SqlNullable, String?>> = erasedPrefixType
            val pairNullableType = pairNullable.exactType()
            val exactPairNullable: Exact<SqlExpression<String, SqlNullable, String?>> = pairNullableType
            val allNullableType = allNullable.exactType()
            val exactAllNullable: Exact<SqlExpression<String, SqlNullable, String?>> = allNullableType
            val hostFallbackType = hostFallback.exactType()
            val exactHostFallback: Exact<SqlExpression<String, Required, String>> = hostFallbackType
            val nullAlternativeType = nullAlternative.exactType()
            val exactNullAlternative: Exact<SqlExpression<String?, SqlNullable, String?>> = nullAlternativeType
            val pagesPlusOneType = pagesPlusOne.exactType()
            val exactPagesPlusOne: Exact<SqlExpression<Number, Required, Number>> = pagesPlusOneType
            val ifNullRequiredType = ifNullRequired.exactType()
            val exactIfNullRequired: Exact<SqlExpression<String, Required, String>> = ifNullRequiredType
            val ifNullRequiredFirstType = ifNullRequiredFirst.exactType()
            val exactIfNullRequiredFirst: Exact<SqlExpression<String, Required, String>> =
                ifNullRequiredFirstType
            val ifNullNullableType = ifNullNullable.exactType()
            val exactIfNullNullable: Exact<SqlExpression<String, SqlNullable, String?>> = ifNullNullableType
            val nullIfHostType = nullIfHost.exactType()
            val exactNullIfHost: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> = nullIfHostType
            val nullIfExpressionType = nullIfExpression.exactType()
            val exactNullIfExpression: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> =
                nullIfExpressionType
            val nullIfNumberType = nullIfNumber.exactType()
            val exactNullIfNumber: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> =
                nullIfNumberType
            val nullIfComparedType = nullIfCompared.exactType()
            val exactNullIfCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nullIfComparedType
            val nullIfReadOnlyType = nullIfReadOnly.exactType()
            val exactNullIfReadOnly: Exact<SqlExpression<String, SqlNullable, String?>> = nullIfReadOnlyType
            val searchedType = searched.exactType()
            val exactSearched: Exact<SqlExpression<String, Required, String>> = searchedType
            val searchedInfixType = searchedInfix.exactType()
            val exactSearchedInfix: Exact<SqlExpression<String, Required, String>> = searchedInfixType
            val withoutElseType = withoutElse.exactType()
            val exactWithoutElse: Exact<SqlExpression<String, SqlNullable, String?>> = withoutElseType
            val nullableBranchType = nullableBranch.exactType()
            val exactNullableBranch: Exact<SqlExpression<String, SqlNullable, String?>> = nullableBranchType
            val nullBranchType = nullBranch.exactType()
            val exactNullBranch: Exact<SqlExpression<String?, SqlNullable, String?>> = nullBranchType
            val simpleType = simple.exactType()
            val exactSimple: Exact<SqlExpression<String, Required, String>> = simpleType
            val simpleInfixType = simpleInfix.exactType()
            val exactSimpleInfix: Exact<SqlExpression<String, SqlNullable, String?>> = simpleInfixType
            val simpleExpressionType = simpleExpression.exactType()
            val exactSimpleExpression: Exact<SqlExpression<Long, Required, Long>> = simpleExpressionType
            val simpleNumericType = simpleNumeric.exactType()
            val exactSimpleNumeric: Exact<SqlExpression<String, SqlNullable, String?>> = simpleNumericType
            val nestedType = nested.exactType()
            val exactNested: Exact<SqlExpression<String, SqlNullable, String?>> = nestedType
            val widenedBranches: SqlExpression<Number, Required, Number> = mixedBranches
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "single COALESCE alternative | val invalid = COALESCE(subtitle) | No value passed for parameter 'second'",
      """host COALESCE alternative | val invalid = COALESCE(subtitle, "Untitled") | None of the following candidates""",
      """host IFNULL alternative | val invalid = IFNULL(subtitle, "Untitled") | None of the following candidates""",
      "NULLIF host of another type | val invalid = NULLIF(title, 5L) | None of the following candidates",
      """host THEN | val invalid = CASE.WHEN(price GREATER_THAN 1.0).THEN("x") | """ +
          "Argument type mismatch: actual type is 'String'",
      """host ELSE | val invalid = CASE.WHEN(price GREATER_THAN 1.0).THEN(title).ELSE("x") | """ +
          "Argument type mismatch: actual type is 'String'",
      """simple CASE host of another type | val invalid = CASE(status).WHEN("draft") | """ +
          "None of the following candidates",
      "simple CASE match of another type | val invalid = CASE(title).WHEN(price) | None of the following candidates",
      "searched WHEN without a predicate | val invalid = CASE.WHEN(title) | Argument type mismatch",
      "END without THEN | val invalid = CASE.WHEN(price GREATER_THAN 1.0).END() | Unresolved reference 'END'",
      "unfinished CASE as a value | val invalid: SqlExpression<String, *, *> = " +
          "CASE.WHEN(price GREATER_THAN 1.0).THEN(title) | actual 'SearchedCaseBranches<String, Required>'",
      """host comparison with a result | val invalid = COALESCE(subtitle, title) IS "x" | """ +
          "Argument type mismatch: actual type is 'String', but 'SqlExpression<String?, *, *>' was expected",
      """parameter from a result | val invalid = COALESCE(subtitle, title).asParameter("x") | """ +
          "Unresolved reference 'asParameter'"
    ]
  )
  fun `conditionals outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedConditional.kt",
        contents = """
          package downstream.conditional

          import com.siimkinks.sqlitemagic.CASE
          import com.siimkinks.sqlitemagic.COALESCE
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.ELSE
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IFNULL
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.NULLIF
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.THEN
          import com.siimkinks.sqlitemagic.WHEN

          class Book
          enum class Status { DRAFT, DONE }

          fun verify(
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>,
            price: Column<Book, Double, Required, Double, Double, Required, Double>,
            status: Column<Book, Status, Required, Status, Status, Required, Status>
          ) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
