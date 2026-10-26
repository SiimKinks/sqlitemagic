package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class PredicateContractTest {
  @Test
  fun `predicates infer their types and compose into clauses`() {
    compileExpressionConsumer(
      """
        fun verify(connection: DbConnection) {
          val adults = (SELECT COLUMN PERSON.NAME FROM PERSON) WHERE (PERSON.AGE GREATER_OR_EQUAL 18)
          val sameOwner = PERSON.ID IS PERSON.PARENT_ID
          val widened = PERSON.ID GREATER_THAN PERSON.AGE
          val unnamed = PERSON.NAME.IS_NULL()
          val named = PERSON.NAME.IS_NOT_NULL()
          val filter = (PERSON.AGE GREATER_THAN 18) AND !(PERSON.NAME IS "Ada")
          val flags = SELECT.COLUMN(PERSON.AGE GREATER_THAN 18)
              .FROM(PERSON)
          val flagRows = flags.execute()
          val fluent = PERSON.AGE.LESS_THAN(PERSON.AGE)
              .OR(PERSON.AGE.LESS_OR_EQUAL(65))
              .AND_NOT(PERSON.NAME.IS_NOT("Grace"))
              .OR_NOT(PERSON.ID.GREATER_OR_EQUAL(PERSON.ID))
          val infix = (PERSON.ID IS_NOT 7L) OR_NOT (PERSON.AGE LESS_THAN 30) AND_NOT unnamed
          val reversedWidening = PERSON.AGE LESS_OR_EQUAL PERSON.ID
          val nullableOperand = PERSON.TOKEN IS PERSON.DECODED
          val nullableReceiver = PERSON.DECODED IS PERSON.TOKEN
          val nullableHost = PERSON.DECODED IS Token("decoded")
          val customHost = PERSON.TOKEN GREATER_THAN Token("token")
          val aliasedHost = PERSON.AGE.AS("years") LESS_THAN 40
          val name = PERSON.NAME.asParameter("name")
          val parameterOperand = PERSON.NAME IS name
          val valueOperand = PERSON.ID IS_NOT 3L.asValue()
          val sqlNull = PERSON.NAME IS NULL
          val raw = "lower(person.name)".expr(SqlType.STRING)
          val observed = raw.observes(PERSON)
          val rawOperand = observed IS PERSON.NAME
          val rawHost = observed IS "ada"
          val rawCustomHost = "person.token".expr(TOKEN_TYPE) IS Token("a")
          val readOnlyRaw = "person.age".expr(SqlType.NUMBER)
          val rawPredicate = "person.age > ?".expr(SqlType.BOOLEAN, 21)
              .observes(PERSON)
          val composedRaw = rawPredicate AND !filter
          val predicateComparison = (PERSON.AGE GREATER_THAN 18) IS (PERSON.ID LESS_THAN 9L)
          val grouped = SELECT.COLUMN(PERSON.AGE)
              .FROM(PERSON)
              .WHERE(composedRaw)
              .GROUP_BY(PERSON.AGE)
              .HAVING(PERSON.AGE GREATER_THAN 20)
          val pair = SELECT
              .COLUMNS(PERSON.ID, named)
              .FROM(PERSON)
              .WHERE(sameOwner OR widened)
          val pairRows = pair.usingConnection(connection)
              .execute()

          val adultsType = adults.exactType()
          val sameOwnerType = sameOwner.exactType()
          val widenedType = widened.exactType()
          val unnamedType = unnamed.exactType()
          val namedType = named.exactType()
          val filterType = filter.exactType()
          val flagsType = flags.exactType()
          val flagRowsType = flagRows.exactType()
          val fluentType = fluent.exactType()
          val infixType = infix.exactType()
          val reversedWideningType = reversedWidening.exactType()
          val nullableOperandType = nullableOperand.exactType()
          val nullableReceiverType = nullableReceiver.exactType()
          val nullableHostType = nullableHost.exactType()
          val customHostType = customHost.exactType()
          val aliasedHostType = aliasedHost.exactType()
          val parameterOperandType = parameterOperand.exactType()
          val valueOperandType = valueOperand.exactType()
          val sqlNullType = sqlNull.exactType()
          val rawType = raw.exactType()
          val observedType = observed.exactType()
          val rawOperandType = rawOperand.exactType()
          val rawHostType = rawHost.exactType()
          val rawCustomHostType = rawCustomHost.exactType()
          val readOnlyRawType = readOnlyRaw.exactType()
          val composedRawType = composedRaw.exactType()
          val predicateComparisonType = predicateComparison.exactType()
          val groupedType = grouped.exactType()
          val pairRowsType = pairRows.exactType()
          val exactAdults: Exact<SelectWhereStage<String?, ScalarSelection<String, SqlNullable, String?>>> = adultsType
          val exactSameOwner: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = sameOwnerType
          val exactWidened: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedType
          val exactUnnamed: Exact<SqlExpression<Boolean, Required, Boolean>> = unnamedType
          val exactNamed: Exact<SqlExpression<Boolean, Required, Boolean>> = namedType
          val exactFilter: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = filterType
          val exactFlags: Exact<SelectFromStage<Boolean?, ScalarSelection<Boolean, SqlNullable, Boolean?>>> = flagsType
          val exactFlagRows: Exact<List<Boolean?>> = flagRowsType
          val exactFluent: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = fluentType
          val exactInfix: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = infixType
          val exactReversedWidening: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = reversedWideningType
          val exactNullableOperand: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nullableOperandType
          val exactNullableReceiver: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nullableReceiverType
          val exactNullableHost: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nullableHostType
          val exactCustomHost: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = customHostType
          val exactAliasedHost: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = aliasedHostType
          val exactParameterOperand: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = parameterOperandType
          val exactValueOperand: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = valueOperandType
          val exactSqlNull: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = sqlNullType
          val exactRaw: Exact<EncodableRawSqlExpression<String, String, Required, String>> = rawType
          val exactObserved: Exact<EncodableRawSqlExpression<String, String, Required, String>> = observedType
          val exactRawOperand: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rawOperandType
          val exactRawHost: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rawHostType
          val exactRawCustomHost: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rawCustomHostType
          val exactReadOnlyRaw: Exact<RawSqlExpression<Number, Required, Number>> = readOnlyRawType
          val exactComposedRaw: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = composedRawType
          val exactPredicateComparison: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> =
              predicateComparisonType
          val exactGrouped: Exact<SelectHavingStage<Int, ScalarSelection<Int, Required, Int>>> = groupedType
          val exactPairRows: Exact<List<Row2<Long, Boolean>>> = pairRowsType
        }

        fun verifyMembership(ids: List<Long>, names: Set<String>, pattern: String) {
          val hostIn = PERSON.ID IN ids
          val emptyIn = PERSON.ID IN emptyList()
          val arrayNotIn = PERSON.ID NOT_IN arrayOf(4L, 5L)
          val varargIn = PERSON.ID.IN(1L, 2L, 3L)
          val nullableFieldIn = PERSON.NAME IN names
          val customNotIn = PERSON.TOKEN NOT_IN listOf(Token("a"))
          val typedIn = PERSON.ID.IN(PERSON.PARENT_ID, 5L.asValue())
          val widenedIn = PERSON.ID.NOT_IN(PERSON.AGE, 7.asValue())
          val rawIn = "lower(person.name)".expr(SqlType.STRING).IN(PERSON.NAME, NULL)
          val start = PERSON.AGE BETWEEN 18
          val range = PERSON.AGE BETWEEN 18 AND 65
          val outside = PERSON.AGE NOT_BETWEEN 18 AND PERSON.AGE
          val typedLower = PERSON.ID BETWEEN PERSON.PARENT_ID AND 9L
          val typedBounds = PERSON.NAME BETWEEN PERSON.NAME AND "M".asValue()
          val textRange = PERSON.NAME BETWEEN "A" AND "M"
          val customRange = PERSON.TOKEN BETWEEN Token("a") AND Token("m")
          val widenedLower = PERSON.ID BETWEEN PERSON.AGE AND 100L
          val widenedUpper = PERSON.ID NOT_BETWEEN 1L AND PERSON.AGE
          val rawRange = "person.age".expr(SqlType.INT) BETWEEN PERSON.AGE AND PERSON.AGE
          val booleanRange = (PERSON.AGE GREATER_THAN 1) BETWEEN (PERSON.AGE LESS_THAN 2) AND (PERSON.ID IS 1L)
          val fluentRange = PERSON.AGE.BETWEEN(18).AND(65)
          val chained = PERSON.AGE BETWEEN 18 AND 65 AND (PERSON.NAME IS "Ada")
          val like = PERSON.NAME LIKE pattern
          val escaped = PERSON.NAME LIKE "A!_%" ESCAPE "!"
          val notLike = PERSON.NAME NOT_LIKE PERSON.NAME.asParameter("pattern")
          val glob = PERSON.AGE GLOB "1*"
          val notGlob = PERSON.TOKEN NOT_GLOB "db:*"
          val query = (SELECT COLUMN PERSON.NAME FROM PERSON) WHERE ((PERSON.ID IN ids) AND (PERSON.NAME LIKE "A%"))

          val hostInType = hostIn.exactType()
          val emptyInType = emptyIn.exactType()
          val arrayNotInType = arrayNotIn.exactType()
          val varargInType = varargIn.exactType()
          val nullableFieldInType = nullableFieldIn.exactType()
          val customNotInType = customNotIn.exactType()
          val typedInType = typedIn.exactType()
          val widenedInType = widenedIn.exactType()
          val rawInType = rawIn.exactType()
          val startType = start.exactType()
          val rangeType = range.exactType()
          val outsideType = outside.exactType()
          val typedLowerType = typedLower.exactType()
          val typedBoundsType = typedBounds.exactType()
          val textRangeType = textRange.exactType()
          val customRangeType = customRange.exactType()
          val widenedLowerType = widenedLower.exactType()
          val widenedUpperType = widenedUpper.exactType()
          val rawRangeType = rawRange.exactType()
          val booleanRangeType = booleanRange.exactType()
          val fluentRangeType = fluentRange.exactType()
          val chainedType = chained.exactType()
          val likeType = like.exactType()
          val escapedType = escaped.exactType()
          val notLikeType = notLike.exactType()
          val globType = glob.exactType()
          val notGlobType = notGlob.exactType()
          val queryType = query.exactType()
          val exactHostIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = hostInType
          val exactEmptyIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = emptyInType
          val exactArrayNotIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = arrayNotInType
          val exactVarargIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = varargInType
          val exactNullableFieldIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = nullableFieldInType
          val exactCustomNotIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = customNotInType
          val exactTypedIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = typedInType
          val exactWidenedIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedInType
          val exactRawIn: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rawInType
          val exactStart: Exact<EncodableSqlRangeStart<Int, Int>> = startType
          val exactRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rangeType
          val exactOutside: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = outsideType
          val exactTypedLower: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = typedLowerType
          val exactTypedBounds: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = typedBoundsType
          val exactTextRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = textRangeType
          val exactCustomRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = customRangeType
          val exactWidenedLower: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedLowerType
          val exactWidenedUpper: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = widenedUpperType
          val exactRawRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = rawRangeType
          val exactBooleanRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = booleanRangeType
          val exactFluentRange: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = fluentRangeType
          val exactChained: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = chainedType
          val exactLike: Exact<LikeSqlExpression> = likeType
          val exactEscaped: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = escapedType
          val exactNotLike: Exact<LikeSqlExpression> = notLikeType
          val exactGlob: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = globType
          val exactNotGlob: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = notGlobType
          val exactQuery: Exact<SelectWhereStage<String?, ScalarSelection<String, SqlNullable, String?>>> = queryType
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "decoded types differ | PERSON.ID IS PERSON.NAME | " +
          "actual type is 'Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>', " +
          "but 'SqlExpression<Long?, *, *>' was expected",
      "ordered decoded types differ | PERSON.NAME GREATER_THAN PERSON.AGE | " +
          "actual type is 'Column<Person, Int, Required, Int, Int, Required, Int>', " +
          "but 'SqlExpression<String?, *, *>' was expected",
      "host null | PERSON.NAME IS null | Null cannot be a value of a non-null type 'SqlExpression<String?, *, *>'",
      "host value of another type | PERSON.ID IS ageValue | actual type is 'Int', but 'Long' was expected",
      """host value without the field encoder | PERSON.TOKEN IS "token" | """ +
          "actual type is 'String', but 'Token' was expected",
      """read-only raw expressions have no encoder | "person.age".expr(SqlType.NUMBER) IS 1 | """ +
          "actual type is 'Int', but 'SqlExpression<Number?, *, *>' was expected",
      """raw expressions have no parameter form | "1".expr(SqlType.LONG).asParameter("raw") | """ +
          "Unresolved reference 'asParameter' on receiver of type 'EncodableRawSqlExpression<",
      "observes on a field | PERSON.ID.observes(PERSON) | " +
          "Unresolved reference 'observes' on receiver of type 'UniqueColumn<",
      "observes on a predicate | (PERSON.ID IS 1L).observes(PERSON) | " +
          "Unresolved reference 'observes' on receiver of type 'SqlExpression<",
      """observes without a table | "lower(person.name)".expr(SqlType.STRING).observes() | """ +
          "No value passed for parameter 'table'",
      "non Boolean AND | PERSON.AGE AND PERSON.AGE | " +
          "SqlExpression<Boolean, *, *>.AND(other: SqlExpression<Boolean, *, *>): " +
          "SqlExpression<Boolean, SqlNullable, Boolean?>' is inapplicable because of a receiver type mismatch",
      "non Boolean NOT | !PERSON.AGE | " +
          "SqlExpression<Boolean, *, *>.not(): SqlExpression<Boolean, SqlNullable, Boolean?>' " +
          "is inapplicable because of a receiver type mismatch",
      "IN null element | PERSON.ID IN listOf(1L, null) | " +
          "actual type is 'List<Long?>', but 'Collection<Long>' was expected",
      "IN elements of another type | PERSON.ID IN listOf(ageValue) | " +
          "actual type is 'List<Int>', but 'Collection<Long>' was expected",
      """IN host values without an encoder | "person.age".expr(SqlType.NUMBER) IN listOf(ageValue) | """ +
          "actual type is 'List<Int>', but 'SqlExpression<Number?, *, *>' was expected",
      "mixed plain and typed IN varargs | PERSON.ID.IN(PERSON.PARENT_ID, 5L) | " +
          "actual type is 'Long', but 'SqlExpression<Long?, *, *>' was expected",
      "typed IN collection | PERSON.ID IN listOf(PERSON.PARENT_ID) | " +
          "actual type is 'List<Column<Person, Long, SqlNullable, Long?, Long?, SqlNullable, Long?>>', " +
          "but 'Collection<Long>' was expected",
      "IN operand of another decoded type | PERSON.ID.IN(PERSON.NAME) | " +
          "actual type is 'Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>', " +
          "but 'SqlExpression<Long?, *, *>' was expected",
      "BETWEEN host bound of another type | PERSON.AGE BETWEEN 1L AND 2L | " +
          "actual type is 'Long', but 'Int' was expected",
      """BETWEEN host bounds without an encoder | "person.age".expr(SqlType.NUMBER) BETWEEN 1 AND 2 | """ +
          "actual type is 'Int', but 'SqlExpression<Number?, *, *>' was expected",
      """upper bound of another decoded type | PERSON.NAME BETWEEN "A" AND PERSON.AGE | """ +
          "actual type is 'Column<Person, Int, Required, Int, Int, Required, Int>', " +
          "but 'SqlExpression<String?, *, *>' was expected",
      "parenthesized bound pair | PERSON.AGE BETWEEN (18 AND 65) | " +
          "SqlRangeStart<Number?>.AND(upper: SqlExpression<Number?, *, *>): " +
          "SqlExpression<Boolean, SqlNullable, Boolean?>' is inapplicable because of a receiver type mismatch",
      "unfinished range as a predicate | SELECT.COLUMN(PERSON.ID).FROM(PERSON).WHERE(PERSON.AGE BETWEEN 18) | " +
          "actual type is 'EncodableSqlRangeStart<Int, Int>', but 'SqlExpression<Boolean, *, *>' was expected",
      """ESCAPE after GLOB | PERSON.NAME GLOB "a" ESCAPE "!" | """ +
          "Unresolved reference 'ESCAPE' on receiver of type 'SqlExpression<",
      "pattern of another decoded type | PERSON.NAME LIKE PERSON.AGE | " +
          "actual type is 'Column<Person, Int, Required, Int, Int, Required, Int>', " +
          "but 'SqlExpression<String?, *, *>' was expected",
      "null pattern | PERSON.NAME LIKE null | Null cannot be a value of a non-null type 'SqlExpression<String?, *, *>'"
    ]
  )
  fun `predicate, membership, range and pattern operands outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileExpressionConsumer(
      """
        fun invalid(ageValue: Int) {
          val predicate = $program
        }
      """
    ).assertCompilationError(diagnostic)
  }
}
