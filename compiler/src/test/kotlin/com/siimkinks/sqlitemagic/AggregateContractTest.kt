package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class AggregateContractTest {
  @Test
  fun `aggregates read their SQL result types and compare with host values`() {
    PlainKotlinCompilation
      .compile(
        fileName = "AggregateConsumer.kt",
        contents = """
          package downstream.aggregate

          import com.siimkinks.sqlitemagic.AVG
          import com.siimkinks.sqlitemagic.AggregateSqlExpression
          import com.siimkinks.sqlitemagic.CASE
          import com.siimkinks.sqlitemagic.COUNT
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.DISTINCT
          import com.siimkinks.sqlitemagic.ELSE
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.GROUP_CONCAT
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.LESS_THAN
          import com.siimkinks.sqlitemagic.MAX
          import com.siimkinks.sqlitemagic.MIN
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SUM
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.THEN
          import com.siimkinks.sqlitemagic.TOTAL
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.asScalar
          import com.siimkinks.sqlitemagic.asValue
          import com.siimkinks.sqlitemagic.plus
          import typewitness.Exact
          import typewitness.exactType

          class Book
          data class Token(val value: String)

          fun verify(
            book: Table<Book>,
            author: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>,
            pages: Column<Book, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>,
            price: Column<Book, Double, Required, Double, Double, Required, Double>,
            token: Column<Book, Token, Required, Token, Token, Required, Token>
          ) {
            val all = COUNT()
            val present = COUNT(subtitle)
            val authors = COUNT(DISTINCT(author))
            val firstTitle = MIN(title)
            val lastToken = MAX(token)
            val cheapest = MIN(DISTINCT(price))
            val label = CASE
                .WHEN(price GREATER_THAN 10.0)
                .THEN("high".asValue())
                .ELSE("low".asValue())
                .END()
            val lastLabel = MAX(label)
            val average = AVG(pages)
            val distinctAverage = AVG(DISTINCT(pages))
            val sum = SUM(pages)
            val distinctSum = SUM(DISTINCT(pages))
            val total = TOTAL(price)
            val titles = GROUP_CONCAT(title)
            val separated = GROUP_CONCAT(title, ", ")
            val distinctTitles = GROUP_CONCAT(DISTINCT(title))
            val many = COUNT() GREATER_THAN 1L
            val averageAbove = AVG(pages) GREATER_THAN 2.5
            val cheap = MIN(price) LESS_THAN 10.0
            val sumAbove = SUM(pages) GREATER_THAN 100
            val tokenIs = MAX(token) IS Token("a")
            val plusOne = COUNT() + 1L
            val grouped = SELECT
                .COLUMNS(author, COUNT())
                .FROM(book)
                .GROUP_BY(author)
                .HAVING(COUNT() GREATER_THAN 1L)
                .ORDER_BY(COUNT().DESC())
            val counted = (SELECT COLUMN COUNT() FROM book).asScalar()

            val allType = all.exactType()
            val exactAll: Exact<AggregateSqlExpression<Long, Long, Required, Long>> = allType
            val presentType = present.exactType()
            val exactPresent: Exact<AggregateSqlExpression<Long, Long, Required, Long>> = presentType
            val authorsType = authors.exactType()
            val exactAuthors: Exact<AggregateSqlExpression<Long, Long, Required, Long>> = authorsType
            val firstTitleType = firstTitle.exactType()
            val exactFirstTitle: Exact<AggregateSqlExpression<String, String, SqlNullable, String?>> =
                firstTitleType
            val lastTokenType = lastToken.exactType()
            val exactLastToken: Exact<AggregateSqlExpression<Token, Token, SqlNullable, Token?>> = lastTokenType
            val cheapestType = cheapest.exactType()
            val exactCheapest: Exact<AggregateSqlExpression<Double, Double, SqlNullable, Double?>> =
                cheapestType
            val lastLabelType = lastLabel.exactType()
            val exactLastLabel: Exact<AggregateSqlExpression<Nothing, String, SqlNullable, String?>> =
                lastLabelType
            val averageType = average.exactType()
            val exactAverage: Exact<AggregateSqlExpression<Double, Double, SqlNullable, Double?>> = averageType
            val distinctAverageType = distinctAverage.exactType()
            val exactDistinctAverage: Exact<AggregateSqlExpression<Double, Double, SqlNullable, Double?>> =
                distinctAverageType
            val sumType = sum.exactType()
            val exactSum: Exact<AggregateSqlExpression<Number, Number, SqlNullable, Number?>> = sumType
            val distinctSumType = distinctSum.exactType()
            val exactDistinctSum: Exact<AggregateSqlExpression<Number, Number, SqlNullable, Number?>> =
                distinctSumType
            val totalType = total.exactType()
            val exactTotal: Exact<AggregateSqlExpression<Double, Double, SqlNullable, Double?>> = totalType
            val titlesType = titles.exactType()
            val exactTitles: Exact<AggregateSqlExpression<String, String, SqlNullable, String?>> = titlesType
            val separatedType = separated.exactType()
            val exactSeparated: Exact<AggregateSqlExpression<String, String, SqlNullable, String?>> =
                separatedType
            val distinctTitlesType = distinctTitles.exactType()
            val exactDistinctTitles: Exact<AggregateSqlExpression<String, String, SqlNullable, String?>> =
                distinctTitlesType
            val manyType = many.exactType()
            val exactMany: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = manyType
            val countedType = counted.exactType()
            val exactCounted: Exact<SqlExpression<Long, SqlNullable, Long?>> = countedType
            val plusOneType = plusOne.exactType()
            val exactPlusOne: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = plusOneType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "DISTINCT as a selected value | val invalid = SELECT.COLUMN(DISTINCT(title)) | Argument type mismatch",
      """DISTINCT GROUP_CONCAT with a separator | val invalid = GROUP_CONCAT(DISTINCT(title), ", ") | """ +
          "actual type is 'Distinct<Column<Book, String, Required, String, String, Required, String>>'",
      "AVG of text | val invalid = AVG(title) | None of the following candidates",
      "SUM of text | val invalid = SUM(title) | None of the following candidates",
      """COUNT compared with text | val invalid = COUNT() IS "x" | None of the following candidates""",
      """parameter from an aggregate | val invalid = COUNT().asParameter("p") | Unresolved reference 'asParameter'""",
      "two-argument MIN | val invalid = MIN(title, title) | None of the following candidates"
    ]
  )
  fun `aggregates outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedAggregate.kt",
        contents = """
          package downstream.aggregate

          import com.siimkinks.sqlitemagic.AVG
          import com.siimkinks.sqlitemagic.COUNT
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.DISTINCT
          import com.siimkinks.sqlitemagic.GROUP_CONCAT
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.MIN
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SUM

          class Book

          fun verify(
            title: Column<Book, String, Required, String, String, Required, String>
          ) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
