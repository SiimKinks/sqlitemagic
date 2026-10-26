package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class TextFunctionContractTest {
  @Test
  fun `text helpers read native results with their inputs nullness`() {
    PlainKotlinCompilation
      .compile(
        fileName = "TextFunctionConsumer.kt",
        contents = """
          package downstream.text

          import com.siimkinks.sqlitemagic.ABS
          import com.siimkinks.sqlitemagic.CONCAT
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.FORMAT
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.LENGTH
          import com.siimkinks.sqlitemagic.LOWER
          import com.siimkinks.sqlitemagic.NOCASE
          import com.siimkinks.sqlitemagic.REPLACE
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SUBSTRING
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.TRIM
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UPPER
          import com.siimkinks.sqlitemagic.collation
          import com.siimkinks.sqlitemagic.plus
          import typewitness.Exact
          import typewitness.exactType

          class Book

          fun verify(
            book: Table<Book>,
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>,
            pages: Column<Book, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>,
            price: Column<Book, Double, Required, Double, Double, Required, Double>,
            cover: Column<Book, ByteArray, SqlNullable, ByteArray?, ByteArray?, SqlNullable, ByteArray?>
          ) {
            val lower = LOWER(title)
            val upper = UPPER(subtitle)
            val length = LENGTH(title)
            val coverLength = LENGTH(cover)
            val prefix = SUBSTRING(title, 2, 5)
            val tail = SUBSTRING(subtitle, 2)
            val coverHead = SUBSTRING(cover, 1, 4)
            val trimmed = TRIM(title)
            val stripped = TRIM(title, "-")
            val replaced = REPLACE(subtitle, "-", " ")
            val formatted = FORMAT("%s #%d", subtitle, id)
            val absolute = ABS(pages)
            val absoluteSum = ABS(price + 1.0)
            val joined = title CONCAT " " CONCAT id
            val nullableJoined = subtitle CONCAT title
            val prefixed = "Book: " CONCAT title
            val collated = title COLLATE NOCASE
            val localized = subtitle COLLATE collation("LOCALIZED")
            val compared = LOWER(title) IS "ada"
            val longer = LENGTH(title) GREATER_THAN 3L
            val collatedIs = (title COLLATE NOCASE) IS "ada"
            val query = SELECT
                .COLUMN(joined)
                .FROM(book)
                .ORDER_BY((title COLLATE NOCASE).ASC())

            val lowerType = lower.exactType()
            val exactLower: Exact<EncodableSqlExpression<String, String, Required, String>> = lowerType
            val upperType = upper.exactType()
            val exactUpper: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> = upperType
            val lengthType = length.exactType()
            val exactLength: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = lengthType
            val coverLengthType = coverLength.exactType()
            val exactCoverLength: Exact<EncodableSqlExpression<Long, Long, SqlNullable, Long?>> =
                coverLengthType
            val prefixType = prefix.exactType()
            val exactPrefix: Exact<EncodableSqlExpression<String, String, Required, String>> = prefixType
            val tailType = tail.exactType()
            val exactTail: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> = tailType
            val coverHeadType = coverHead.exactType()
            val exactCoverHead: Exact<EncodableSqlExpression<ByteArray, ByteArray, SqlNullable, ByteArray?>> =
                coverHeadType
            val trimmedType = trimmed.exactType()
            val exactTrimmed: Exact<EncodableSqlExpression<String, String, Required, String>> = trimmedType
            val strippedType = stripped.exactType()
            val exactStripped: Exact<EncodableSqlExpression<String, String, Required, String>> = strippedType
            val replacedType = replaced.exactType()
            val exactReplaced: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> =
                replacedType
            val formattedType = formatted.exactType()
            val exactFormatted: Exact<EncodableSqlExpression<String, String, Required, String>> = formattedType
            val absoluteType = absolute.exactType()
            val exactAbsolute: Exact<EncodableSqlExpression<Int?, Int, SqlNullable, Int?>> = absoluteType
            val absoluteSumType = absoluteSum.exactType()
            val exactAbsoluteSum: Exact<EncodableSqlExpression<Number, Number, Required, Number>> =
                absoluteSumType
            val joinedType = joined.exactType()
            val exactJoined: Exact<EncodableSqlExpression<String, String, Required, String>> = joinedType
            val nullableJoinedType = nullableJoined.exactType()
            val exactNullableJoined: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> =
                nullableJoinedType
            val prefixedType = prefixed.exactType()
            val exactPrefixed: Exact<EncodableSqlExpression<String, String, Required, String>> = prefixedType
            val collatedType = collated.exactType()
            val exactCollated: Exact<EncodableSqlExpression<String, String, Required, String>> = collatedType
            val localizedType = localized.exactType()
            val exactLocalized: Exact<EncodableSqlExpression<String?, String, SqlNullable, String?>> =
                localizedType
            val comparedType = compared.exactType()
            val exactCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = comparedType
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "receiver helper | val invalid = title.LOWER() | Unresolved reference 'LOWER' on receiver of type 'Column",
      "LOWER of a number | val invalid = LOWER(id) | but 'SqlExpression<String?, Required, *>' was expected",
      """two host strings | val invalid = "a" CONCAT "b" | """ +
          "actual type is 'String', but 'SqlExpression<*, Required, *>' was expected",
      """collation as a string | val invalid = title COLLATE "NOCASE" | """ +
          "actual type is 'String', but 'Collation' was expected",
      """SUBSTRING with a text start | val invalid = SUBSTRING(title, "2") | None of the following candidates""",
      "helper compared with a number | val invalid = LOWER(title) IS 5L | None of the following candidates",
      "ABS of text | val invalid = ABS(title) | but 'EncodableSqlExpression<uninferred W"
    ]
  )
  fun `text helpers outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedTextFunction.kt",
        contents = """
          package downstream.text

          import com.siimkinks.sqlitemagic.ABS
          import com.siimkinks.sqlitemagic.CONCAT
          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.LOWER
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SUBSTRING

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
