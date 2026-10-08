package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class RuntimeDslClauseSequencingContractTest {
  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "fluent complete",
    "grouped without WHERE",
    "ordered pagination",
    "infix complete"
  )
  fun `supported clause sequences compile`(label: String) {
    val expression = when (label) {
      "fluent complete" -> """
        Select.column(column)
          .from(table)
          .where(column.`is`(1))
          .groupBy(column)
          .having(column.greaterThan(0))
          .orderBy(column.asc())
          .limit(1)
          .offset(0)
      """
      "grouped without WHERE" -> """
        Select.column(column)
          .from(table)
          .groupBy(column)
          .having(column.greaterThan(0))
          .limit(1)
      """
      "ordered pagination" -> """
        Select.column(column)
          .from(table)
          .orderBy(column.desc())
          .limit(2)
          .offset(1)
      """
      else -> """
        (SELECT COLUMN column FROM table WHERE (column IS 1) GROUP_BY column
          HAVING (column GREATER_THAN 0) ORDER_BY column.asc() LIMIT 1 OFFSET 0)
      """
    }
    compile(expression).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "HAVING requires GROUP BY, having",
    "WHERE cannot follow LIMIT, where",
    "GROUP BY cannot follow ORDER BY, groupBy",
    "OFFSET requires LIMIT, offset",
    "WHERE cannot repeat, where"
  )
  fun `unsupported clause sequences reject the unavailable stage operation`(
    label: String,
    symbol: String
  ) {
    val expression = when (label) {
      "HAVING requires GROUP BY" -> "Select.from(table).having(column.`is`(1))"
      "WHERE cannot follow LIMIT" -> "Select.from(table).limit(1).where(column.`is`(1))"
      "GROUP BY cannot follow ORDER BY" -> "Select.from(table).orderBy(column.asc()).groupBy(column)"
      "OFFSET requires LIMIT" -> "Select.from(table).offset(1)"
      else -> "Select.from(table).where(column.`is`(1)).where(column.`is`(2))"
    }
    compile(expression).assertCompilationError(symbol)
  }

  private fun compile(expression: String) = SqliteMagicCompilation
    .compile(
      SourceFile.kotlin(
        name = "ClauseSequencingConsumer.kt",
        contents = """
          package downstream

          import com.siimkinks.sqlitemagic.*

          fun query(
            table: Table<Any>,
            column: NumericColumn<Int, Int, Number, Any, NotNullable>
          ) = $expression
        """
      ),
      processingStepsFactory = null
    )
}
