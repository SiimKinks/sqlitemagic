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
          val exactNamed: Exact<InsertSingleRow1Stage<Person, String, String?>> = namedType
          val exactNulled: Exact<InsertSingleRow1Stage<Person, String, String?>> = nulledType
          val exactListed: Exact<InsertValues1Stage<Person, String, String?>> = listedType
          val exactEncodedNull: Exact<InsertSingleRow1Stage<Person, Token?, Token?>> = encodedNullType
          val exactFluent: Exact<InsertValues3Stage<Person, String, String?, Int, Int, Token, Token>> = fluentType
          val exactFluentList: Exact<InsertValues2Stage<Person, String, String?, Int, Int>> = fluentListType
          val exactInfixList: Exact<InsertValues2Stage<Person, String, String?, Int, Int>> = infixListType
          val exactExpressions: Exact<
              InsertValues4Stage<Person, String, String?, Int, Int, Long, Long?, Token, Token>
          > = expressionsType
          val exactRequired: Exact<InsertSingleRow1Stage<Person, Long, Long>> = requiredType
          val exactCounted: Exact<InsertSingleRow1Stage<Person, Int, Int>> = countedType
          val exactCountedRows: Exact<InsertSingleRow1Stage<Person, Int, Int>> = countedRowsType
          val exactDefaults: Exact<InsertDefaultValuesStage> = defaultsType
          val exactPets: Exact<InsertSingleRow4Stage<Pet, Long, Long, Long, Long, String, String, String, String?>> =
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
          val exactRow: Exact<InsertSingleRowStage<Person>> = rowType
          val exactInfix: Exact<InsertValuesStage<Person>> = infixType
          val exactListed: Exact<InsertValuesStage<Person>> = listedType
          val exactAffected: Exact<Int> = affectedType
          val exactObserved: Exact<Single<Int>> = observedType
        }
      """
    ).isOk()
  }

  @Test
  fun `readRowId reads the row ID of single-row statements`() {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection) {
          val name = PERSON.NAME.asParameter("name")
          val single = (INSERT INTO PERSON COLUMNS PERSON.NAME VALUES name).readRowId()
          val expressions = INSERT
              .OR(CONFLICT_IGNORE)
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .VALUES("Ada".asValue(), 36.asValue())
              .readRowId()
          val nine = INSERT
              .INTO(PERSON)
              .COLUMNS(
                PERSON.ID,
                PERSON.NAME,
                PERSON.AGE,
                PERSON.PARENT_ID,
                PERSON.TOKEN,
                PERSON.DECODED,
                PERSON.ID,
                PERSON.NAME,
                PERSON.AGE
              )
              .VALUES(1L, "Ada", 36, null, Token("a"), null, 2L, "Bob", 17)
              .readRowId()
          val defaults = INSERT
              .INTO(PERSON)
              .DEFAULT_VALUES()
              .readRowId()
          val wide = INSERT
              .OR(CONFLICT_REPLACE)
              .INTO(PERSON)
              .COLUMNS($WIDE_COLUMNS)
              .VALUES { it[PERSON.NAME] = "Ada" }
              .readRowId()
          val bound = single
              .bind(name TO "Ada")
              .usingConnection(connection)
          val rowId = bound.execute()
          val compiled = bound.compile()
          val compiledRowId = compiled.execute()
          val observed = single.observe()
          val compiledObserved = compiled.observe()

          val singleType = single.exactType()
          val expressionsType = expressions.exactType()
          val nineType = nine.exactType()
          val defaultsType = defaults.exactType()
          val wideType = wide.exactType()
          val boundType = bound.exactType()
          val rowIdType = rowId.exactType()
          val compiledType = compiled.exactType()
          val compiledRowIdType = compiledRowId.exactType()
          val observedType = observed.exactType()
          val compiledObservedType = compiledObserved.exactType()
          val exactSingle: Exact<RowIdInsertQuery> = singleType
          val exactExpressions: Exact<RowIdInsertQuery> = expressionsType
          val exactNine: Exact<RowIdInsertQuery> = nineType
          val exactDefaults: Exact<RowIdInsertQuery> = defaultsType
          val exactWide: Exact<RowIdInsertQuery> = wideType
          val exactBound: Exact<RowIdInsertQuery> = boundType
          val exactRowId: Exact<Long?> = rowIdType
          val exactCompiled: Exact<CompiledRowIdInsert> = compiledType
          val exactCompiledRowId: Exact<Long?> = compiledRowIdType
          val exactObserved: Exact<Maybe<Long>> = observedType
          val exactCompiledObserved: Exact<Maybe<Long>> = compiledObservedType
        }
      """
    ).isOk()
  }

  @Test
  fun `INSERT SELECT types its positions by the selected expressions`() {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection) {
          val minAge = PERSON.AGE.asParameter("min")
          val ages = SELECT COLUMN PERSON.AGE FROM PERSON WHERE (PERSON.AGE GREATER_THAN minAge)
          val readAges = ages
              .readAs(SqlType.STRING)
              .mapTo(::Token)
          val names = SELECT
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .FROM(PERSON)
          val mappedNames = names.mapTo { name, _ -> name.orEmpty() }
          val adults = names AS "adults"
          val pets = SELECT
              .COLUMNS(PET.NAME, PET.OWNER)
              .FROM(PET)
          val oneColumn = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT ages
          val readOneColumn = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT readAges
          val inlineOneColumn = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN PERSON.AGE FROM PERSON)
          val nullableIntoRequired = INSERT INTO PET COLUMNS PET.NAME SELECT (SELECT COLUMN PERSON.NAME FROM PERSON)
          val counted = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (
            SELECT COLUMN COUNT().readAs(SqlType.INT) FROM PET
          )
          val scalar = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN ages.asScalar())
          val rows = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .SELECT(names)
          val infixRows = INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE) SELECT mappedNames
          val requiredIntoNullable = INSERT
              .OR(CONFLICT_IGNORE)
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.PARENT_ID)
              .SELECT(pets)
          val compound = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .SELECT(names UNION_ALL names)
          val derived = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .SELECT(
                SELECT
                    .COLUMNS(adults[PERSON.NAME], adults[PERSON.AGE])
                    .FROM(adults)
              )
          val with = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .SELECT(
                WITH(names) { cte ->
                  SELECT
                      .COLUMNS(cte[PERSON.NAME], cte[PERSON.AGE])
                      .FROM(cte)
                }
              )
          val wide = INSERT
              .INTO(PERSON)
              .COLUMNS($WIDE_COLUMNS)
              .SELECT(SELECT.COLUMNS($WIDE_COLUMNS).FROM(PERSON))
          val infixWide = INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS) SELECT names
          val affected = oneColumn
              .bind(minAge TO 30)
              .usingConnection(connection)
              .execute()
          val observed = rows.observe()

          val readAgesType = readAges.exactType()
          val mappedNamesType = mappedNames.exactType()
          val oneColumnType = oneColumn.exactType()
          val readOneColumnType = readOneColumn.exactType()
          val inlineOneColumnType = inlineOneColumn.exactType()
          val nullableIntoRequiredType = nullableIntoRequired.exactType()
          val countedType = counted.exactType()
          val scalarType = scalar.exactType()
          val rowsType = rows.exactType()
          val infixRowsType = infixRows.exactType()
          val requiredIntoNullableType = requiredIntoNullable.exactType()
          val compoundType = compound.exactType()
          val derivedType = derived.exactType()
          val withType = with.exactType()
          val wideType = wide.exactType()
          val infixWideType = infixWide.exactType()
          val affectedType = affected.exactType()
          val observedType = observed.exactType()
          val exactReadAges: Exact<SelectQuery<Token, ScalarSelection<Int, Required, Token>>> = readAgesType
          val exactMappedNames: Exact<SelectQuery<String, RowSelection<Row2<String, Int>, String>>> = mappedNamesType
          val exactOneColumn: Exact<InsertSelectStage<Person>> = oneColumnType
          val exactReadOneColumn: Exact<InsertSelectStage<Person>> = readOneColumnType
          val exactInlineOneColumn: Exact<InsertSelectStage<Person>> = inlineOneColumnType
          val exactNullableIntoRequired: Exact<InsertSelectStage<Pet>> = nullableIntoRequiredType
          val exactCounted: Exact<InsertSelectStage<Person>> = countedType
          val exactScalar: Exact<InsertSelectStage<Person>> = scalarType
          val exactRows: Exact<InsertSelectStage<Person>> = rowsType
          val exactInfixRows: Exact<InsertSelectStage<Person>> = infixRowsType
          val exactRequiredIntoNullable: Exact<InsertSelectStage<Person>> = requiredIntoNullableType
          val exactCompound: Exact<InsertSelectStage<Person>> = compoundType
          val exactDerived: Exact<InsertSelectStage<Person>> = derivedType
          val exactWith: Exact<InsertSelectStage<Person>> = withType
          val exactWide: Exact<InsertSelectStage<Person>> = wideType
          val exactInfixWide: Exact<InsertSelectStage<Person>> = infixWideType
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

  @Test
  fun `UPSERT infers its clause stages and keeps readRowId through DO NOTHING only`() {
    compileInsertConsumer(
      """
        fun verify(connection: DbConnection) {
          val target = INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME
          val composite = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME, PERSON.AGE)
              .VALUES("Ada", 36)
              .VALUES("Bob", 17)
              .ON_CONFLICT(PERSON.NAME, PERSON.AGE)
          val last = INSERT
              .INTO(PERSON)
              .COLUMNS(PERSON.NAME)
              .VALUES("Ada")
              .ON_CONFLICT()
          val nothing = target.DO_NOTHING()
          val nothingRowId = nothing.readRowId()
          val chainedNothing = nothing
              .ON_CONFLICT(PERSON.ID)
              .DO_NOTHING()
          val chainedRowId = chainedNothing.readRowId()
          val lastNothing = last.DO_NOTHING()
          val lastNothingRowId = lastNothing.readRowId()
          val compositeNothing = composite.DO_NOTHING()
          val doUpdate = target.DO_UPDATE
          val set = doUpdate SET (PERSON.AGE to PERSON.AGE.EXCLUDED + 1)
          val setNull = doUpdate SET_NULL PERSON.PARENT_ID
          val excludedValue = doUpdate.SET(PERSON.TOKEN, PERSON.TOKEN.EXCLUDED)
          val where = set WHERE (PERSON.AGE.EXCLUDED GREATER_THAN PERSON.AGE)
          val afterUpdate = where
              .ON_CONFLICT(PERSON.ID)
              .DO_NOTHING()
          val lastUpdate = last.DO_UPDATE.SET(PERSON.NAME, PERSON.NAME.EXCLUDED)
          val lastWhere = lastUpdate WHERE (PERSON.TOKEN.EXCLUDED IS Token("a"))
          val ages = SELECT COLUMN PERSON.AGE FROM PERSON
          val selected = INSERT INTO PERSON COLUMNS PERSON.AGE SELECT ages ON_CONFLICT PERSON.AGE
          val wide = INSERT
              .INTO(PERSON)
              .COLUMNS($WIDE_COLUMNS)
              .VALUES { it[PERSON.NAME] = "Ada" }
              .ON_CONFLICT(PERSON.NAME)
          val wideRowId = wide
              .DO_NOTHING()
              .readRowId()
          val wideRows = INSERT
              .INTO(PERSON)
              .COLUMNS($WIDE_COLUMNS)
              .VALUES { it[PERSON.NAME] = "Ada" }
              .VALUES { it[PERSON.NAME] = "Bob" }
              .ON_CONFLICT()
          val excluded = PERSON.TOKEN.EXCLUDED
          val affected = where
              .usingConnection(connection)
              .execute()
          val observed = lastWhere.observe()

          val targetType = target.exactType()
          val compositeType = composite.exactType()
          val lastType = last.exactType()
          val nothingType = nothing.exactType()
          val nothingRowIdType = nothingRowId.exactType()
          val chainedNothingType = chainedNothing.exactType()
          val chainedRowIdType = chainedRowId.exactType()
          val lastNothingType = lastNothing.exactType()
          val lastNothingRowIdType = lastNothingRowId.exactType()
          val compositeNothingType = compositeNothing.exactType()
          val doUpdateType = doUpdate.exactType()
          val setType = set.exactType()
          val setNullType = setNull.exactType()
          val excludedValueType = excludedValue.exactType()
          val whereType = where.exactType()
          val afterUpdateType = afterUpdate.exactType()
          val lastUpdateType = lastUpdate.exactType()
          val lastWhereType = lastWhere.exactType()
          val selectedType = selected.exactType()
          val wideType = wide.exactType()
          val wideRowIdType = wideRowId.exactType()
          val wideRowsType = wideRows.exactType()
          val excludedType = excluded.exactType()
          val affectedType = affected.exactType()
          val observedType = observed.exactType()
          val exactTarget: Exact<RowIdUpsertStage<Person>> = targetType
          val exactComposite: Exact<UpsertStage<Person>> = compositeType
          val exactLast: Exact<RowIdLastUpsertStage<Person>> = lastType
          val exactNothing: Exact<RowIdUpsertDoNothingStage<Person>> = nothingType
          val exactNothingRowId: Exact<RowIdInsertQuery> = nothingRowIdType
          val exactChainedNothing: Exact<RowIdUpsertDoNothingStage<Person>> = chainedNothingType
          val exactChainedRowId: Exact<RowIdInsertQuery> = chainedRowIdType
          val exactLastNothing: Exact<RowIdLastUpsertDoNothingStage> = lastNothingType
          val exactLastNothingRowId: Exact<RowIdInsertQuery> = lastNothingRowIdType
          val exactCompositeNothing: Exact<UpsertDoNothingStage<Person>> = compositeNothingType
          val exactDoUpdate: Exact<UpsertDoUpdateStage<Person>> = doUpdateType
          val exactSet: Exact<UpsertSetStage<Person>> = setType
          val exactSetNull: Exact<UpsertSetStage<Person>> = setNullType
          val exactExcludedValue: Exact<UpsertSetStage<Person>> = excludedValueType
          val exactWhere: Exact<UpsertWhereStage<Person>> = whereType
          val exactAfterUpdate: Exact<UpsertDoNothingStage<Person>> = afterUpdateType
          val exactLastUpdate: Exact<LastUpsertSetStage<Person>> = lastUpdateType
          val exactLastWhere: Exact<MutationQuery> = lastWhereType
          val exactSelected: Exact<UpsertStage<Person>> = selectedType
          val exactWide: Exact<RowIdUpsertStage<Person>> = wideType
          val exactWideRowId: Exact<RowIdInsertQuery> = wideRowIdType
          val exactWideRows: Exact<LastUpsertStage<Person>> = wideRowsType
          val exactExcluded: Exact<EncodableSqlExpression<Token, Token, Required, Token>> = excludedType
          val exactAffected: Exact<Int> = affectedType
          val exactObserved: Exact<Single<Int>> = observedType
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
      import com.siimkinks.sqlitemagic.EXCLUDED
      import com.siimkinks.sqlitemagic.CompiledMutation
      import com.siimkinks.sqlitemagic.CompiledRowIdInsert
      import com.siimkinks.sqlitemagic.INSERT
      import com.siimkinks.sqlitemagic.InsertColumns1Stage
      import com.siimkinks.sqlitemagic.InsertColumnsStage
      import com.siimkinks.sqlitemagic.InsertDefaultValuesStage
      import com.siimkinks.sqlitemagic.InsertIntoStage
      import com.siimkinks.sqlitemagic.InsertSingleRow1Stage
      import com.siimkinks.sqlitemagic.InsertSingleRow4Stage
      import com.siimkinks.sqlitemagic.InsertSelectStage
      import com.siimkinks.sqlitemagic.InsertSingleRowStage
      import com.siimkinks.sqlitemagic.InsertValues1Stage
      import com.siimkinks.sqlitemagic.InsertValues2Stage
      import com.siimkinks.sqlitemagic.InsertValues3Stage
      import com.siimkinks.sqlitemagic.InsertValues4Stage
      import com.siimkinks.sqlitemagic.InsertValuesStage
      import com.siimkinks.sqlitemagic.InsertedRow
      import com.siimkinks.sqlitemagic.LastUpsertSetStage
      import com.siimkinks.sqlitemagic.LastUpsertStage
      import com.siimkinks.sqlitemagic.MutationQuery
      import com.siimkinks.sqlitemagic.RawMutationQuery
      import com.siimkinks.sqlitemagic.RowIdInsertQuery
      import com.siimkinks.sqlitemagic.RowIdLastUpsertDoNothingStage
      import com.siimkinks.sqlitemagic.RowIdLastUpsertStage
      import com.siimkinks.sqlitemagic.RowIdUpsertDoNothingStage
      import com.siimkinks.sqlitemagic.RowIdUpsertStage
      import com.siimkinks.sqlitemagic.Row1
      import com.siimkinks.sqlitemagic.Row3
      import com.siimkinks.sqlitemagic.RowSelection
      import com.siimkinks.sqlitemagic.SUM
      import com.siimkinks.sqlitemagic.SelectQuery
      import com.siimkinks.sqlitemagic.SET
      import com.siimkinks.sqlitemagic.UNION_ALL
      import com.siimkinks.sqlitemagic.UpsertDoNothingStage
      import com.siimkinks.sqlitemagic.UpsertDoUpdateStage
      import com.siimkinks.sqlitemagic.UpsertSetStage
      import com.siimkinks.sqlitemagic.UpsertStage
      import com.siimkinks.sqlitemagic.UpsertWhereStage
      import com.siimkinks.sqlitemagic.WITH
      import com.siimkinks.sqlitemagic.asScalar
      import com.siimkinks.sqlitemagic.mapTo
      import com.siimkinks.sqlitemagic.rxjava2.observe
      import io.reactivex.Maybe
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
        "Unresolved reference 'VALUES' on receiver of type 'InsertDefaultValuesStage'"
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
        "readRowId after a second VALUES",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).VALUES("Ada", 36).VALUES("Bob", 17).readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'InsertValues2Stage<Person, String, String?, Int, Int>'"
      ),
      Arguments.of(
        "readRowId after list rows",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES listOf(Row1("Ada"))).readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'InsertValues1Stage<Person, String, String?>'"
      ),
      Arguments.of(
        "readRowId after a second wide VALUES",
        """INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES { it[PERSON.NAME] = "Ada" }.VALUES { }.readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'InsertValuesStage<Person>'"
      ),
      Arguments.of(
        "readRowId after wide list rows",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).VALUES(listOf(1)) { row, _ -> row[PERSON.AGE] = 1 }.readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'InsertValuesStage<Person>'"
      ),
      Arguments.of(
        "readRowId without rows",
        "(INSERT INTO PERSON COLUMNS PERSON.NAME).readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'InsertColumns1Stage<Person, String, String?>'"
      ),
      Arguments.of(
        "readRowId without wide rows",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'InsertColumnsStage<Person>'"
      ),
      Arguments.of(
        "readRowId after the count statement is configured",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada").usingConnection(connection).readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "SelectedRow query in a typed form",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).SELECT(SELECT.COLUMNS(PERSON.all()).FROM(PERSON))",
        "actual type is 'SelectFromStage<SelectedRow, RowSelection<SelectedRow, SelectedRow>>', but " +
            "'SelectQuery<*, out RowSelection<out Row2<String?, Int?>, *>>' was expected"
      ),
      Arguments.of(
        "entity value in a typed form",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE)" +
            ".SELECT(SELECT.COLUMNS(PERSON, PERSON.AGE).FROM(PERSON))",
        "actual type is 'SelectFromStage<Row2<Person, Int>, " +
            "RowSelection<Row2<EntityRows, Int>, Row2<Person, Int>>>', " +
            "but 'SelectQuery<*, out RowSelection<out Row2<String?, Int?>, *>>' was expected"
      ),
      Arguments.of(
        "entity query in a typed form",
        "INSERT INTO PERSON COLUMNS PERSON.NAME SELECT (SELECT FROM PERSON)",
        "actual type is 'SelectFromStage<Person, EntitySelection<Person>>', but " +
            "'SelectQuery<*, out ScalarSelection<out String?, *, *>>' was expected"
      ),
      Arguments.of(
        "query of another column count",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE)" +
            ".SELECT(SELECT.COLUMNS(PERSON.NAME, PERSON.AGE, PERSON.ID).FROM(PERSON))",
        "actual type is 'SelectFromStage<Row3<String?, Int, Long>, RowSelection<Row3<String, Int, Long>, " +
            "Row3<String?, Int, Long>>>', but 'SelectQuery<*, out RowSelection<out Row2<String?, Int?>, *>>' " +
            "was expected"
      ),
      Arguments.of(
        "scalar query for two columns",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE).SELECT(SELECT.COLUMN(PERSON.NAME).FROM(PERSON))",
        "actual type is 'SelectFromStage<String?, ScalarSelection<String, SqlNullable, String?>>', but " +
            "'SelectQuery<*, out RowSelection<out Row2<String?, Int?>, *>>' was expected"
      ),
      Arguments.of(
        "selected expression of another value type",
        "INSERT.INTO(PERSON).COLUMNS(PERSON.NAME, PERSON.AGE)" +
            ".SELECT(SELECT.COLUMNS(PERSON.NAME, PERSON.NAME).FROM(PERSON))",
        "actual type is 'SelectFromStage<Row2<String?, String?>, RowSelection<Row2<String, String>, " +
            "Row2<String?, String?>>>', but 'SelectQuery<*, out RowSelection<out Row2<String?, Int?>, *>>' was expected"
      ),
      Arguments.of(
        "scalar query of another value type",
        "INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN PERSON.NAME FROM PERSON)",
        "actual type is 'SelectFromStage<String?, ScalarSelection<String, SqlNullable, String?>>', but " +
            "'SelectQuery<*, out ScalarSelection<out Int?, *, *>>' was expected"
      ),
      Arguments.of(
        "selected wider number without readAs",
        "INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN COUNT() FROM PET)",
        "actual type is 'SelectFromStage<Long, ScalarSelection<Long, Required, Long>>', but " +
            "'SelectQuery<*, out ScalarSelection<out Int?, *, *>>' was expected"
      ),
      Arguments.of(
        "readRowId after SELECT",
        "(INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN PERSON.AGE FROM PERSON)).readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'InsertSelectStage<Person>'"
      ),
      Arguments.of(
        "VALUES after SELECT",
        "(INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN PERSON.AGE FROM PERSON)).VALUES(36)",
        "Unresolved reference 'VALUES' on receiver of type 'InsertSelectStage<Person>'"
      ),
      Arguments.of(
        "VALUES after wide SELECT",
        "INSERT.INTO(PERSON).COLUMNS($WIDE_COLUMNS).SELECT(SELECT.FROM(PERSON)).VALUES { }",
        "Unresolved reference 'VALUES' on receiver of type 'InsertSelectStage<Person>'"
      ),
      Arguments.of(
        "readRowId after DO UPDATE",
        """((INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE SET """ +
            """(PERSON.AGE to 1)).readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'UpsertSetStage<Person>'"
      ),
      Arguments.of(
        "readRowId after DO UPDATE WHERE",
        """((INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE SET """ +
            """(PERSON.AGE to 1) WHERE (PERSON.AGE IS 1)).readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'UpsertWhereStage<Person>'"
      ),
      Arguments.of(
        "readRowId after DO NOTHING that follows DO UPDATE",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").ON_CONFLICT(PERSON.NAME).DO_UPDATE""" +
            """.SET(PERSON.AGE, 1).ON_CONFLICT(PERSON.ID).DO_NOTHING().readRowId()""",
        "Unresolved reference 'readRowId' on receiver of type 'UpsertDoNothingStage<Person>'"
      ),
      Arguments.of(
        "readRowId after DO UPDATE without a target",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").ON_CONFLICT().DO_UPDATE.SET(PERSON.AGE, 1)""" +
            ".readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'LastUpsertSetStage<Person>'"
      ),
      Arguments.of(
        "readRowId after multi-row DO NOTHING",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").VALUES("Bob").ON_CONFLICT(PERSON.NAME)""" +
            ".DO_NOTHING().readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'UpsertDoNothingStage<Person>'"
      ),
      Arguments.of(
        "readRowId after INSERT SELECT DO NOTHING",
        "(INSERT INTO PERSON COLUMNS PERSON.AGE SELECT (SELECT COLUMN PERSON.AGE FROM PERSON) ON_CONFLICT " +
            "PERSON.AGE).DO_NOTHING().readRowId()",
        "Unresolved reference 'readRowId' on receiver of type 'UpsertDoNothingStage<Person>'"
      ),
      Arguments.of(
        "ON_CONFLICT after DO NOTHING without a target",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").ON_CONFLICT().DO_NOTHING()""" +
            ".ON_CONFLICT(PERSON.NAME)",
        "Unresolved reference 'ON_CONFLICT' on receiver of type 'RowIdLastUpsertDoNothingStage'"
      ),
      Arguments.of(
        "ON_CONFLICT after multi-row DO NOTHING without a target",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").VALUES("Bob").ON_CONFLICT().DO_NOTHING()""" +
            ".ON_CONFLICT()",
        "Unresolved reference 'ON_CONFLICT' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "ON_CONFLICT after DO UPDATE without a target",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").ON_CONFLICT().DO_UPDATE.SET(PERSON.AGE, 1)""" +
            ".ON_CONFLICT(PERSON.NAME)",
        "Unresolved reference 'ON_CONFLICT' on receiver of type 'LastUpsertSetStage<Person>'"
      ),
      Arguments.of(
        "ON_CONFLICT after DO UPDATE WHERE without a target",
        """INSERT.INTO(PERSON).COLUMNS(PERSON.NAME).VALUES("Ada").ON_CONFLICT().DO_UPDATE.SET(PERSON.AGE, 1)""" +
            ".WHERE(PERSON.AGE IS 1).ON_CONFLICT()",
        "Unresolved reference 'ON_CONFLICT' on receiver of type 'MutationQuery'"
      ),
      Arguments.of(
        "ON_CONFLICT after DEFAULT VALUES",
        "INSERT.INTO(PERSON).DEFAULT_VALUES().ON_CONFLICT()",
        "Unresolved reference 'ON_CONFLICT' on receiver of type 'InsertDefaultValuesStage'"
      ),
      Arguments.of(
        "ON_CONFLICT before VALUES",
        "INSERT INTO PERSON COLUMNS PERSON.NAME ON_CONFLICT PERSON.NAME",
        "Unresolved reference 'ON_CONFLICT'"
      ),
      Arguments.of(
        "conflict target of another table",
        """INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PET.NAME""",
        "actual type is 'Column<Pet"
      ),
      Arguments.of(
        "execute without an upsert action",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).execute()""",
        "Unresolved reference 'execute' on receiver of type 'RowIdUpsertStage<Person>'"
      ),
      Arguments.of(
        "execute after DO UPDATE without SET",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE.execute()""",
        "Unresolved reference 'execute' on receiver of type 'UpsertDoUpdateStage<Person>'"
      ),
      Arguments.of(
        "DO UPDATE SET target of another table",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE SET """ +
            """(PET.NAME to "Rex")""",
        "Column<Pet"
      ),
      Arguments.of(
        "EXCLUDED as a SET target",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE""" +
            """.SET(PERSON.NAME.EXCLUDED, "Bob")""",
        "EncodableSqlExpression<String?, String, SqlNullable, String?>"
      ),
      Arguments.of(
        "EXCLUDED of another value type",
        """(INSERT INTO PERSON COLUMNS PERSON.NAME VALUES "Ada" ON_CONFLICT PERSON.NAME).DO_UPDATE""" +
            ".SET(PERSON.AGE, PERSON.NAME.EXCLUDED)",
        "EncodableSqlExpression<String?, String, SqlNullable, String?>"
      ),
      Arguments.of(
        "EXCLUDED of an expression",
        "(PERSON.AGE + 1).EXCLUDED",
        "EXCLUDED: EncodableSqlExpression<W, R, N, T>' is inapplicable because of a receiver type mismatch"
      ),
      Arguments.of(
        "raw table name",
        """INSERT INTO "person"""",
        "Table<uninferred P (of fun <P> INTO)>"
      )
    )
  }
}
