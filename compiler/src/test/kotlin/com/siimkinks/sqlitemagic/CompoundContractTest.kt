package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class CompoundContractTest {
  @Test
  fun `compounds take the left arm's type and widen nullable scalar unions`() {
    PlainKotlinCompilation
      .compile(
        fileName = "CompoundConsumer.kt",
        contents = """
          package downstream.compound

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.EXCEPT
          import com.siimkinks.sqlitemagic.EntitySelection
          import com.siimkinks.sqlitemagic.INTERSECT
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.NullableEncodableExpression
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.Row2
          import com.siimkinks.sqlitemagic.RowSelection
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.ScalarSelection
          import com.siimkinks.sqlitemagic.SelectCompoundStage
          import com.siimkinks.sqlitemagic.SqlExpression
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UNION
          import com.siimkinks.sqlitemagic.UNION_ALL
          import com.siimkinks.sqlitemagic.UPPER
          import com.siimkinks.sqlitemagic.asScalar
          import com.siimkinks.sqlitemagic.mapTo
          import typewitness.Exact
          import typewitness.exactType

          class Person
          class Pet

          fun verify(
            person: Table<Person>,
            pet: Table<Pet>,
            personId: Column<Person, Long, Required, Long, Long, Required, Long>,
            personName: Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>,
            petId: Column<Pet, Long, Required, Long, Long, Required, Long>,
            petName: Column<Pet, String, Required, String, String, Required, String>
          ) {
            val people = SELECT.COLUMN(personName).FROM(person)
            val pets = SELECT.COLUMN(petName).FROM(pet)
            val union = people UNION pets
            val widened = pets UNION people
            val widenedAll = pets UNION_ALL people
            val kept = pets EXCEPT people
            val intersected = pets INTERSECT people
            val chained = people UNION pets EXCEPT pets UNION_ALL people
            val dotCall = people.UNION(pets)
            val personRows = SELECT.COLUMNS(personId, personName).FROM(person)
            val petRows = SELECT.COLUMNS(petId, petName).FROM(pet)
            val rows = personRows UNION petRows
            val liftedRows = SELECT.COLUMNS(petId, petName.nullable()).FROM(pet) UNION personRows
            val entities = SELECT.FROM(person) UNION (SELECT.FROM(person).WHERE(personId IS 1L))
            val ordered = (people UNION pets).ORDER_BY(personName.ASC())
            val paged = (people UNION pets)
                .ORDER_BY(personName.ASC())
                .LIMIT(2)
                .OFFSET(1)
            val mapped = (people UNION pets).mapTo { it?.length }
            val nested = (people UNION pets).asScalar()
            val liftedField = petName.nullable()
            val liftedComputed = UPPER(petName).nullable()
            val liftedPredicate = (personId IS 1L).nullable()

            val unionType = union.exactType()
            val exactUnion: Exact<SelectCompoundStage<String?, ScalarSelection<String, SqlNullable, String?>>> =
                unionType
            val widenedType = widened.exactType()
            val exactWidened: Exact<SelectCompoundStage<String?, ScalarSelection<String, SqlNullable, String?>>> =
                widenedType
            val widenedAllType = widenedAll.exactType()
            val exactWidenedAll: Exact<SelectCompoundStage<String?, ScalarSelection<String, SqlNullable, String?>>> =
                widenedAllType
            val keptType = kept.exactType()
            val exactKept: Exact<SelectCompoundStage<String, ScalarSelection<String, Required, String>>> =
                keptType
            val intersectedType = intersected.exactType()
            val exactIntersected: Exact<SelectCompoundStage<String, ScalarSelection<String, Required, String>>> =
                intersectedType
            val chainedType = chained.exactType()
            val exactChained: Exact<SelectCompoundStage<String?, ScalarSelection<String, SqlNullable, String?>>> =
                chainedType
            val rowsType = rows.exactType()
            val exactRows: Exact<SelectCompoundStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                rowsType
            val liftedRowsType = liftedRows.exactType()
            val exactLiftedRows: Exact<SelectCompoundStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>> =
                liftedRowsType
            val entitiesType = entities.exactType()
            val exactEntities: Exact<SelectCompoundStage<Person, EntitySelection<Person>>> = entitiesType
            val nestedType = nested.exactType()
            val exactNested: Exact<SqlExpression<String, SqlNullable, String?>> = nestedType
            val liftedFieldType = liftedField.exactType()
            val exactLiftedField: Exact<NullableEncodableExpression<String, String, String>> = liftedFieldType
            val liftedComputedType = liftedComputed.exactType()
            val exactLiftedComputed: Exact<NullableEncodableExpression<String, String, String>> = liftedComputedType
            val liftedPredicateType = liftedPredicate.exactType()
            val exactLiftedPredicate: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> =
                liftedPredicateType
            val results: List<List<Any?>> = listOf(
              dotCall.execute(),
              ordered.execute(),
              paged.execute(),
              mapped.execute()
            )
          }
        """
      )
      .isOk()
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "row arm with a nullable column on the right | val invalid = petRows UNION personRows | " +
          "actual type is 'SelectFromStage<Row2<Long, String?>, RowSelection<Row2<Long, String?>>>', " +
          "but 'SelectQuery<out Row2<Long, String>, *>' was expected",
      "arms of different value types | val invalid = ids UNION pets | " +
          "actual type is 'SelectFromStage<String, ScalarSelection<String, Required, String>>'",
      "required scalar arm before a nullable one of another type | val invalid = ids UNION people | " +
          "actual type is 'SelectFromStage<String?, ScalarSelection<String, SqlNullable, String?>>'",
      "scalar and row arms | val invalid = pets UNION petRows | " +
          "actual type is 'SelectFromStage<Row2<Long, String>, RowSelection<Row2<Long, String>>>'",
      "WHERE after a compound | val invalid = (people UNION pets).WHERE(personId IS 1L) | " +
          "Unresolved reference 'WHERE' on receiver of type 'SelectCompoundStage",
      "OFFSET without LIMIT | val invalid = (people UNION pets).OFFSET(1) | " +
          "Unresolved reference 'OFFSET' on receiver of type 'SelectCompoundStage"
    ]
  )
  fun `compounds outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        fileName = "RejectedCompound.kt",
        contents = """
          package downstream.compound

          import com.siimkinks.sqlitemagic.Column
          import com.siimkinks.sqlitemagic.IS
          import com.siimkinks.sqlitemagic.Required
          import com.siimkinks.sqlitemagic.SELECT
          import com.siimkinks.sqlitemagic.SqlNullable
          import com.siimkinks.sqlitemagic.Table
          import com.siimkinks.sqlitemagic.UNION

          class Person
          class Pet

          fun verify(
            person: Table<Person>,
            pet: Table<Pet>,
            personId: Column<Person, Long, Required, Long, Long, Required, Long>,
            personName: Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>,
            petId: Column<Pet, Long, Required, Long, Long, Required, Long>,
            petName: Column<Pet, String, Required, String, String, Required, String>
          ) {
            val people = SELECT.COLUMN(personName).FROM(person)
            val pets = SELECT.COLUMN(petName).FROM(pet)
            val ids = SELECT.COLUMN(petId).FROM(pet)
            val personRows = SELECT.COLUMNS(personId, personName).FROM(person)
            val petRows = SELECT.COLUMNS(petId, petName).FROM(pet)
            $program
          }
        """
      )
      .assertCompilationError(diagnostic)
  }
}
