package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.siimkinks.sqlitemagic.AnnotationNames.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.index.indexProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ModelValidationContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "instance function, function, true",
    "companion function, companion, true",
    "excluded body property, excluded, false",
    "companion property, companionProperty, true",
    "persistAll body property, persisted, true",
    "explicit column, column, false",
    "secondary constructor, constructor, true",
    "default constructor parameter, default, true",
    "inherited shape, inherited, true",
    "embedded shape, embedded, true"
  )
  fun `validates only required table types`(
    label: String,
    surface: String,
    persistAll: Boolean
  ) {
    val rounds = mutableListOf<RoundState>()
    val required = surface in setOf("persisted", "column", "constructor", "default", "inherited", "embedded")
    val constructor = when (surface) {
      "default" -> "(helper: LaterType? = null)"
      else -> "()"
    }
    val supertype = when (surface) {
      "inherited" -> ": LaterType()"
      else -> ""
    }
    val member = when (surface) {
      "function" -> "fun helper(value: LaterType): LaterType = value"
      "companion" -> "companion object { fun helper(value: LaterType): LaterType = value }"
      "companionProperty" -> "companion object { val helper: LaterType? = null }"
      "excluded" -> "var helper: LaterType? = null"
      "persisted" -> """var helper: LaterType = error("compile only")"""
      "column" -> """@Column var helper: LaterType = error("compile only")"""
      "constructor" -> "constructor(helper: LaterType) : this()"
      "embedded" -> """@Embedded(prefix = "helper_") var helper: LaterType = error("compile only")"""
      else -> ""
    }
    val laterType = when (surface) {
      "persisted", "column" -> "@com.siimkinks.sqlitemagic.annotation.Table data class LaterType(" +
          "@com.siimkinks.sqlitemagic.annotation.Id val id: Long, val value: String)"
      "inherited" -> """open class LaterType { var inheritedValue: String = "" }"""
      "embedded" -> "data class LaterType(val value: String)"
      else -> "class LaterType"
    }
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ValidationTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table(persistAll = $persistAll)
            @Index
            class ValidationTable$constructor $supertype {
              @Column var value: String = ""
              $member
            }
          """
        ),
        processingStepsFactory = { environment ->
          modelProcessingSteps(environment).map { step ->
            when (step) {
              is ModelCollectionStep -> RecordingStep(
                delegate = step,
                environment = environment,
                rounds = rounds
              )
              else -> step
            }
          } + listOf(
            IndexCollectionStep(environment),
            LaterTypeStep(
              environment = environment,
              contents = """
                package $PACKAGE
                $laterType
              """.trimIndent()
            )
          )
        }
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_ValidationTable_Dao.kt", "ValidationTableTable.kt")

    assertThat(rounds.first()).isEqualTo(
      when {
        required -> RoundState(
          collected = emptyList(),
          deferred = listOf("ValidationTable")
        )
        else -> RoundState(
          collected = listOf("ValidationTable"),
          deferred = emptyList()
        )
      }
    )
    assertThat(rounds.drop(1).flatMap(RoundState::deferred)).isEmpty()
    assertThat(rounds.flatMap(RoundState::collected).filter { it == "ValidationTable" })
      .containsExactly("ValidationTable")
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "class index ignores helper function, function, false",
    "class index ignores ordinary member type, property, false",
    "field index retains column type, field, true",
    "membership retains column type, membership, true",
    "class index retains supertype, inherited, true",
    "class index retains pending metadata, metadata, true"
  )
  fun `index readiness follows its own metadata boundary`(
    label: String,
    surface: String,
    required: Boolean
  ) {
    val results = mutableListOf<List<String>>()
    val supertype = when (surface) {
      "inherited" -> ": LaterType()"
      else -> ""
    }
    val member = when (surface) {
      "function" -> "fun helper(value: LaterType): LaterType = value"
      "field" -> """@Index("field_index") var helper: LaterType? = null"""
      "membership" -> """@Column(belongsToIndex = "validation_index") var helper: LaterType? = null"""
      else -> "var helper: LaterType? = null"
    }
    val indexAnnotation = when (surface) {
      "metadata" -> "@Index(LaterIndexMetadata.NAME)"
      else -> """@Index("validation_index")"""
    }
    // Isolate index readiness from model collection; persisted membership is deliberately deferred until later.
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "IndexValidation.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table $indexAnnotation
            class IndexValidation$supertype { $member }
          """
        ),
        processingStepsFactory = { environment ->
          listOf(
            object : ProcessingStep {
              override fun process(resolver: Resolver): ProcessingStepResult {
                val declarations = resolver
                  .getSymbolsWithAnnotation(TABLE_ANNOTATION)
                  .filterIsInstance<KSClassDeclaration>()
                  .toList()
                environment.setDeferredTableSourceKeys(declarations)
                val result = IndexCollectionStep(environment).process(resolver)
                results += when (result) {
                  is Deferred -> result.symbols
                    .filterIsInstance<KSDeclaration>()
                    .map(KSDeclaration::simpleName)
                    .map(KSName::asString)
                  else -> emptyList()
                }
                // The test records index deferral; its enclosing table is intentionally not collected.
                return Continue
              }
            },
            LaterTypeStep(
              environment = environment,
              contents = """
                package $PACKAGE
                open class LaterType
                object LaterIndexMetadata { const val NAME = "later_index" }
              """.trimIndent()
            )
          )
        }
      )
      .isOk()
    assertThat(results.first().isNotEmpty()).isEqualTo(required)
  }

  @Test
  fun `earlier readiness preserves table positions and index creation dependencies`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TableReadinessOrder.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.annotation.Database
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Database(name = "readiness.db", version = 1)
            class ReadinessDatabase

            @Table @Index
            data class HelperTable(val value: String) {
              fun helper(value: LaterType): LaterType = value
            }

            @Table @Index
            data class ReadyTable(val value: String)
          """
        ),
        processingStepsFactory = { environment ->
          indexProcessingSteps(environment) + LaterTypeStep(
            environment = environment,
            contents = """
              package $PACKAGE
              class LaterType
            """.trimIndent()
          )
        }
      )
      .isOk()
      .apply {
        val tables = environment.tableElements.values.toList()
        assertThat(tables.map(TableElement::declarationOrder)).containsExactly(0, 1)
        assertThat(tables.map(TableElement::modelName)).containsExactly("HelperTable", "ReadyTable")
        assertThat(environment.indexElements.values.map(IndexElement::tableName))
          .containsExactly("helper_table", "ready_table")
        tables.forEach { table ->
          withGeneratedSource(fileName = "SqliteMagic_${table.modelName}_Adapter.kt") { generated ->
            assertThat(generated).contains("tablePosition: Int = ${table.declarationOrder}")
          }
        }
      }
      .withGeneratedSource(fileName = "SqliteMagicDatabase.kt") { generated ->
        assertThat(generated).contains("db.execSQL(SqliteMagic_HelperTable_Adapter.TABLE_SCHEMA)")
        assertThat(generated).contains("db.execSQL(SqliteMagic_ReadyTable_Adapter.TABLE_SCHEMA)")
        assertThat(generated).contains("index_helper_table_value")
        assertThat(generated).contains("index_ready_table_value")
        assertThat(generated.indexOf("createSchemaTables(db = db, temporary = false)"))
          .isLessThan(generated.indexOf("createSchemaIndexes(db = db, temporary = false)"))
      }
  }

  private data class RoundState(
    val collected: List<String>,
    val deferred: List<String>
  )

  private class RecordingStep(
    private val delegate: ProcessingStep,
    private val environment: Environment,
    private val rounds: MutableList<RoundState>
  ) : ProcessingStep {
    override fun process(resolver: Resolver): ProcessingStepResult {
      val result = delegate.process(resolver)
      rounds += RoundState(
        collected = environment.tableRoundElementsForCurrentRound
          .map(TableRoundElement::table)
          .map(TableElement::modelName),
        deferred = when (result) {
          is Deferred -> result.symbols
            .filterIsInstance<KSDeclaration>()
            .map(KSDeclaration::simpleName)
            .map(KSName::asString)
          else -> emptyList()
        }
      )
      return result
    }
  }

  private class LaterTypeStep(
    private val environment: Environment,
    private val contents: String
  ) : ProcessingStep {
    private var generated = false

    override fun process(resolver: Resolver): ProcessingStepResult {
      if (generated) return Continue
      generated = true
      environment.codeGenerator
        .createNewFile(
          dependencies = Dependencies(aggregating = false),
          packageName = PACKAGE,
          fileName = "LaterType"
        )
        .bufferedWriter()
        .use { it.write(contents) }
      return Continue
    }
  }
}
