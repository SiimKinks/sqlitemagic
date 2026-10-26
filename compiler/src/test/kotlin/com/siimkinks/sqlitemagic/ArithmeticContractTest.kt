package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ArithmeticContractTest {
  @Test
  fun `arithmetic infers Number results with precise nullness and composes with predicates and clauses`() {
    compileExpressionConsumer(
      """
        fun verify(connection: DbConnection) {
          val next = PERSON.AGE + 1
          val sum = PERSON.AGE + PERSON.ID
          val maybe = PERSON.AGE + PERSON.PARENT_ID
          val difference = PERSON.ID - 1L
          val product = PERSON.AGE * PERSON.AGE
          val quotient = PERSON.AGE / 2
          val remainder = PERSON.ID % PERSON.AGE
          val hostRemainder = PERSON.AGE % 2
          val negated = -PERSON.AGE
          val nullableNegated = -PERSON.PARENT_ID
          val chain = ((((PERSON.AGE + 2) * 2.0) - 4.0) / 2.0) % 100.0
          val value = 5.asValue() - 3
          val nullableHost = PERSON.PARENT_ID + 1L
          val literal = PERSON.AGE + 1.asLiteral()
          val raw = "person.age".expr(SqlType.INT) + PERSON.AGE
          val parameter = PERSON.AGE + PERSON.AGE.asParameter("delta")
          val compared = (PERSON.AGE + 1) GREATER_THAN 18
          val precedence = PERSON.AGE + 1 GREATER_THAN 18
          val member = (PERSON.AGE + 1) IN listOf(18, 37)
          val ranged = (PERSON.AGE * 2) BETWEEN 30 AND 70.5
          val widened = (PERSON.AGE + 1) IS PERSON.ID
          val rows = SELECT.COLUMN(PERSON.AGE + 1)
              .FROM(PERSON)
              .usingConnection(connection)
              .execute()
          val nullableRows = SELECT.COLUMN(PERSON.AGE / 2)
              .FROM(PERSON)
              .usingConnection(connection)
              .execute()
          val longs = SELECT.COLUMN(PERSON.AGE + 1)
              .FROM(PERSON)
              .readAs(SqlType.LONG)
              .usingConnection(connection)
              .execute()
          val ordered = (SELECT COLUMN PERSON.ID FROM PERSON) ORDER_BY (PERSON.AGE * -1).ASC()

          val nextType = next.exactType()
          val sumType = sum.exactType()
          val maybeType = maybe.exactType()
          val differenceType = difference.exactType()
          val productType = product.exactType()
          val quotientType = quotient.exactType()
          val remainderType = remainder.exactType()
          val hostRemainderType = hostRemainder.exactType()
          val negatedType = negated.exactType()
          val nullableNegatedType = nullableNegated.exactType()
          val chainType = chain.exactType()
          val valueType = value.exactType()
          val nullableHostType = nullableHost.exactType()
          val literalType = literal.exactType()
          val rawType = raw.exactType()
          val parameterType = parameter.exactType()
          val comparedType = compared.exactType()
          val precedenceType = precedence.exactType()
          val memberType = member.exactType()
          val rangedType = ranged.exactType()
          val widenedType = widened.exactType()
          val rowsType = rows.exactType()
          val nullableRowsType = nullableRows.exactType()
          val longsType = longs.exactType()
          val exactNext: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = nextType
          val exactSum: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = sumType
          val exactMaybe: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> = maybeType
          val exactDifference: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = differenceType
          val exactProduct: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = productType
          val exactQuotient: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> = quotientType
          val exactRemainder: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> = remainderType
          val exactHostRemainder: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> =
              hostRemainderType
          val exactNegated: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = negatedType
          val exactNullableNegated: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> =
              nullableNegatedType
          val exactChain: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> = chainType
          val exactValue: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = valueType
          val exactNullableHost: Exact<EncodableSqlExpression<Number, Number, SqlNullable, Number?>> = nullableHostType
          val exactLiteral: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = literalType
          val exactRaw: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = rawType
          val exactParameter: Exact<EncodableSqlExpression<Number, Number, Required, Number>> = parameterType
          val exactCompared: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = comparedType
          val exactPrecedence: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = precedenceType
          val exactMember: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = memberType
          val exactRanged: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rangedType
          val exactWidened: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedType
          val exactRows: Exact<List<Number>> = rowsType
          val exactNullableRows: Exact<List<Number?>> = nullableRowsType
          val exactLongs: Exact<List<Long>> = longsType
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "text operand | PERSON.NAME + PERSON.NAME | $NUMBER_PLUS_RECEIVER_MISMATCH",
      """text plain value | PERSON.NAME + "x" | $NUMBER_PLUS_RECEIVER_MISMATCH""",
      "plain number of another type on a field | PERSON.AGE + 2.5 | " +
          "actual type is 'Double', but 'SqlExpression<Number?, Required, *>' was expected",
      """plain number on read-only raw SQL | "person.age".expr(SqlType.NUMBER) + 1 | """ +
          "actual type is 'Int', but 'SqlExpression<Number?, Required, *>' was expected",
      "left-hand plain number | 1 + PERSON.AGE | " +
          "actual type is 'Column<Person, Int, Required, Int, Int, Required, Int>', but 'Int' was expected",
      "predicate operand | (PERSON.AGE GREATER_THAN 1) + 1 | $NUMBER_PLUS_RECEIVER_MISMATCH",
      """non-number plain value on a result | (PERSON.AGE + 1) * "2" | """ +
          "actual type is 'String', but 'SqlExpression<Number?, Required, *>' was expected",
      "unary minus on text | -PERSON.NAME | " +
          "SqlExpression<Number?, Required, *>.unaryMinus(): EncodableSqlExpression<Number, Number, Required, " +
          "Number>' is inapplicable because of a receiver type mismatch",
      """result as a parameter | (PERSON.AGE + 1).asParameter("delta") | """ +
          "Unresolved reference 'asParameter' on receiver of type 'EncodableSqlExpression<",
      "AND between results | (PERSON.AGE + 1) AND (PERSON.AGE + 2) | " +
          "SqlExpression<Boolean, *, *>.AND(other: SqlExpression<Boolean, *, *>): " +
          "SqlExpression<Boolean, SqlNullable, Boolean?>' is inapplicable because of a receiver type mismatch"
    ]
  )
  fun `arithmetic operands outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileExpressionConsumer(
      """
        fun invalid() {
          val result = $program
        }
      """
    ).assertCompilationError(diagnostic)
  }

  private companion object {
    const val NUMBER_PLUS_RECEIVER_MISMATCH = "SqlExpression<Number?, Required, *>.plus(" +
        "other: SqlExpression<Number?, Required, *>): EncodableSqlExpression<Number, Number, Required, Number>' " +
        "is inapplicable because of a receiver type mismatch"
  }
}
