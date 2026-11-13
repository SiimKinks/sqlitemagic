package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class MutationContractTest {
  @Test
  fun `UPDATE and DELETE statements infer their stages, values and terminals`() {
    compileMutationConsumer(
      """
        fun verify(connection: DbConnection, name: String?, age: Int) {
          val target = UPDATE TABLE PERSON
          val renamed = (UPDATE TABLE PERSON SET (PERSON.NAME to "Ada")) WHERE (PERSON.ID IS 7L)
          val fluent = UPDATE.TABLE(PERSON)
              .SET(PERSON.AGE, age)
              .SET(PERSON.NAME, name)
              .WHERE(PERSON.ID IS 7L)
          val counted = UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE + 1)
          val fluentCounted = UPDATE.TABLE(PERSON)
              .SET(PERSON.AGE, PERSON.AGE + 1)
          val nulled = UPDATE TABLE PERSON SET (PERSON.NAME to null)
          val fluentNulled = UPDATE.TABLE(PERSON)
              .SET(PERSON.NAME, null)
          val encodedNull = UPDATE TABLE PERSON SET (PERSON.DECODED to null)
          val explicitNull = UPDATE OR CONFLICT_REPLACE TABLE PERSON SET_NULL PERSON.AGE
          val fluentExplicitNull = UPDATE.TABLE(PERSON).SET_NULL(PERSON.AGE)
          val chained = UPDATE TABLE PERSON SET (PERSON.NAME to "Ada") SET_NULL PERSON.PARENT_ID SET (PERSON.AGE to 18)
          val nullableTarget = UPDATE TABLE PERSON SET (PERSON.PARENT_ID to PERSON.AGE / 2)
          val widened = UPDATE TABLE PERSON SET (PERSON.ID to PERSON.AGE)
          val sameEncoding = UPDATE TABLE PERSON SET (PERSON.TOKEN to PERSON.TOKEN)
          val customValue = UPDATE TABLE PERSON SET (PERSON.TOKEN to Token("t"))
          val parameter = PERSON.NAME.asParameter("name")
          val parameterized = (UPDATE TABLE PERSON SET (PERSON.NAME to parameter)) WHERE (PERSON.ID IS 1L)
          val rawValue = UPDATE TABLE PERSON SET (PERSON.AGE to "abs(age)".expr(SqlType.INT))
          val conflict = UPDATE OR CONFLICT_IGNORE TABLE PERSON SET (PERSON.ID to 8L)
          val fluentConflict = UPDATE.OR(CONFLICT_IGNORE)
              .TABLE(PERSON)
              .SET(PERSON.ID, 8L)
          val deleted = DELETE FROM PERSON
          val filtered = (DELETE FROM PERSON) WHERE (PERSON.AGE LESS_THAN 18)
          val fluentDelete = DELETE.FROM(PERSON).WHERE(PERSON.AGE LESS_THAN 18)
          val rawDelete = (DELETE FROM PERSON) WHERE ("age < ?".expr(SqlType.BOOLEAN, 18))
          val connected = renamed.usingConnection(connection)
          val affected = connected.execute()
          val bound = parameterized.bind(parameter TO "Ada")
          val compiled = bound.compile()
          val compiledAffected = compiled.execute()
          val observed = deleted.usingConnection(connection).observe()
          val compiledObserved = compiled.observe()

          val targetType = target.exactType()
          val renamedType = renamed.exactType()
          val fluentType = fluent.exactType()
          val countedType = counted.exactType()
          val chainedType = chained.exactType()
          val deletedType = deleted.exactType()
          val filteredType = filtered.exactType()
          val connectedType = connected.exactType()
          val boundType = bound.exactType()
          val compiledType = compiled.exactType()
          val affectedType = affected.exactType()
          val compiledAffectedType = compiledAffected.exactType()
          val observedType = observed.exactType()
          val compiledObservedType = compiledObserved.exactType()
          val exactTarget: Exact<UpdateTableStage<Person>> = targetType
          val exactRenamed: Exact<MutationQuery> = renamedType
          val exactFluent: Exact<MutationQuery> = fluentType
          val exactCounted: Exact<UpdateSetStage<Person>> = countedType
          val exactChained: Exact<UpdateSetStage<Person>> = chainedType
          val exactDeleted: Exact<DeleteFromStage> = deletedType
          val exactFiltered: Exact<MutationQuery> = filteredType
          val exactConnected: Exact<MutationQuery> = connectedType
          val exactBound: Exact<MutationQuery> = boundType
          val exactCompiled: Exact<CompiledMutation> = compiledType
          val exactAffected: Exact<Int> = affectedType
          val exactCompiledAffected: Exact<Int> = compiledAffectedType
          val exactObserved: Exact<Single<Int>> = observedType
          val exactCompiledObserved: Exact<Single<Int>> = compiledObservedType
          val sql: String = compiled.sql
        }
      """
    ).isOk()
  }

  @Test
  fun `UPDATE SET overloads return the UPDATE SET stage, fluent and infix`() {
    compileMutationConsumer(
      """
        fun verify(name: String?) {
          val target = UPDATE TABLE PERSON
          val hostValue = target.SET(PERSON.NAME, name)
          val hostValuePair = target SET (PERSON.NAME to name)
          val required = target.SET(PERSON.AGE, PERSON.AGE + 1)
          val requiredPair = target SET (PERSON.AGE to PERSON.AGE + 1)
          val nullable = target.SET(PERSON.NAME, PERSON.NAME)
          val nullablePair = target SET (PERSON.NAME to PERSON.NAME)
          val requiredNumber = target.SET(PERSON.ID, PERSON.AGE)
          val requiredNumberPair = target SET (PERSON.ID to PERSON.AGE)
          val nullableNumber = target.SET(PERSON.PARENT_ID, PERSON.AGE / 2)
          val nullableNumberPair = target SET (PERSON.PARENT_ID to PERSON.AGE / 2)
          val nulled = target.SET_NULL(PERSON.PARENT_ID)
          val nulledInfix = target SET_NULL PERSON.PARENT_ID
          val chained = hostValue
              .SET(PERSON.AGE, PERSON.AGE + 1)
              .SET(PERSON.ID, PERSON.AGE)
              .SET_NULL(PERSON.PARENT_ID)
          val chainedInfix = hostValue SET (PERSON.ID to PERSON.AGE) SET_NULL PERSON.PARENT_ID
          val widenedStage: UpdateTableStage<Person> = chained

          val hostValueType = hostValue.exactType()
          val hostValuePairType = hostValuePair.exactType()
          val requiredType = required.exactType()
          val requiredPairType = requiredPair.exactType()
          val nullableType = nullable.exactType()
          val nullablePairType = nullablePair.exactType()
          val requiredNumberType = requiredNumber.exactType()
          val requiredNumberPairType = requiredNumberPair.exactType()
          val nullableNumberType = nullableNumber.exactType()
          val nullableNumberPairType = nullableNumberPair.exactType()
          val nulledType = nulled.exactType()
          val nulledInfixType = nulledInfix.exactType()
          val chainedType = chained.exactType()
          val chainedInfixType = chainedInfix.exactType()
          val exactHostValue: Exact<UpdateSetStage<Person>> = hostValueType
          val exactHostValuePair: Exact<UpdateSetStage<Person>> = hostValuePairType
          val exactRequired: Exact<UpdateSetStage<Person>> = requiredType
          val exactRequiredPair: Exact<UpdateSetStage<Person>> = requiredPairType
          val exactNullable: Exact<UpdateSetStage<Person>> = nullableType
          val exactNullablePair: Exact<UpdateSetStage<Person>> = nullablePairType
          val exactRequiredNumber: Exact<UpdateSetStage<Person>> = requiredNumberType
          val exactRequiredNumberPair: Exact<UpdateSetStage<Person>> = requiredNumberPairType
          val exactNullableNumber: Exact<UpdateSetStage<Person>> = nullableNumberType
          val exactNullableNumberPair: Exact<UpdateSetStage<Person>> = nullableNumberPairType
          val exactNulled: Exact<UpdateSetStage<Person>> = nulledType
          val exactNulledInfix: Exact<UpdateSetStage<Person>> = nulledInfixType
          val exactChained: Exact<UpdateSetStage<Person>> = chainedType
          val exactChainedInfix: Exact<UpdateSetStage<Person>> = chainedInfixType
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `UPDATE and DELETE outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileMutationConsumer(
      """
        fun verify(
          predicate: SqlExpression<Boolean, *, *>,
          connection: DbConnection
        ) = $program
      """
    ).assertCompilationError(diagnostic)
  }

  private fun compileMutationConsumer(body: String) = compileExpressionConsumer(
    body = body,
    imports = """
      import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
      import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
      import com.siimkinks.sqlitemagic.AssignmentStage
      import com.siimkinks.sqlitemagic.CompiledMutation
      import com.siimkinks.sqlitemagic.DELETE
      import com.siimkinks.sqlitemagic.DeleteFromStage
      import com.siimkinks.sqlitemagic.MutationQuery
      import com.siimkinks.sqlitemagic.SET
      import com.siimkinks.sqlitemagic.UPDATE
      import com.siimkinks.sqlitemagic.UpdateSetStage
      import com.siimkinks.sqlitemagic.UpdateTableStage
      import com.siimkinks.sqlitemagic.rxjava2.observe
      import io.reactivex.Single
    """
  )

  companion object {
    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "target of another model",
        """UPDATE TABLE PERSON SET (PET.NAME to "Rex")""",
        "actual type is 'Pair<Column<Pet"
      ),
      Arguments.of(
        "fluent target of another model",
        """UPDATE.TABLE(PERSON).SET(PET.NAME, "Rex")""",
        "actual type is 'Column<Pet"
      ),
      Arguments.of(
        "UPDATE without SET",
        "(UPDATE TABLE PERSON).execute()",
        "Unresolved reference 'execute' on receiver of type 'UpdateTableStage<Person>'"
      ),
      Arguments.of(
        "WHERE before SET",
        "UPDATE TABLE PERSON WHERE (PERSON.ID IS 1L)",
        "Unresolved reference 'WHERE' on receiver of type 'UpdateTableStage<Person>'"
      ),
      Arguments.of(
        "SET after WHERE",
        "((UPDATE TABLE PERSON SET (PERSON.AGE to 1)) WHERE predicate) SET (PERSON.AGE to 2)",
        "Candidate 'fun <P, S> AssignmentStage<P, S>.SET("
      ),
      Arguments.of(
        "assignment stage subclass",
        "object : AssignmentStage<Person, Int>() {}",
        "Anonymous object cannot extend a sealed class"
      ),
      Arguments.of(
        "second UPDATE WHERE",
        "((UPDATE TABLE PERSON SET (PERSON.AGE to 1)) WHERE predicate) WHERE predicate",
        "Unresolved reference 'WHERE' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "clause after connection",
        "(DELETE FROM PERSON).usingConnection(connection).WHERE(predicate)",
        "Unresolved reference 'WHERE' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "null into NOT NULL",
        "UPDATE TABLE PERSON SET (PERSON.AGE to null)",
        "Int, Required, Int>, Nothing?>'"
      ),
      Arguments.of(
        "fluent null into NOT NULL",
        "UPDATE.TABLE(PERSON).SET(PERSON.AGE, null)",
        "Null cannot be a value of a non-null type"
      ),
      Arguments.of(
        "nullable division into NOT NULL",
        "UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.AGE / 2)",
        "Int>, EncodableSqlExpression<Number, Number, SqlNullable, Number?>>'"
      ),
      Arguments.of(
        "nullable field into NOT NULL",
        "UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.PARENT_ID)",
        "Int>, Column<Person, Long, SqlNullable"
      ),
      Arguments.of(
        "text into number",
        "UPDATE TABLE PERSON SET (PERSON.AGE to PERSON.NAME)",
        "Int>, Column<Person, String, SqlNullable"
      ),
      Arguments.of(
        "host text into number",
        """UPDATE TABLE PERSON SET (PERSON.AGE to "36")""",
        "Int, Required, Int>, String>'"
      ),
      Arguments.of(
        "other encoding",
        "UPDATE TABLE PERSON SET (PERSON.TOKEN to PERSON.NAME)",
        "Token>, Column<Person, String, SqlNullable"
      ),
      Arguments.of(
        "raw table name",
        """UPDATE TABLE "person"""",
        "Table<uninferred P (of fun <P> TABLE)>"
      ),
      Arguments.of(
        "raw DELETE table name",
        """DELETE FROM "person"""",
        "Table<uninferred P (of fun <P> FROM)>"
      ),
      Arguments.of(
        "raw WHERE pair",
        """(DELETE FROM PERSON) WHERE ("age < ?" to arrayOf(18))""",
        "actual type is 'Pair<String, Array<Int>>', but 'SqlExpression<Boolean, *, *>' was expected"
      ),
      Arguments.of(
        "legacy conflict spelling",
        "UPDATE WITH_CONFLICT_ALGORITHM CONFLICT_IGNORE TABLE PERSON",
        "Unresolved reference 'WITH_CONFLICT_ALGORITHM' on receiver of type 'Update'"
      ),
      Arguments.of(
        "conflict on DELETE",
        "DELETE OR CONFLICT_IGNORE FROM PERSON",
        "OR(other: SqlExpression<Boolean, *, *>): SqlExpression<Boolean, SqlNullable, Boolean?>' is inapplicable"
      )
    )
  }
}
