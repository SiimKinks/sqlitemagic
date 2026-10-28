package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

internal class ScalarFieldContractTest {
  @Test
  fun `generated scalar fields preserve exact expression reader nullness and selected types`() {
    assertThat(GENERATED.adapterDriverResult)
      .isEqualTo(
        listOf(
          "scalar_field_records",
          10,
          true,
          true,
          73L,
          true,
          true,
          true
        )
      )

    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "ScalarFieldConsumer.kt",
          contents = """
            package downstream.scalarfield

            import com.siimkinks.sqlitemagic.AND
            import com.siimkinks.sqlitemagic.CompiledCount
            import com.siimkinks.sqlitemagic.CompiledCursor
            import com.siimkinks.sqlitemagic.CompiledFirst
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.ParameterSource
            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.Row2
            import com.siimkinks.sqlitemagic.RowSelection
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.ScalarFieldRecordTable.Companion.SCALAR_FIELD_RECORD
            import com.siimkinks.sqlitemagic.ScalarSelection
            import com.siimkinks.sqlitemagic.SelectFromStage
            import com.siimkinks.sqlitemagic.SelectGroupStage
            import com.siimkinks.sqlitemagic.SelectHavingStage
            import com.siimkinks.sqlitemagic.SelectOrderStage
            import com.siimkinks.sqlitemagic.SelectProjectionStage
            import com.siimkinks.sqlitemagic.Selection
            import com.siimkinks.sqlitemagic.SqlExpression
            import com.siimkinks.sqlitemagic.SqlNullability
            import com.siimkinks.sqlitemagic.SqlNullable
            import com.siimkinks.sqlitemagic.SqlParameter
            import com.siimkinks.sqlitemagic.asValue
            import fixtures.scalarfield.DecodedToken
            import fixtures.scalarfield.FieldToken
            import fixtures.scalarfield.SentinelToken
            import typewitness.Exact

            fun <R, N : SqlNullability, T> SqlExpression<R, N, T>.exact() =
              Exact<SqlExpression<R, N, T>>()
            fun <W, R, N : SqlNullability, T> ParameterSource<W, R, N, T>.parameterSourceExact() =
              Exact<ParameterSource<W, R, N, T>>()
            fun <W, R, N : SqlNullability, T> SqlParameter<W, R, N, T>.parameterExact() =
              Exact<SqlParameter<W, R, N, T>>()
            fun <T> CompiledSelect<T>.exact() = Exact<CompiledSelect<T>>()
            fun <T> CompiledFirst<T>.exact() = Exact<CompiledFirst<T>>()
            fun <T> CompiledCursor<T>.exact() = Exact<CompiledCursor<T>>()
            fun <T, S : Selection<T>> SelectProjectionStage<T, S>.exact() =
              Exact<SelectProjectionStage<T, S>>()
            fun <T, S : Selection<T>> SelectFromStage<T, S>.exact() = Exact<SelectFromStage<T, S>>()
            fun <T, S : Selection<T>> SelectGroupStage<T, S>.exact() = Exact<SelectGroupStage<T, S>>()
            fun <T, S : Selection<T>> SelectHavingStage<T, S>.exact() = Exact<SelectHavingStage<T, S>>()
            fun <T, S : Selection<T>> SelectOrderStage<T, S>.exact() = Exact<SelectOrderStage<T, S>>()

            fun verify() {
              val id = SCALAR_FIELD_RECORD.ID
              val required = SCALAR_FIELD_RECORD.REQUIRED_TEXT
              val requiredBlob = SCALAR_FIELD_RECORD.REQUIRED_BLOB
              val optional = SCALAR_FIELD_RECORD.OPTIONAL_TEXT
              val token = SCALAR_FIELD_RECORD.TOKEN
              val sentinel = SCALAR_FIELD_RECORD.SENTINEL
              val sentinelRequired = SCALAR_FIELD_RECORD.SENTINEL_REQUIRED
              val decoded = SCALAR_FIELD_RECORD.DECODED
              val optionalDecoded = SCALAR_FIELD_RECORD.OPTIONAL_DECODED
              val optionalBoolean = SCALAR_FIELD_RECORD.OPTIONAL_BOOLEAN

              val idExact = id.exact()
              val requiredExact = required.exact()
              val optionalExact = optional.exact()
              val tokenExact = token.exact()
              val sentinelExact = sentinel.exact()
              val sentinelRequiredExact = sentinelRequired.exact()
              val decodedExact = decoded.exact()
              val optionalBooleanExact = optionalBoolean.exact()

              val idType: Exact<SqlExpression<Long, Required, Long>> = idExact
              val requiredType: Exact<SqlExpression<String, Required, String>> = requiredExact
              val optionalType: Exact<SqlExpression<String, SqlNullable, String?>> = optionalExact
              val tokenType: Exact<SqlExpression<FieldToken, Required, FieldToken>> = tokenExact
              val sentinelType: Exact<SqlExpression<SentinelToken, SqlNullable, SentinelToken>> = sentinelExact
              val sentinelRequiredType: Exact<SqlExpression<SentinelToken, Required, SentinelToken>> =
                  sentinelRequiredExact
              val decodedType: Exact<SqlExpression<DecodedToken?, Required, DecodedToken>> = decodedExact
              val booleanType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = optionalBooleanExact

              val idParameter = id.asParameter("id")
              val requiredParameter = required.asParameter("required")
              val requiredBlobParameter = requiredBlob.asParameter("requiredBlob")
              val optionalParameter = optional.asParameter("optional")
              val tokenParameter = token.asParameter("token")
              val sentinelParameter = sentinel.asParameter("sentinel")
              val sentinelRequiredParameter = sentinelRequired.asParameter("sentinelRequired")
              val decodedParameter = decoded.asParameter("decoded")
              val optionalDecodedParameter = optionalDecoded.asParameter("optionalDecoded")
              val optionalBooleanParameter = optionalBoolean.asParameter("optionalBoolean")
              val aliasedTableForParameter = SCALAR_FIELD_RECORD AS "parameterRecord"
              val aliasedIdParameter = aliasedTableForParameter.ID.asParameter("aliasedId")

              val requiredSourceType: Exact<ParameterSource<String, String, Required, String>> =
                  required.parameterSourceExact()
              val optionalDecodedSourceType:
                  Exact<ParameterSource<DecodedToken?, DecodedToken?, Required, DecodedToken?>> =
                  optionalDecoded.parameterSourceExact()
              val idParameterType: Exact<SqlParameter<Long, Long, Required, Long>> = idParameter.parameterExact()
              val requiredParameterType: Exact<SqlParameter<String, String, Required, String>> =
                  requiredParameter.parameterExact()
              val requiredBlobParameterType: Exact<SqlParameter<ByteArray, ByteArray, Required, ByteArray>> =
                  requiredBlobParameter.parameterExact()
              val optionalParameterType: Exact<SqlParameter<String?, String, SqlNullable, String?>> =
                  optionalParameter.parameterExact()
              val tokenParameterType: Exact<SqlParameter<FieldToken, FieldToken, Required, FieldToken>> =
                  tokenParameter.parameterExact()
              val sentinelParameterType:
                  Exact<SqlParameter<SentinelToken?, SentinelToken, SqlNullable, SentinelToken?>> =
                  sentinelParameter.parameterExact()
              val sentinelRequiredParameterType:
                  Exact<SqlParameter<SentinelToken, SentinelToken, SqlNullable, SentinelToken?>> =
                  sentinelRequiredParameter.parameterExact()
              val decodedParameterType: Exact<SqlParameter<DecodedToken, DecodedToken?, Required, DecodedToken?>> =
                  decodedParameter.parameterExact()
              val optionalDecodedParameterType:
                  Exact<SqlParameter<DecodedToken?, DecodedToken?, Required, DecodedToken?>> =
                  optionalDecodedParameter.parameterExact()
              val optionalBooleanParameterType: Exact<SqlParameter<Boolean?, Boolean, SqlNullable, Boolean?>> =
                  optionalBooleanParameter.parameterExact()
              val aliasedIdParameterType: Exact<SqlParameter<Long, Long, Required, Long>> =
                  aliasedIdParameter.parameterExact()

              optionalParameter TO null
              sentinelParameter TO null
              optionalDecodedParameter TO null
              optionalBooleanParameter TO null

              val tokenPredicate = token IS FieldToken("token")
              val sentinelPredicate = sentinel IS_NOT SentinelToken("sentinel")
              val decodedPredicate = decoded IS optionalDecoded
              val optionalDecodedPredicate = optionalDecoded LESS_THAN DecodedToken("decoded")
              val combinedPredicate = (id GREATER_THAN idParameter) AND optionalBoolean.IS_NOT_NULL()
              val tokenMembership = token IN listOf(FieldToken("first"), FieldToken("second"))
              val decodedRange = decoded BETWEEN DecodedToken("lower") AND optionalDecoded
              val sentinelPattern = sentinel NOT_LIKE "sentinel!%" ESCAPE "!"
              val filtered = SELECT.COLUMN(token)
                  .FROM(SCALAR_FIELD_RECORD)
                  .WHERE(tokenPredicate AND sentinelPredicate AND decodedPredicate AND optionalDecodedPredicate)
                  .compile()
              val tokenPredicateExact = tokenPredicate.exact()
              val sentinelPredicateExact = sentinelPredicate.exact()
              val decodedPredicateExact = decodedPredicate.exact()
              val optionalDecodedPredicateExact = optionalDecodedPredicate.exact()
              val combinedPredicateExact = combinedPredicate.exact()
              val tokenMembershipExact = tokenMembership.exact()
              val decodedRangeExact = decodedRange.exact()
              val sentinelPatternExact = sentinelPattern.exact()
              val tokenPredicateType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = tokenPredicateExact
              val sentinelPredicateType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = sentinelPredicateExact
              val decodedPredicateType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = decodedPredicateExact
              val optionalDecodedPredicateType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> =
                  optionalDecodedPredicateExact
              val combinedPredicateType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = combinedPredicateExact
              val tokenMembershipType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = tokenMembershipExact
              val decodedRangeType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = decodedRangeExact
              val sentinelPatternType: Exact<SqlExpression<Boolean, SqlNullable, Boolean?>> = sentinelPatternExact
              val filteredType: Exact<CompiledSelect<FieldToken>> = filtered.exact()

              val fluent = SELECT.COLUMN(required)
                  .FROM(SCALAR_FIELD_RECORD)
                  .compile()
              val infix = (SELECT COLUMN optional FROM SCALAR_FIELD_RECORD).compile()
              val aliasedTable = SCALAR_FIELD_RECORD AS "record"
              val aliased = SELECT.COLUMN(aliasedTable.ID)
                  .FROM(aliasedTable)
                  .compile()
              val sentinelPlan = SELECT.COLUMN(sentinel)
                  .FROM(SCALAR_FIELD_RECORD)
                  .compile()
              val decodedPlan = SELECT.COLUMN(decoded)
                  .FROM(SCALAR_FIELD_RECORD)
                  .compile()
              val booleanPlan = SELECT.COLUMN(optionalBoolean)
                  .FROM(SCALAR_FIELD_RECORD)
                  .compile()
              val first = SELECT.COLUMN(required)
                  .FROM(SCALAR_FIELD_RECORD)
                  .takeFirst()
              val count = SELECT.COLUMN(required)
                  .FROM(SCALAR_FIELD_RECORD)
                  .count()
              val cursor = SELECT.COLUMN(required)
                  .FROM(SCALAR_FIELD_RECORD)
                  .toCursor()
              val orderedId = SELECT.COLUMN(id)
                  .FROM(SCALAR_FIELD_RECORD)
                  .ORDER_BY(id.ASC())
              val orderedDecoded = SELECT.COLUMN(decoded)
                  .FROM(SCALAR_FIELD_RECORD)
                  .ORDER_BY(decoded.DESC())
              val orderedIdPlan = orderedId.compile()
              val orderedDecodedPlan = orderedDecoded.compile()

              val fluentExact = fluent.exact()
              val infixExact = infix.exact()
              val aliasedExact = aliased.exact()
              val sentinelPlanExact = sentinelPlan.exact()
              val decodedPlanExact = decodedPlan.exact()
              val booleanPlanExact = booleanPlan.exact()
              val firstExact = first.exact()
              val cursorExact = cursor.exact()
              val orderedIdExact = orderedId.exact()
              val orderedDecodedExact = orderedDecoded.exact()
              val orderedIdPlanExact = orderedIdPlan.exact()
              val orderedDecodedPlanExact = orderedDecodedPlan.exact()

              val fluentType: Exact<CompiledSelect<String>> = fluentExact
              val infixType: Exact<CompiledSelect<String?>> = infixExact
              val aliasedType: Exact<CompiledSelect<Long>> = aliasedExact
              val sentinelPlanType: Exact<CompiledSelect<SentinelToken>> = sentinelPlanExact
              val decodedPlanType: Exact<CompiledSelect<DecodedToken>> = decodedPlanExact
              val booleanPlanType: Exact<CompiledSelect<Boolean?>> = booleanPlanExact
              val firstType: Exact<CompiledFirst<String>> = firstExact
              val countType: CompiledCount = count
              val cursorType: Exact<CompiledCursor<String>> = cursorExact
              val orderedIdType: Exact<SelectOrderStage<Long, ScalarSelection<Long, Required, Long>>> = orderedIdExact
              val orderedDecodedType:
                  Exact<SelectOrderStage<DecodedToken, ScalarSelection<DecodedToken?, Required, DecodedToken>>> =
                  orderedDecodedExact
              val orderedIdPlanType: Exact<CompiledSelect<Long>> = orderedIdPlanExact
              val orderedDecodedPlanType: Exact<CompiledSelect<DecodedToken>> = orderedDecodedPlanExact

              val groupedId = SELECT.COLUMN(id)
                  .FROM(SCALAR_FIELD_RECORD)
                  .GROUP_BY(id)
              val havingId = groupedId.HAVING(true.asValue())
              val groupedDecoded = SELECT.COLUMN(decoded AS "grouped decoded")
                  .FROM(SCALAR_FIELD_RECORD)
                  .GROUP_BY(id)
              val havingDecoded = groupedDecoded.HAVING(true.asValue())
              val groupedIdExact = groupedId.exact()
              val havingIdExact = havingId.exact()
              val groupedDecodedExact = groupedDecoded.exact()
              val havingDecodedExact = havingDecoded.exact()
              val groupedIdType: Exact<SelectGroupStage<Long, ScalarSelection<Long, Required, Long>>> = groupedIdExact
              val havingIdType: Exact<SelectHavingStage<Long, ScalarSelection<Long, Required, Long>>> = havingIdExact
              val groupedDecodedType:
                  Exact<SelectGroupStage<DecodedToken, ScalarSelection<DecodedToken?, Required, DecodedToken>>> =
                  groupedDecodedExact
              val havingDecodedType:
                  Exact<SelectHavingStage<DecodedToken, ScalarSelection<DecodedToken?, Required, DecodedToken>>> =
                  havingDecodedExact
              val groupedIdPlan = groupedId.compile()
              val havingDecodedCursor = havingDecoded.toCursor()
              val groupedIdPlanExact = groupedIdPlan.exact()
              val havingDecodedCursorExact = havingDecodedCursor.exact()
              val groupedIdPlanType: Exact<CompiledSelect<Long>> = groupedIdPlanExact
              val havingDecodedCursorType: Exact<CompiledCursor<DecodedToken>> = havingDecodedCursorExact

              val distinctId = SELECT DISTINCT id
              val distinctSentinel = SELECT.DISTINCT(sentinel)
                  .FROM(SCALAR_FIELD_RECORD)
              val distinctDecoded = SELECT DISTINCT (decoded AS "distinct decoded") FROM SCALAR_FIELD_RECORD
              val distinctIdExact = distinctId.exact()
              val distinctSentinelExact = distinctSentinel.exact()
              val distinctDecodedExact = distinctDecoded.exact()
              val distinctIdType: Exact<SelectProjectionStage<Long, ScalarSelection<Long, Required, Long>>> =
                  distinctIdExact
              val distinctSentinelType:
                  Exact<SelectFromStage<SentinelToken, ScalarSelection<SentinelToken, SqlNullable, SentinelToken>>> =
                  distinctSentinelExact
              val distinctDecodedType:
                  Exact<SelectFromStage<DecodedToken, ScalarSelection<DecodedToken?, Required, DecodedToken>>> =
                  distinctDecodedExact
              val distinctDecodedPlan = distinctDecoded.compile()
              val distinctDecodedPlanType: Exact<CompiledSelect<DecodedToken>> = distinctDecodedPlan.exact()

              val generatedRow = SELECT.COLUMNS(id, optionalDecoded)
                  .FROM(SCALAR_FIELD_RECORD)
              val inferredGeneratedRow = generatedRow.exact()
              val generatedRowType:
                  Exact<SelectFromStage<Row2<Long, DecodedToken?>, RowSelection<Row2<Long, DecodedToken?>>>> =
                  inferredGeneratedRow
            }
          """
        ),
        classpaths = listOf(GENERATED.outputDirectory)
      )
      .isOk()
  }

  @Test
  fun `declares NOT NULL for columns of non-null properties whatever their encoders return`() {
    GENERATED.adapterSource.assertContainsUnwrapped(
      "CREATE TABLE IF NOT EXISTS scalar_field_records (id INTEGER PRIMARY KEY, " +
          "required_text TEXT NOT NULL DEFAULT '', required_blob BLOB NOT NULL DEFAULT X'', " +
          "optional_text TEXT DEFAULT NULL, token TEXT NOT NULL DEFAULT '', sentinel TEXT DEFAULT NULL, " +
          "sentinel_required TEXT NOT NULL DEFAULT '', decoded TEXT NOT NULL DEFAULT '', " +
          "optional_decoded TEXT DEFAULT NULL, optional_boolean INTEGER DEFAULT NULL)"
    )
  }

  @Test
  fun `generated fields read required columns as their property types`() {
    GENERATED.tableSource.assertContainsUnwrapped(
      "selectedRead = { cursor, index -> cursor.getString(index) }",
      "selectedRead = { cursor, index -> cursor.getBlob(index) }",
      "selectedRead = { cursor, index -> SentinelTokenTransformer.fromDb(cursor.getString(index)) }",
      "selectedRead = { cursor, index -> checkNotNull(DecodedTokenTransformer.fromDb(cursor.getString(index))) { " +
          """"Transformer fixtures.scalarfield.DecodedTokenTransformer.fromDb returned null for required property """ +
          """decoded" } }""",
      "selectedRead = { cursor, index -> cursor.getString(index)?.let(DecodedTokenTransformer::fromDb) }"
    )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidGeneratedFieldPrograms")
  fun `generated scalar fields outside their contracts do not compile`(
    label: String,
    program: String,
    diagnostic: String
  ) {
    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "InvalidGeneratedFieldConsumer.kt",
          contents = """
            package downstream.scalarfield.invalid

            import com.siimkinks.sqlitemagic.ScalarFieldRecordTable.Companion.SCALAR_FIELD_RECORD

            $program
          """
        ),
        classpaths = listOf(GENERATED.outputDirectory)
      )
      .assertCompilationError(diagnostic)
  }

  companion object {
    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "ScalarFieldRecord.kt",
            contents = """
                @file:OptIn(InternalSqliteMagicApi::class)

                package fixtures.scalarfield

                import com.siimkinks.sqlitemagic.ScalarFieldRecordTable.Companion.SCALAR_FIELD_RECORD
                import com.siimkinks.sqlitemagic.SqliteMagic_ScalarFieldRecord_Adapter
                import com.siimkinks.sqlitemagic.annotation.Id
                import com.siimkinks.sqlitemagic.annotation.Table
                import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
                import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
                import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi

                data class FieldToken(val value: String)
                data class SentinelToken(val value: String)
                data class DecodedToken(val value: String)

                object FieldTokenTransformer {
                  @ObjectToDbValue
                  fun toDb(value: FieldToken): String = value.value

                  @DbValueToObject
                  fun fromDb(value: String): FieldToken = FieldToken(value)
                }

                object SentinelTokenTransformer {
                  var calls = 0

                  @ObjectToDbValue
                  fun toDb(value: SentinelToken): String? {
                    calls++
                    return when (value.value) {
                      "sentinel" -> null
                      else -> value.value
                    }
                  }

                  @DbValueToObject
                  fun fromDb(value: String?): SentinelToken = SentinelToken(value ?: "sentinel")
                }

                object DecodedTokenTransformer {
                  val inputs = mutableListOf<DecodedToken?>()

                  @ObjectToDbValue
                  fun toDb(value: DecodedToken?): String {
                    inputs += value
                    return value?.value ?: "absent"
                  }

                  @DbValueToObject
                  fun fromDb(value: String): DecodedToken? = when (value) {
                    "absent" -> null
                    else -> DecodedToken(value)
                  }
                }

                @Table("scalar_field_records")
                data class ScalarFieldRecord(
                  @Id(autoIncrement = false) val id: Long,
                  val requiredText: String,
                  val requiredBlob: ByteArray,
                  val optionalText: String?,
                  val token: FieldToken,
                  val sentinel: SentinelToken?,
                  val sentinelRequired: SentinelToken,
                  val decoded: DecodedToken,
                  val optionalDecoded: DecodedToken?,
                  val optionalBoolean: Boolean?
                )

                object ScalarFieldAdapterDriver {
                  @JvmStatic
                  fun verify(): List<Any> {
                    val adapter = SqliteMagic_ScalarFieldRecord_Adapter
                    val entity = ScalarFieldRecord(
                      id = 73L,
                      requiredText = "required",
                      requiredBlob = byteArrayOf(1, 2),
                      optionalText = null,
                      token = FieldToken("token"),
                      sentinel = null,
                      sentinelRequired = SentinelToken("sentinel"),
                      decoded = DecodedToken("decoded"),
                      optionalDecoded = null,
                      optionalBoolean = null
                    )
                    val sentinelCalls = SentinelTokenTransformer.calls
                    SCALAR_FIELD_RECORD.SENTINEL.asParameter("sentinel") TO null
                    val bypassedNull = SentinelTokenTransformer.calls == sentinelCalls
                    SCALAR_FIELD_RECORD.SENTINEL_REQUIRED.asParameter("sentinelRequired") TO SentinelToken("sentinel")
                    val encodedSentinel = SentinelTokenTransformer.calls == sentinelCalls + 1
                    SCALAR_FIELD_RECORD.OPTIONAL_DECODED.asParameter("optionalDecoded") TO null
                    return listOf(
                      adapter.tableName,
                      adapter.maxColumns,
                      adapter.defaultIdentityColumnName == "id",
                      adapter.hasIdentityValue(
                        entity = entity,
                        columnName = "id"
                      ),
                      adapter.identity(
                        entity = entity,
                        columnName = "id"
                      ),
                      bypassedNull,
                      encodedSentinel,
                      DecodedTokenTransformer.inputs == listOf(null)
                    )
                  }
                }
              """
          ),
          processingStepsFactory = ::modelProcessingSteps
        )
        .isOk()
        .assertGeneratedSources(
          "ScalarFieldRecordTable.kt",
          "SqliteMagic_ScalarFieldRecord_Adapter.kt",
          "SqliteMagic_ScalarFieldRecord_Dao.kt",
          "FieldTokenSqlType.kt"
        )
        .run {
          GeneratedScalarFields(
            adapterDriverResult = loadClass("fixtures.scalarfield.ScalarFieldAdapterDriver")
              .getMethod("verify")
              .invoke(null),
            outputDirectory = outputDirectory,
            tableSource = generatedSource("ScalarFieldRecordTable.kt"),
            adapterSource = generatedSource("SqliteMagic_ScalarFieldRecord_Adapter.kt")
          )
        }
    }

    @JvmStatic
    fun invalidGeneratedFieldPrograms() = listOf(
      Arguments.of(
        "a required text field takes no NULL argument",
        """
          val parameter = SCALAR_FIELD_RECORD.REQUIRED_TEXT.asParameter("required")
          val invalid = parameter TO null
        """,
        "Null cannot be a value of a non-null type 'String'"
      ),
      Arguments.of(
        "a public field cannot be rebound to a table",
        "val invalid = SCALAR_FIELD_RECORD.ID.inTable(SCALAR_FIELD_RECORD)",
        "Unresolved reference 'inTable' on receiver of type 'UniqueColumn<"
      )
    )
  }
}

private class GeneratedScalarFields(
  val adapterDriverResult: Any?,
  val outputDirectory: File,
  val tableSource: String,
  val adapterSource: String
)
