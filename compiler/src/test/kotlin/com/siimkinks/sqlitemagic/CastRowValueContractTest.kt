package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class CastRowValueContractTest {
  @Test
  fun `casts read their target type and row values compare in order`() {
    PlainKotlinCompilation
      .compile(
        fileName = "CastRowValueConsumer.kt",
        contents = """
          package downstream.cast

          import com.siimkinks.sqlitemagic.BLOB
          import com.siimkinks.sqlitemagic.CAST
          import com.siimkinks.sqlitemagic.COALESCE
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.INTEGER
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
          import com.siimkinks.sqlitemagic.NUMERIC
          import com.siimkinks.sqlitemagic.REAL
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.TEXT
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.asValue
          import com.siimkinks.sqlitemagic.plus
          import com.siimkinks.sqlitemagic.row
          import typewitness.Exact
          import typewitness.exactType

          class Book

          fun verify(
            book: Table<Book>,
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>,
            pages: Column<Book, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>,
            price: Column<Book, Double, Required, Double, Double, Required, Double>
          ) {
            val idText = CAST(id AS TEXT)
            val subtitleNumber = CAST(subtitle AS INTEGER)
            val pagesReal = CAST(pages AS REAL)
            val titleBytes = CAST(title AS BLOB)
            val titleNumeric = CAST(title AS NUMERIC)
            val computed = CAST(price + 1.0 AS TEXT)
            val compared = CAST(subtitle AS INTEGER) GREATER_THAN 5L
            val equal = idText IS "42"
            val within = idText IN listOf("1", "2")
            val numeric = titleNumeric GREATER_THAN 2.5
            val arithmetic = CAST(title AS INTEGER) + 1
            val fallback = COALESCE(subtitleNumber, 0L.asValue())
            val keyset = row(title, id) GREATER_THAN row("Kotlin", 42L)
            val wide = row(title, id, price) LESS_OR_EQUAL row(subtitle, 1L.asValue(), 2.5)
            val selected = SELECT
                .COLUMN(idText)
                .FROM(book)
                .WHERE(row(title, id) GREATER_THAN row("Kotlin", 42L))

            val idTextType = idText.exactType()
            val exactIdText: Exact<EncodableSqlExpression<String, String, Required, String>> = idTextType
            val subtitleNumberType = subtitleNumber.exactType()
            val exactSubtitleNumber: Exact<EncodableSqlExpression<Long, Long, SqlNullable, Long?>> =
                subtitleNumberType
            val pagesRealType = pagesReal.exactType()
            val exactPagesReal: Exact<EncodableSqlExpression<Double, Double, SqlNullable, Double?>> =
                pagesRealType
            val titleBytesType = titleBytes.exactType()
            val exactTitleBytes: Exact<EncodableSqlExpression<ByteArray, ByteArray, Required, ByteArray>> =
                titleBytesType
            val titleNumericType = titleNumeric.exactType()
            val exactTitleNumeric: Exact<EncodableSqlExpression<Number, Number, Required, Number>> =
                titleNumericType
            val computedType = computed.exactType()
            val exactComputed: Exact<EncodableSqlExpression<String, String, Required, String>> = computedType
            val comparedType = compared.exactType()
            val exactCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = comparedType
            val equalType = equal.exactType()
            val exactEqual: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = equalType
            val withinType = within.exactType()
            val exactWithin: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = withinType
            val numericType = numeric.exactType()
            val exactNumeric: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = numericType
            val fallbackType = fallback.exactType()
            val exactFallback: Exact<SqlExpression<Long, Required, Long>> = fallbackType
            val keysetType = keyset.exactType()
            val exactKeyset: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = keysetType
            val wideType = wide.exactType()
            val exactWide: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = wideType
            val arithmeticType = arithmetic.exactType()
            val exactArithmetic: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = arithmeticType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "CAST without a type | val invalid = CAST(id) | but 'CastTarget<Required, uninferred C",
      """CAST to an output alias | val invalid = CAST(id AS "TEXT") | but 'CastTarget<Required, uninferred C""",
      "CAST host value of another type | val invalid = CAST(id AS TEXT) IS 42L | None of the following candidates",
      "single-element row | val invalid = row(id) | No value passed for parameter 'second'",
      """row equality | val invalid = row(title, id) IS row("Kotlin", 42L) | """ +
          "inapplicable because of a receiver type mismatch",
      """row membership | val invalid = row(title, id) IN listOf(row("Kotlin", 42L)) | """ +
          "None of the following candidates is applicable",
      "row as a selected value | val invalid = SELECT.COLUMN(row(title, id)) | Argument type mismatch",
      "row with a host operand | val invalid = row(title, id) GREATER_THAN 42L | Argument type mismatch"
    ]
  )
  fun `casts and row values outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedCastRowValue.kt",
        contents = """
          package downstream.cast

          import com.siimkinks.sqlitemagic.CAST
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.TEXT
          import com.siimkinks.sqlitemagic.row

          class Book

          fun verify(
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>
          ) {
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
