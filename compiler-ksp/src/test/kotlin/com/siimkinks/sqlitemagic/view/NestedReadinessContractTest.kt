package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class NestedReadinessContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table inherited leaf, Table, inherited",
    "table embedded leaf, Table, embedded",
    "view inherited leaf, View, inherited",
    "view embedded leaf, View, embedded"
  )
  fun `helper readiness never masks a required nested type`(
    label: String,
    annotation: String,
    surface: String
  ) {
    val deferredRounds = mutableListOf<List<String>>()
    val inheritance = when (surface) {
      "inherited" -> ": KnownBase()"
      else -> ""
    }
    val member = when (surface) {
      "embedded" -> """@Embedded(prefix = "details_") var details: KnownDetails = error("compile only")"""
      else -> ""
    }
    val columnAnnotation = when (annotation) {
      "Table" -> ""
      else -> """@ViewColumn("local")"""
    }
    val queryAnnotation = when (annotation) {
      "Table" -> ""
      else -> "@ViewQuery"
    }
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NestedReadiness.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            open class KnownBase { @ViewColumn("delayed") var delayed: LaterType? = null }
            data class KnownDetails(val delayed: LaterType?)

            @$annotation
            class NestedOwner $inheritance {
              $columnAnnotation var local: String = ""
              $member
              fun helper(value: LaterType): LaterType = value
              companion object {
                $queryAnnotation val QUERY: CompiledSelect<String, Select1> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              annotation == "Table" && step is ModelCollectionStep ||
                  annotation == "View" && step is ViewCollectionStep -> object : ProcessingStep {
                override fun process(resolver: Resolver): ProcessingStepResult {
                  val result = step.process(resolver)
                  deferredRounds += when (result) {
                    is Deferred -> result.symbols
                      .filterIsInstance<KSDeclaration>()
                      .map(KSDeclaration::simpleName)
                      .map(KSName::asString)
                    else -> emptyList()
                  }
                  return result
                }
              }
              else -> step
            }
          } + object : ProcessingStep {
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
                .use { writer ->
                  writer.write(
                    """
                      package $PACKAGE
                      @com.siimkinks.sqlitemagic.annotation.Table
                    """.trimIndent() +
                        " data class LaterType(@com.siimkinks.sqlitemagic.annotation.Id val id: String)"
                  )
                }
              return Continue
            }
          }
        }
      )
      .isOk()
    assertThat(deferredRounds.first()).containsExactly("NestedOwner")
    assertThat(deferredRounds.drop(1).flatten()).isEmpty()
  }
}
