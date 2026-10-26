package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class WindowContractTest {
  @Test
  fun `windows and filters keep their result types`() {
    PlainKotlinCompilation
      .compile(
        fileName = "WindowConsumer.kt",
        contents = """
          package downstream.window

          import com.siimkinks.sqlitemagic.COUNT
          import com.siimkinks.sqlitemagic.CURRENT_ROW
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.DENSE_RANK
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.FOLLOWING
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.MAX
          import com.siimkinks.sqlitemagic.ORDER_BY
          import com.siimkinks.sqlitemagic.PARTITION_BY
          import com.siimkinks.sqlitemagic.PRECEDING
          import com.siimkinks.sqlitemagic.RANK
          import com.siimkinks.sqlitemagic.ROWS_BETWEEN
          import com.siimkinks.sqlitemagic.ROW_NUMBER
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SUM
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UNBOUNDED_FOLLOWING
          import com.siimkinks.sqlitemagic.UNBOUNDED_PRECEDING
          import com.siimkinks.sqlitemagic.WindowSpec
          import com.siimkinks.sqlitemagic.WindowableSqlExpression
          import typewitness.Exact
          import typewitness.exactType

          class Book

          fun verify(
            book: Table<Book>,
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            author: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>,
            pages: Column<Book, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>
          ) {
            val perAuthor = PARTITION_BY(author).ORDER_BY(id.ASC())
            val running = ORDER_BY(id.ASC()).ROWS_BETWEEN(2.PRECEDING).AND(CURRENT_ROW)
            val whole = PARTITION_BY(author, title).ROWS_BETWEEN(UNBOUNDED_PRECEDING).AND(UNBOUNDED_FOLLOWING)
            val ahead = ROWS_BETWEEN(CURRENT_ROW).AND(3L.FOLLOWING)
            val ordered = ORDER_BY(author.ASC(), id.DESC())
            val numbered = ROW_NUMBER() OVER perAuthor
            val ranked = RANK() OVER ordered
            val dense = DENSE_RANK().OVER()
            val runningSum = SUM(pages) OVER running
            val counted = COUNT().OVER()
            val longest = MAX(title) OVER whole
            val filtered = COUNT() FILTER_WHERE (pages GREATER_THAN 300)
            val filteredWindow = SUM(pages) FILTER_WHERE (author IS 1L) OVER ahead
            val compared = (ROW_NUMBER() OVER perAuthor) GREATER_THAN 1L
            val query = SELECT
                .COLUMNS(title, numbered)
                .FROM(book)
                .ORDER_BY(numbered.DESC())

            val exactPerAuthor: WindowSpec = perAuthor
            val numberedType = numbered.exactType()
            val exactNumbered: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = numberedType
            val rankedType = ranked.exactType()
            val exactRanked: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = rankedType
            val denseType = dense.exactType()
            val exactDense: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = denseType
            val runningSumType = runningSum.exactType()
            val exactRunningSum: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> =
                runningSumType
            val countedType = counted.exactType()
            val exactCounted: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = countedType
            val longestType = longest.exactType()
            val exactLongest: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> = longestType
            val filteredType = filtered.exactType()
            val exactFiltered: Exact<WindowableSqlExpression<Long, Long, Required, Long>> = filteredType
            val filteredWindowType = filteredWindow.exactType()
            val exactFilteredWindow: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> =
                filteredWindowType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "ranking without OVER as a value | val invalid: SqlExpression<Long, *, *> = ROW_NUMBER() | " +
          "actual 'RankingFunction'",
      "FILTER twice | val invalid = COUNT() FILTER_WHERE (id IS 1L) FILTER_WHERE (id IS 2L) | " +
          "Unresolved reference 'FILTER_WHERE' on receiver of type 'WindowableSqlExpression",
      "FILTER after OVER | val invalid = COUNT().OVER() FILTER_WHERE (id IS 1L) | " +
          "Unresolved reference 'FILTER_WHERE' on receiver of type 'EncodableSqlExpression",
      "OVER twice | val invalid = COUNT().OVER().OVER() | " +
          "Unresolved reference 'OVER' on receiver of type 'EncodableSqlExpression",
      "FILTER on a ranking function | val invalid = ROW_NUMBER() FILTER_WHERE (id IS 1L) | " +
          "Unresolved reference 'FILTER_WHERE'",
      "ORDER BY before PARTITION BY | val invalid = ORDER_BY(id.ASC()).PARTITION_BY(id) | " +
          "Unresolved reference 'PARTITION_BY'",
      "unfinished frame | val invalid: WindowSpec = ROWS_BETWEEN(CURRENT_ROW) | actual 'WindowFrameStart'",
      "FILTER with a non-predicate | val invalid = COUNT() FILTER_WHERE id | Argument type mismatch"
    ]
  )
  fun `windows and filters outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedWindow.kt",
        contents = """
          package downstream.window

          import com.siimkinks.sqlitemagic.COUNT
          import com.siimkinks.sqlitemagic.CURRENT_ROW
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.ORDER_BY
          import com.siimkinks.sqlitemagic.ROWS_BETWEEN
          import com.siimkinks.sqlitemagic.ROW_NUMBER
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.WindowSpec

          class Book

          fun verify(
            id: Column<Book, Long, Required, Long, Long, Required, Long>
          ) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
