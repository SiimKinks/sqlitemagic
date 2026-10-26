package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.requiredReadType
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

internal class ArithmeticSemanticsTest {
  @Test
  fun `arithmetic renders grouped operands and binds plain numbers in SQL order`() {
    val plan = SELECT
      .COLUMN(-(PERSON.AGE + 1) * PERSON.ID)
      .FROM(PERSON)
      .WHERE(((PERSON.AGE + 1) * 2.5) GREATER_THAN 18)
      .ORDER_BY((PERSON.AGE + 1.asLiteral()).DESC())
      .compile()

    assertThat(snapshot(plan)).isEqualTo(
      PlanSnapshot(
        sql = """SELECT (-("person"."age" + ?)) * "person"."id" FROM "main"."person" """ +
            """WHERE (("person"."age" + ?) * ?) > ? ORDER BY "person"."age" + 1 DESC""",
        arguments = listOf(
          NativeValue.Integer(1),
          NativeValue.Integer(1),
          NativeValue.Real(2.5),
          NativeValue.Integer(18)
        )
      )
    )
  }

  @Test
  fun `negation groups its operand so no SQL comment forms`() {
    val sql = SELECT
      .COLUMN(-(-5).asLiteral())
      .compile()
      .sql

    assertThat(sql).isEqualTo("SELECT -(-5)")
  }

  @Test
  fun `custom number operands compute on their stored value`() {
    val amountType = requiredReadType(
      storage = SqlStorage.STRING,
      encode = BigDecimal::toPlainString,
      decode = ::BigDecimal,
      encodingNullness = Required
    )

    personDatabase().use { database ->
      val sum = SELECT
        .COLUMN("'10.50'".expr(amountType) + 1.asValue())
        .usingConnection(database.dbConnection())
        .execute()

      assertThat(sum).isEqualTo(listOf(11.5))
    }
  }

  @Test
  fun `a required result that SQLite computes as NULL fails like every required read`() {
    val infinite = PERSON.AGE * 1e308.asValue()

    val message = personDatabase().use { database ->
      sqlExceptionMessage {
        SELECT
          .COLUMN(infinite - infinite)
          .FROM(PERSON)
          .usingConnection(database.dbConnection())
          .execute()
      }
    }

    assertThat(message).isEqualTo("Required SQL result returned SQL NULL")
  }

  @Test
  fun `an unsupported plain number is rejected when the expression is built`() {
    val failure = assertThrows(IllegalArgumentException::class.java) {
      (PERSON.AGE + 1) + BigDecimal.ONE
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("Unsupported native SQL argument: java.math.BigDecimal")
  }
}
