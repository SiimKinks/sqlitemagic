package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ArithmeticSelectTest(private val case: ArithmeticCase) {
  class ArithmeticCase(
    private val label: String,
    val expression: () -> SqlExpression<Number, SqlNullability, Number?>,
    val expectedValues: List<Number?>
  ) {
    override fun toString() = label
  }

  @Test
  fun `projection reads the SQLite result`() {
    personDatabase().use { database ->
      val values = SELECT
        .COLUMN(case.expression())
        .FROM(PERSON)
        .ORDER_BY(PERSON.ID.ASC())
        .usingConnection(database.dbConnection())
        .execute()

      assertThat(values).isEqualTo(case.expectedValues)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases() = listOf(
      ArithmeticCase(
        label = "plus a plain number",
        expression = { PERSON.AGE + 1 },
        expectedValues = listOf(37L, 86L, 18L, 18L)
      ),
      ArithmeticCase(
        label = "plus a field of another numeric type",
        expression = { PERSON.AGE + PERSON.ID },
        expectedValues = listOf(37L, 87L, 20L, 21L)
      ),
      ArithmeticCase(
        label = "a NULL operand yields NULL",
        expression = { PERSON.AGE + PERSON.PARENT_ID },
        expectedValues = listOf(null, 86L, 19L, 21L)
      ),
      ArithmeticCase(
        label = "minus",
        expression = { PERSON.ID - 1L },
        expectedValues = listOf(0L, 1L, 2L, 3L)
      ),
      ArithmeticCase(
        label = "times a REAL value reads Double",
        expression = { PERSON.AGE * 1.5.asValue() },
        expectedValues = listOf(54.0, 127.5, 25.5, 25.5)
      ),
      ArithmeticCase(
        label = "integer division",
        expression = { PERSON.AGE / 2 },
        expectedValues = listOf(18L, 42L, 8L, 8L)
      ),
      ArithmeticCase(
        label = "REAL division",
        expression = { PERSON.AGE / 2.0.asValue() },
        expectedValues = listOf(18.0, 42.5, 8.5, 8.5)
      ),
      ArithmeticCase(
        label = "division by zero yields NULL",
        expression = { PERSON.AGE / 0 },
        expectedValues = listOf(null, null, null, null)
      ),
      ArithmeticCase(
        label = "remainder",
        expression = { PERSON.AGE % 5 },
        expectedValues = listOf(1L, 0L, 2L, 2L)
      ),
      ArithmeticCase(
        label = "REAL remainder",
        expression = { (PERSON.AGE * 1.5.asValue()) % 2 },
        expectedValues = listOf(0.0, 1.0, 1.0, 1.0)
      ),
      ArithmeticCase(
        label = "unary minus",
        expression = { -PERSON.AGE },
        expectedValues = listOf(-36L, -85L, -17L, -17L)
      ),
      ArithmeticCase(
        label = "chained plain numbers on results",
        expression = { ((((PERSON.AGE + 2) * 2.0) - 4.0) / 2.0) % 100.0 },
        expectedValues = listOf(36.0, 85.0, 17.0, 17.0)
      ),
      ArithmeticCase(
        label = "integer overflow reads SQLite's REAL",
        expression = { PERSON.ID * 4611686018427387904L },
        expectedValues = listOf(
          4611686018427387904L,
          9.223372036854776E18,
          1.3835058055282164E19,
          1.8446744073709552E19
        )
      )
    )
  }
}
