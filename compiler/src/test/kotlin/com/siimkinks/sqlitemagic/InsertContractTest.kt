package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class InsertContractTest {
  @Test
  fun `INSERT statements infer their stages, rows and terminals`() {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection, names: List<String?>, ages: List<Int>) {
          val target = INSERT INTO PERSON
          val conflict = INSERT OR CONFLICT_IGNORE INTO PERSON
          val columns = INSERT INTO PERSON COLUMNS PERSON.NAME
          val named = INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada"
          val nulled = INSERT INTO PERSON COLUMNS PERSON.NAME VALUES null
          val listed = INSERT INTO PERSON COLUMNS PERSON.NAME VALUES names.map(::Row1)
          val encodedNull = INSERT INTO PERSON COLUMNS PERSON.DECODED VALUES null
          val fluent = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.TOKEN)
              .VALUES("Ada", 36, Token("a"))
              .VALUES(null, 17, Token("b"))
          val fluentList = INSERT
              .OR(CONFLICT_REPLACE)
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .VALUES(names.zip(ages, ::Row2))
          val infixList = INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE) VALUES listOf(Row2("Ada", 36))
          val age = PERSON.AGE.asParameter("age")
          val parentId = PERSON.PARENT_ID.asParameter("parent")
          val scalar = SELECT
              .COLUMN(PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 1L)
              .asScalar()
          val expressions = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.PARENT_ID, PERSON.TOKEN)
              .VALUES("Ada".asValue(), age, parentId, Token("t").asValue(TOKEN_TYPE))
              .VALUES(PERSON.NAME.asParameter("name"), scalar, 1L.asValue(), "?".expr(TOKEN_TYPE, "db:t"))
          val required = INSERT INTO PERSON COLUMNS PERSON.ID VALUES PERSON.PARENT_ID.asParameter("id")
          val counted = INSERT INTO PERSON COLUMNS PERSON.AGE VALUES (36.asValue() + 1).readAs(SqlType.INT)
          val petCount = SELECT
              .COLUMN(COUNT())
              .FROM(PET)
              .asScalar()
              .readAs(SqlType.INT)
          val countedRows = INSERT INTO PERSON COLUMNS PERSON.AGE VALUES petCount
          val defaults = INSERT
              .INTO(PERSON)
              .DEFAULT_VALUES()
          val pets = INSERT
              .INTO(PET)
              .COLUMNS(PET.ID, PET.OWNER, PET.NAME, PET.NICKNAME)
              .VALUES(1L, 2L, "Rex", null)
          val affected = fluent
              .usingConnection(connection)
              .execute()
          val bound = expressions.bind(age TO 36, parentId TO null)
          val compiled = bound.compile()
          val compiledAffected = compiled.execute()
          val observed = listed.observe()
          val raw = INSERT
              .RAW("INSERT INTO person (name) VALUES (?)", "Ada")
              .notifies(PERSON)
          val infixRaw = INSERT RAW "INSERT INTO person DEFAULT VALUES"

          val targetType = target.exactType()
          val conflictType = conflict.exactType()
          val columnsType = columns.exactType()
          val namedType = named.exactType()
          val nulledType = nulled.exactType()
          val listedType = listed.exactType()
          val encodedNullType = encodedNull.exactType()
          val fluentType = fluent.exactType()
          val fluentListType = fluentList.exactType()
          val infixListType = infixList.exactType()
          val expressionsType = expressions.exactType()
          val requiredType = required.exactType()
          val countedType = counted.exactType()
          val countedRowsType = countedRows.exactType()
          val defaultsType = defaults.exactType()
          val petsType = pets.exactType()
          val affectedType = affected.exactType()
          val boundType = bound.exactType()
          val compiledType = compiled.exactType()
          val compiledAffectedType = compiledAffected.exactType()
          val observedType = observed.exactType()
          val rawType = raw.exactType()
          val infixRawType = infixRaw.exactType()
          val exactTarget: Exact<InsertIntoStage<Person>> = targetType
          val exactConflict: Exact<InsertIntoStage<Person>> = conflictType
          val exactColumns: Exact<InsertColumns1Stage<Person, String, String?>> = columnsType
          val exactNamed: Exact<InsertValues1Stage<Person, String, String?>> = namedType
          val exactNulled: Exact<InsertValues1Stage<Person, String, String?>> = nulledType
          val exactListed: Exact<InsertValues1Stage<Person, String, String?>> = listedType
          val exactEncodedNull: Exact<InsertValues1Stage<Person, Token?, Token?>> = encodedNullType
          val exactFluent: Exact<InsertValues3Stage<Person, String, String?, Int, Int, Token, Token>> = fluentType
          val exactFluentList: Exact<InsertValues2Stage<Person, String, String?, Int, Int>> = fluentListType
          val exactInfixList: Exact<InsertValues2Stage<Person, String, String?, Int, Int>> = infixListType
          val exactExpressions: Exact<
              InsertValues4Stage<Person, String, String?, Int, Int, Long, Long?, Token, Token>
          > = expressionsType
          val exactRequired: Exact<InsertValues1Stage<Person, Long, Long>> = requiredType
          val exactCounted: Exact<InsertValues1Stage<Person, Int, Int>> = countedType
          val exactCountedRows: Exact<InsertValues1Stage<Person, Int, Int>> = countedRowsType
          val exactDefaults: Exact<MutationQuery> = defaultsType
          val exactPets: Exact<InsertValues4Stage<Pet, Long, Long, Long, Long, String, String, String, String?>> =
              petsType
          val exactAffected: Exact<Int> = affectedType
          val exactBound: Exact<MutationQuery> = boundType
          val exactCompiled: Exact<CompiledMutation> = compiledType
          val exactCompiledAffected: Exact<Int> = compiledAffectedType
          val exactObserved: Exact<Single<Int>> = observedType
          val exactRaw: Exact<RawMutationQuery> = rawType
          val exactInfixRaw: Exact<RawMutationQuery> = infixRawType
        }
      """
    ).isOk()
  }

  @Test
  fun `wide INSERT statements infer their stages, row assignments and terminals`() {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection, names: List<String?>) {
          val columns = INSERT
              .INTO(PERSON)
              .COLUMNS($WIDE_COLUMNS)
          val spread = INSERT
              .OR(CONFLICT_REPLACE)
              .INTO(PERSON)
              .COLUMNS(*arrayOf(PERSON.NAME, PERSON.AGE))
          val scalar = SELECT
              .COLUMN(PERSON.AGE)
              .FROM(PERSON)
              .WHERE(PERSON.ID IS 1L)
              .asScalar()
          val parentId = PERSON.PARENT_ID.asParameter("parent")
          val row = columns.VALUES {
            it[PERSON.ID] = 1L
            it[PERSON.NAME] = null
            it[PERSON.AGE] = 36
            it[PERSON.PARENT_ID] = null
            it[PERSON.TOKEN] = Token("a")
            it[PERSON.DECODED] = null
            it[PERSON.ID] = PERSON.PARENT_ID.asParameter("id")
            it[PERSON.AGE] = scalar
            it[PERSON.AGE] = (36.asValue() + 1).readAs(SqlType.INT)
            it[PERSON.PARENT_ID] = parentId
            it[PERSON.TOKEN] = "?".expr(TOKEN_TYPE, "db:t")
            val rowType = it.exactType()
            val exactRow: Exact<InsertedRow<Person>> = rowType
          }
          val infix = spread VALUES { it[PERSON.NAME] = "Ada" } VALUES { it[PERSON.AGE] = 36 }
          val listed = spread.VALUES(names) { row, name ->
            row[PERSON.NAME] = name
            row[PERSON.AGE] = 36
          }
          val affected = row
              .bind(parentId TO null)
              .usingConnection(connection)
              .execute()
          val observed = listed.observe()

          val columnsType = columns.exactType()
          val spreadType = spread.exactType()
          val rowType = row.exactType()
          val infixType = infix.exactType()
          val listedType = listed.exactType()
          val affectedType = affected.exactType()
          val observedType = observed.exactType()
          val exactColumns: Exact<InsertColumnsStage<Person>> = columnsType
          val exactSpread: Exact<InsertColumnsStage<Person>> = spreadType
          val exactRow: Exact<InsertValuesStage<Person>> = rowType
          val exactInfix: Exact<InsertValuesStage<Person>> = infixType
          val exactListed: Exact<InsertValuesStage<Person>> = listedType
          val exactAffected: Exact<Int> = affectedType
          val exactObserved: Exact<Single<Int>> = observedType
        }
      """
    ).isOk()
  }

  @Test
  fun `expression readAs keeps nullability and reads the given type`() {
    compileInsertConsumer(
      """
        fun verify() {
          val required = (PERSON.AGE + 1).readAs(SqlType.INT)
          val nullable = SUM(PERSON.AGE).readAs(SqlType.INT)
          val widened = COUNT().readAs(SqlType.INT.nullable)
          val raw = (PERSON.AGE + 1).readAs(SqlType.NUMBER)

          val requiredType = required.exactType()
          val nullableType = nullable.exactType()
          val widenedType = widened.exactType()
          val rawType = raw.exactType()
          val exactRequired: Exact<EncodableSqlExpression<Int, Int, Required, Int>> = requiredType
          val exactNullable: Exact<EncodableSqlExpression<Int, Int, SqlNullable, Int?>> = nullableType
          val exactWidened: Exact<EncodableSqlExpression<Int, Int, SqlNullable, Int?>> = widenedType
          val exactRaw: Exact<SqlExpression<Number, Required, Number>> = rawType
          val compared = required GREATER_THAN 40
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `INSERT outside its contracts does not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection) = $program
      """
    ).assertCompilationError(diagnostic)
  }

  private fun compileInsertConsumer(body: String) = compileExpressionConsumer(
    body = body,
    imports = """
      import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
      import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
      import com.siimkinks.sqlitemagic.COUNT
      import com.siimkinks.sqlitemagic.CompiledMutation
      import com.siimkinks.sqlitemagic.INSERT
      import com.siimkinks.sqlitemagic.InsertColumns1Stage
      import com.siimkinks.sqlitemagic.InsertColumnsStage
      import com.siimkinks.sqlitemagic.InsertIntoStage
      import com.siimkinks.sqlitemagic.InsertValues1Stage
      import com.siimkinks.sqlitemagic.InsertValues2Stage
      import com.siimkinks.sqlitemagic.InsertValues3Stage
      import com.siimkinks.sqlitemagic.InsertValues4Stage
      import com.siimkinks.sqlitemagic.InsertValuesStage
      import com.siimkinks.sqlitemagic.InsertedRow
      import com.siimkinks.sqlitemagic.MutationQuery
      import com.siimkinks.sqlitemagic.RawMutationQuery
      import com.siimkinks.sqlitemagic.Row1
      import com.siimkinks.sqlitemagic.Row3
      import com.siimkinks.sqlitemagic.SUM
      import com.siimkinks.sqlitemagic.asScalar
      import com.siimkinks.sqlitemagic.rxjava2.observe
      import io.reactivex.Single
    """
  )

  companion object {
    private const val WIDE_COLUMNS = "PERSON.ID, PERSON.NAME, PERSON.AGE, PERSON.PARENT_ID, PERSON.TOKEN, " +
        "PERSON.DECODED, PERSON.ID, PERSON.NAME, PERSON.AGE, PERSON.PARENT_ID"

    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "column of another table",
        "INSERT INTO PERSON COLUMNS PET.NAME",
        "actual type is 'Column<Pet"
      ),
      Arguments.of(
        "fluent columns of another table",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PET.NAME)",
        "actual type is 'Column<Pet"
      ),
      Arguments.of(
        "too few values",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).VALUES("Ada")""",
        "actual type is 'String', but 'List<Row2<String?, Int>>' was expected"
      ),
      Arguments.of(
        "too many values",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).VALUES("Ada", 36, 1L)""",
        "Too many arguments for 'fun VALUES(first: String?, second: Int)"
      ),
      Arguments.of(
        "list rows of another arity",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).VALUES(listOf(Row3("Ada", 36, 1L)))""",
        "actual type is 'List<Row3<String, Int, Long>>', but 'List<Row2<String?, Int>>' was expected"
      ),
      Arguments.of(
        "plain list for one column",
        """INSERT INTO PERSON COLUMNS PERSON.NAME VALUES listOf("Ada")""",
        "actual type is 'List<String>', but 'List<Row1<String?>>' was expected"
      ),
      Arguments.of(
        "expression of another value type",
        """INSERT INTO PERSON COLUMNS PERSON.AGE VALUES PERSON.NAME.asParameter("name")""",
        "'SqlParameter<String?, String, SqlNullable, String?>', but 'SqlExpression<Int?, *, *>' was expected"
      ),
      Arguments.of(
        "wider number without readAs",
        "INSERT INTO PERSON COLUMNS PERSON.AGE VALUES (36.asValue() + 1)",
        "'EncodableSqlExpression<Number, Number, Required, Number>', but 'SqlExpression<Int?, *, *>' was expected"
      ),
      Arguments.of(
        "host value of another type",
        """INSERT INTO PERSON COLUMNS PERSON.AGE VALUES "36"""",
        "actual type is 'String', but 'Int' was expected"
      ),
      Arguments.of(
        "null into NOT NULL",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.AGE, PERSON.NAME).VALUES(null, null)",
        "Null cannot be a value of a non-null type 'Int'"
      ),
      Arguments.of(
        "host values and expressions in one row",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).VALUES("Ada", 36.asValue())""",
        "actual type is 'EncodedSqlExpression<Int, Int, Required, Int>', but 'Int' was expected"
      ),
      Arguments.of(
        "execute before VALUES",
        "(INSERT INTO PERSON COLUMNS PERSON.NAME).execute()",
        "Unresolved reference 'execute' on receiver of type 'InsertColumns1Stage<Person, String, String?>'"
      ),
      Arguments.of(
        "execute without columns",
        "(INSERT INTO PERSON).execute()",
        "Unresolved reference 'execute' on receiver of type 'InsertIntoStage<Person>'"
      ),
      Arguments.of(
        "VALUES after DEFAULT VALUES",
        """(INSERT INTO PERSON).DEFAULT_VALUES().VALUES("Ada")""",
        "Unresolved reference 'VALUES' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "wide columns of another table",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS, PET.NAME)",
        "actual type is 'Column<Pet, String, Required, String, String, Required, String>', but " +
            "'Column<Person, *, *, *, *, *, *>' was expected"
      ),
      Arguments.of(
        "wide row column of another table",
        """INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PET.NAME] = "Rex" }""",
        "actual type is 'Column<Pet, String, Required, String, String, Required, String>', but " +
            "'Column<Person, uninferred R (of fun <R> set), *, *, *, *, *>' was expected"
      ),
      Arguments.of(
        "wide host value of another type",
        """INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PERSON.AGE] = "36" }""",
        "actual type is 'String', but 'Int' was expected"
      ),
      Arguments.of(
        "wide expression of another value type",
        """INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PERSON.AGE] = PERSON.NAME.asParameter("n") }""",
        "actual type is 'SqlParameter<String?, String, SqlNullable, String?>', but " +
            "'SqlExpression<uninferred R (of fun <R> set), *, *>' was expected"
      ),
      Arguments.of(
        "wide wider number without readAs",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PERSON.AGE] = 36.asValue() + 1 }",
        "actual type is 'EncodableSqlExpression<Number, Number, Required, Number>', but " +
            "'SqlExpression<uninferred R (of fun <R> set), *, *>' was expected"
      ),
      Arguments.of(
        "wide null into NOT NULL",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PERSON.AGE] = null }",
        "Null cannot be a value of a non-null type 'uninferred W (of fun <W> set)'"
      ),
      Arguments.of(
        "execute before wide VALUES",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).execute()",
        "Unresolved reference 'execute' on receiver of type 'InsertColumnsStage<Person>'"
      ),
      Arguments.of(
        "raw table name",
        """INSERT INTO "person"""",
        "Table<uninferred P (of fun <P> INTO)>"
      )
    )
  }
}
