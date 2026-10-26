package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class JoinContractTest {
  @Test
  fun `joins follow SQL order and outer-joined values lift per field`() {
    compileJoinConsumer(
      """
        fun verify(connection: DbConnection) {
          val owner = PERSON AS "owner"
          val fluentOwner = PERSON.AS("owner")
          val lifted = PET.NAME.nullable()
          val leftJoined = SELECT.COLUMNS(PERSON.NAME, PET.NAME.nullable())
              .FROM(PERSON)
              .LEFT_JOIN(PET)
          val left = leftJoined.ON(PERSON.ID IS PET.OWNER)
          val infix = SELECT COLUMN PET.NAME FROM PET INNER_JOIN owner ON (PET.OWNER IS owner.ID) WHERE
              (owner.AGE GREATER_THAN 18)
          val entities = SELECT FROM PERSON INNER_JOIN PET ON (PERSON.ID IS PET.OWNER)
          val usingOne = SELECT FROM PERSON INNER_JOIN owner USING owner.ID
          val usingMany = SELECT.FROM(PERSON)
              .INNER_JOIN(owner)
              .USING(owner.ID, owner.NAME)
          val crossed = SELECT.COLUMN(PERSON.ID)
              .FROM(PERSON)
              .CROSS_JOIN(PET)
          val crossedOn = crossed.ON(PERSON.ID IS PET.OWNER)
          val natural = SELECT.COLUMN(PERSON.ID)
              .FROM(PERSON)
              .NATURAL_JOIN(owner)
          val naturalLeft = SELECT COLUMN PERSON.ID FROM PERSON NATURAL_LEFT_JOIN PET
          val naturalCross = SELECT COLUMN PERSON.ID FROM PERSON NATURAL_CROSS_JOIN PET
          val withoutPets = SELECT FROM PERSON LEFT_JOIN PET ON (PERSON.ID IS PET.OWNER) WHERE (PET.ID.IS_NULL())
          val computed = SELECT COLUMN (PET.ID.nullable() + 1) FROM PERSON LEFT_JOIN PET ON (PERSON.ID IS PET.OWNER)
          val chained = SELECT.COLUMN(PERSON.NAME)
              .FROM(PERSON)
              .LEFT_JOIN(PET)
              .ON(PERSON.ID IS PET.OWNER)
              .INNER_JOIN(owner)
              .ON(PERSON.PARENT_ID IS owner.ID)
              .WHERE(owner.AGE GREATER_THAN 18)
              .GROUP_BY(PERSON.NAME)
              .ORDER_BY(PERSON.NAME.ASC())
              .LIMIT(1)
              .OFFSET(0)
          val rightJoined = SELECT.COLUMNS(PERSON.ID.nullable(), PET.NAME)
              .FROM(PERSON)
              .RIGHT_JOIN(PET)
          val right = rightJoined.ON(PERSON.ID IS PET.OWNER)
          val full = SELECT COLUMN PET.NAME.nullable() FROM PERSON FULL_JOIN PET USING PET.ID
          val bareFull = SELECT.FROM(PERSON)
              .FULL_JOIN(owner)
          val naturalRight = SELECT COLUMN PET.ID FROM PERSON NATURAL_RIGHT_JOIN PET
          val naturalFull = SELECT.COLUMN(PET.ID.nullable())
              .FROM(PERSON)
              .NATURAL_FULL_JOIN(PET)
          val merged = SELECT COLUMN "id".expr(SqlType.LONG) FROM PERSON FULL_JOIN owner USING owner.ID
          val deep = (SELECT FROM PERSON INNER_JOIN PET ON (PERSON.ID IS PET.OWNER)).queryDeep()
          val rows = left
              .usingConnection(connection)
              .execute()

          val ownerType = owner.exactType()
          val fluentOwnerType = fluentOwner.exactType()
          val liftedType = lifted.exactType()
          val leftJoinedType = leftJoined.exactType()
          val leftType = left.exactType()
          val infixType = infix.exactType()
          val entitiesType = entities.exactType()
          val usingOneType = usingOne.exactType()
          val usingManyType = usingMany.exactType()
          val crossedType = crossed.exactType()
          val crossedOnType = crossedOn.exactType()
          val naturalType = natural.exactType()
          val naturalLeftType = naturalLeft.exactType()
          val naturalCrossType = naturalCross.exactType()
          val withoutPetsType = withoutPets.exactType()
          val computedType = computed.exactType()
          val chainedType = chained.exactType()
          val rightJoinedType = rightJoined.exactType()
          val rightType = right.exactType()
          val fullType = full.exactType()
          val bareFullType = bareFull.exactType()
          val naturalRightType = naturalRight.exactType()
          val naturalFullType = naturalFull.exactType()
          val mergedType = merged.exactType()
          val deepType = deep.exactType()
          val rowsType = rows.exactType()
          val exactOwner: Exact<PersonTable> = ownerType
          val exactFluentOwner: Exact<PersonTable> = fluentOwnerType
          val exactLifted: Exact<NullableEncodableExpression<String, String, String>> = liftedType
          val liftedExpression: SqlExpression<String, SqlNullable, String?> = lifted
          val exactLeftJoined:
              Exact<SelectJoinStage<Row2<String?, String?>, RowSelection<Row2<String?, String?>>>> = leftJoinedType
          val exactLeft: Exact<SelectFromStage<Row2<String?, String?>, RowSelection<Row2<String?, String?>>>> = leftType
          val exactInfix: Exact<SelectWhereStage<String, ScalarSelection<String, Required, String>>> = infixType
          val exactEntities: Exact<SelectFromStage<Person, EntitySelection<Person>>> = entitiesType
          val exactUsingOne: Exact<SelectFromStage<Person, EntitySelection<Person>>> = usingOneType
          val exactUsingMany: Exact<SelectFromStage<Person, EntitySelection<Person>>> = usingManyType
          val exactCrossed: Exact<SelectJoinStage<Long, ScalarSelection<Long, Required, Long>>> = crossedType
          val exactCrossedOn: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = crossedOnType
          val exactNatural: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = naturalType
          val exactNaturalLeft: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = naturalLeftType
          val exactNaturalCross: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = naturalCrossType
          val exactWithoutPets: Exact<SelectWhereStage<Person, EntitySelection<Person>>> = withoutPetsType
          val exactComputed:
              Exact<SelectFromStage<Number?, ScalarSelection<Number, SqlNullable, Number?>>> = computedType
          val exactChained:
              Exact<SelectOffsetStage<String?, ScalarSelection<String, SqlNullable, String?>>> = chainedType
          val exactRightJoined: Exact<SelectJoinStage<Row2<Long?, String>, RowSelection<Row2<Long?, String>>>> =
              rightJoinedType
          val exactRight: Exact<SelectFromStage<Row2<Long?, String>, RowSelection<Row2<Long?, String>>>> = rightType
          val exactFull: Exact<SelectFromStage<String?, ScalarSelection<String, SqlNullable, String?>>> = fullType
          val exactBareFull: Exact<SelectJoinStage<Person, EntitySelection<Person>>> = bareFullType
          val exactNaturalRight: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = naturalRightType
          val exactNaturalFull:
              Exact<SelectFromStage<Long?, ScalarSelection<Long, SqlNullable, Long?>>> = naturalFullType
          val exactMerged: Exact<SelectFromStage<Long, ScalarSelection<Long, Required, Long>>> = mergedType
          val exactDeep: Exact<SelectQuery<Person, EntitySelection<Person>>> = deepType
          val exactRows: Exact<List<Row2<String?, String?>>> = rowsType
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `joins outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileJoinConsumer(
      """
        fun verify(predicate: SqlExpression<Boolean, *, *>) = $program
      """
    ).assertCompilationError(diagnostic)
  }

  private fun compileJoinConsumer(body: String) = compileExpressionConsumer(
    body = body,
    imports = """
      import com.siimkinks.sqlitemagic.EntitySelection
      import com.siimkinks.sqlitemagic.NullableEncodableExpression
      import com.siimkinks.sqlitemagic.RowSelection
      import com.siimkinks.sqlitemagic.SelectJoinStage
      import com.siimkinks.sqlitemagic.SelectOffsetStage
      import com.siimkinks.sqlitemagic.SelectQuery
      import com.siimkinks.sqlitemagic.queryDeep
    """
  )

  companion object {
    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "lowercase table alias",
        """PERSON.`as`("owner")""",
        "Unresolved reference 'as'"
      ),
      Arguments.of(
        "join before FROM",
        "SELECT.COLUMN(PERSON.ID).LEFT_JOIN(PET)",
        "Unresolved reference 'LEFT_JOIN'"
      ),
      Arguments.of(
        "join after WHERE",
        "SELECT.FROM(PERSON).WHERE(predicate).INNER_JOIN(PET)",
        "Unresolved reference 'INNER_JOIN'"
      ),
      Arguments.of(
        "second ON",
        "SELECT.FROM(PERSON).LEFT_JOIN(PET).ON(predicate).ON(predicate)",
        "Unresolved reference 'ON'"
      ),
      Arguments.of(
        "USING after ON",
        "SELECT.FROM(PERSON).INNER_JOIN(PET).ON(predicate).USING(PET.ID)",
        "Unresolved reference 'USING'"
      ),
      Arguments.of(
        "ON with a non-Boolean expression",
        "SELECT.FROM(PERSON).INNER_JOIN(PET).ON(PET.ID)",
        "actual type is 'UniqueColumn<Pet, Long, Required, Long, Long, Required, Long>', " +
            "but 'SqlExpression<Boolean, *, *>' was expected"
      ),
      Arguments.of(
        "computed USING key",
        "SELECT.FROM(PERSON).INNER_JOIN(PET).USING(PET.ID + 1)",
        "but 'Column<*, *, *, *, *, *, *>' was expected"
      ),
      Arguments.of(
        "bare JOIN spelling",
        "SELECT.FROM(PERSON).JOIN(PET)",
        "Unresolved reference 'JOIN'"
      ),
      Arguments.of(
        "OUTER spelling",
        "SELECT.FROM(PERSON).LEFT_OUTER_JOIN(PET)",
        "Unresolved reference 'LEFT_OUTER_JOIN'"
      ),
      Arguments.of(
        "RIGHT OUTER spelling",
        "SELECT.FROM(PERSON).RIGHT_OUTER_JOIN(PET)",
        "Unresolved reference 'RIGHT_OUTER_JOIN'"
      ),
      Arguments.of(
        "FULL OUTER spelling",
        "SELECT.FROM(PERSON).FULL_OUTER_JOIN(PET)",
        "Unresolved reference 'FULL_OUTER_JOIN'"
      ),
      Arguments.of(
        "queryDeep on a scalar query",
        "SELECT.COLUMN(PERSON.ID).FROM(PERSON).queryDeep()",
        "Candidate 'fun <T> SelectQuery<T, EntitySelection<T>>.queryDeep(): SelectQuery<T, EntitySelection<T>>' " +
            "is inapplicable because of a receiver type mismatch"
      )
    )
  }
}
