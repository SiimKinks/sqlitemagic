package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.siimkinks.sqlitemagic.Environment
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

internal class ViewValidationContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `earlier helper readiness preserves generated registry membership`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OrderedValidationViews.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
            import com.siimkinks.sqlitemagic.annotation.Database
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Database(name = "validation.db", version = 1)
            class ValidationDatabase

            @View
            data class HelperView(@ViewColumn("value") val value: String) {
              fun helper(value: LaterType): LaterType = value

              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = error("compile only")
              }
            }

            @View
            data class ReadyView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment) + LaterTypeStep(
            environment = environment,
            contents = """
              package $PACKAGE
              class LaterType
            """.trimIndent()
          )
        }
      )
      .isOk()
      .withGeneratedSource(fileName = "SqliteMagicDatabase.kt") { generated ->
        assertThat(
          generated.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("views.add(") }
            .toList()
        ).containsExactly(
          "views.add(SqliteMagic_ReadyView_Dao.GENERATED_VIEW)",
          "views.add(SqliteMagic_HelperView_Dao.GENERATED_VIEW)"
        )
      }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "instance helper, instance, false",
    "companion helper, companion, false",
    "unmapped property, property, false",
    "mapped property, mapped, true",
    "defaulted constructor property, default, true",
    "secondary constructor parameter, constructor, true",
    "query type argument, query, true",
    "supertype, inherited, true",
    "embedded type, embedded, true"
  )
  fun `validates only the surface needed to collect a view`(
    label: String,
    surface: String,
    requiresDeferral: Boolean
  ) {
    val rounds = mutableListOf<RoundState>()
    val constructor = when (surface) {
      "default" -> """(@ViewColumn("value") val value: String, val helper: LaterType? = null)"""
      else -> "()"
    }
    val supertype = when (surface) {
      "inherited" -> ": LaterType()"
      else -> ""
    }
    val member = when (surface) {
      "instance" -> "fun helper(value: LaterType): LaterType = value"
      "property" -> "val helper: LaterType? = null"
      "mapped" -> """@ViewColumn("helper") var helper: LaterType = error("compile only")"""
      "constructor" -> "constructor(helper: LaterType) : this()"
      "embedded" -> """@Embedded(prefix = "helper_") var helper: LaterType = error("compile only")"""
      else -> ""
    }
    val companionMember = when (surface) {
      "companion" -> "fun helper(value: LaterType): LaterType = value"
      else -> ""
    }
    val queryType = when (surface) {
      "query" -> "LaterType"
      else -> "String"
    }
    val valueProperty = when (surface) {
      "default" -> ""
      else -> """@ViewColumn("value") var value: String = """""
    }
    val laterType = when (surface) {
      "mapped" -> "@com.siimkinks.sqlitemagic.annotation.Table data class LaterType(val value: String)"
      "inherited" -> "open class LaterType"
      "embedded" -> "data class LaterType(val value: String)"
      else -> "class LaterType"
    }

    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ValidationView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            class ValidationView$constructor $supertype {
              $valueProperty
              $member

              companion object {
                $companionMember
                @ViewQuery
                val QUERY: CompiledSelect<$queryType, Select1> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          val steps = viewProcessingSteps(environment)
          steps.map { step ->
            when (step) {
              is ViewCollectionStep -> RecordingCollectionStep(
                delegate = step,
                environment = environment,
                rounds = rounds
              )
              else -> step
            }
          } + LaterTypeStep(
            environment = environment,
            contents = """
              package $PACKAGE
              $laterType
            """.trimIndent()
          )
        }
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_ValidationView_Dao.kt", "ValidationViewTable.kt")

    val expectedFirstRound = when {
      requiresDeferral -> RoundState(
        collected = emptyList(),
        deferred = listOf("ValidationView")
      )
      else -> RoundState(
        collected = listOf("ValidationView"),
        deferred = emptyList()
      )
    }
    assertThat(rounds.first()).isEqualTo(expectedFirstRound)
    assertThat(
      rounds.drop(1)
        .flatMap(RoundState::deferred)
    ).isEmpty()
    assertThat(rounds.flatMap(RoundState::collected)).containsExactly("ValidationView")
  }

  private data class RoundState(
    val collected: List<String>,
    val deferred: List<String>
  )

  private class RecordingCollectionStep(
    private val delegate: ProcessingStep,
    private val environment: Environment,
    private val rounds: MutableList<RoundState>
  ) : ProcessingStep {
    override fun process(resolver: Resolver): ProcessingStepResult {
      val result = delegate.process(resolver)
      rounds += RoundState(
        collected = environment.viewRoundElementsForCurrentRound
          .map(ViewRoundElement::view)
          .map(ViewElement::modelName),
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
