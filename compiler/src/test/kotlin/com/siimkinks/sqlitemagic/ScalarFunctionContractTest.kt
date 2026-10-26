package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ScalarFunctionContractTest {
  @Test
  fun `declared functions read through their descriptor and compare through its encoder`() {
    PlainKotlinCompilation
      .compile(
        fileName = "ScalarFunctionConsumer.kt",
        contents = """
          @file:OptIn(InternalSqliteMagicApi::class)

          package downstream.functions

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EncodableScalarFunction
          import com.siimkinks.sqlitemagic.EncodableSqlExpression
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.ScalarFunction
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
          import com.siimkinks.sqlitemagic.internal.SqlStorage
          import com.siimkinks.sqlitemagic.internal.requiredReadType
          import com.siimkinks.sqlitemagic.scalarFunction
          import typewitness.Exact
          import typewitness.exactType

          class Book
          class Email(val value: String)

          val EMAIL = requiredReadType(
            storage = SqlStorage.STRING,
            encode = Email::value,
            decode = ::Email,
            encodingNullness = Required
          )

          fun verify(
            book: Table<Book>,
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>,
            subtitle: Column<Book, String, SqlNullable, String?, String?, SqlNullable, String?>
          ) {
            val instr = scalarFunction("instr").readAs(SqlType.LONG)
            val normalize = scalarFunction("normalize-name").readAs(SqlType.STRING.nullable)
            val canonical = scalarFunction("canonical_email").readAs(EMAIL)
            val score = scalarFunction("score").readAs(SqlType.NUMBER)
            val lookup = scalarFunction("lookup_label")
                .readAs(SqlType.STRING.nullable)
                .observes(book)
            val version = scalarFunction("sqlite_version").readAs(SqlType.STRING)

            val position = instr(title, "a")
            val normalized = normalize(subtitle)
            val canonicalEmail = canonical(title)
            val scored = score(id, 2)
            val label = lookup(id)
            val engine = version()
            val compared = normalize(title) IS "ada"
            val positive = instr(title, "a") GREATER_THAN 0L
            val listed = normalize(title) IN listOf("ada", "grace")
            val sameEmail = canonical(title) IS Email("ada@example.com")
            val ranked = score(id) GREATER_THAN score(id, 1)
            val query = SELECT
                .COLUMN(normalize(title))
                .FROM(book)
                .WHERE(instr(title, "a") GREATER_THAN 0L)

            val instrType = instr.exactType()
            val exactInstr: Exact<EncodableScalarFunction<Long, Long, Required, Long>> = instrType
            val normalizeType = normalize.exactType()
            val exactNormalize: Exact<EncodableScalarFunction<String, String, SqlNullable, String?>> =
                normalizeType
            val scoreType = score.exactType()
            val exactScore: Exact<ScalarFunction<Number, Required, Number>> = scoreType
            val positionType = position.exactType()
            val exactPosition: Exact<EncodableSqlExpression<Long, Long, Required, Long>> = positionType
            val normalizedType = normalized.exactType()
            val exactNormalized: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> =
                normalizedType
            val canonicalType = canonicalEmail.exactType()
            val exactCanonical: Exact<EncodableSqlExpression<Email, Email, Required, Email>> =
                canonicalType
            val scoredType = scored.exactType()
            val exactScored: Exact<SqlExpression<Number, Required, Number>> = scoredType
            val labelType = label.exactType()
            val exactLabel: Exact<EncodableSqlExpression<String, String, SqlNullable, String?>> = labelType
            val engineType = engine.exactType()
            val exactEngine: Exact<EncodableSqlExpression<String, String, Required, String>> = engineType
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
      """name before readAs | val invalid = scalarFunction("lower")(title) | """ +
          "Unresolved reference 'invoke' on receiver of type 'ScalarFunctionName'",
      """observes before readAs | val invalid = scalarFunction("lower").observes(book) | """ +
          "Unresolved reference 'observes'",
      "observes without a table | val invalid = lower.observes() | No value passed for parameter 'table'",
      "readAs twice | val invalid = lower.readAs(SqlType.LONG) | Unresolved reference 'readAs'",
      "read-only result with a host value | val invalid = score(id) GREATER_THAN 5L | " +
          "actual type is 'Long', but 'SqlExpression<Number?, *, *>' was expected",
      "host value of another type | val invalid = lower(title) IS 5L | " +
          "actual type is 'Long', but 'String' was expected"
    ]
  )
  fun `declared functions outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedScalarFunction.kt",
        contents = """
          package downstream.functions

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.GREATER_THAN
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SqlType
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.scalarFunction

          class Book

          fun verify(
            book: Table<Book>,
            id: Column<Book, Long, Required, Long, Long, Required, Long>,
            title: Column<Book, String, Required, String, String, Required, String>
          ) {
            val lower = scalarFunction("lower").readAs(SqlType.STRING)
            val score = scalarFunction("score").readAs(SqlType.NUMBER)
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
