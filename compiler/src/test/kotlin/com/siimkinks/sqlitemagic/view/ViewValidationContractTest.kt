package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.RoundState
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.recordingRounds
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ViewValidationContractTest {
  @Test
  fun `earlier helper readiness preserves generated registry membership`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OrderedValidationViews.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
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
                val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }

            @View
            data class ReadyView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment) + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterType",
            contents = """
              package $PACKAGE
              class LaterType
            """
          )
        }
      )
      .isOk()
      .withGeneratedSource("SqliteMagicDatabase.kt") { generated ->
        assertThat(
          generated
            .lineSequence()
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

            import com.siimkinks.sqlitemagic.SelectQuery
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
                val QUERY: SelectQuery<$queryType, *> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when (step) {
              is ViewCollectionStep -> step.recordingRounds(rounds) {
                environment.viewRoundElementsForCurrentRound
                  .map(ViewRoundElement::view)
                  .map(ViewElement::modelName)
              }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterType",
            contents = """
              package $PACKAGE
              $laterType
            """
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
      rounds
        .drop(1)
        .flatMap(RoundState::deferred)
    ).isEmpty()
    assertThat(rounds.flatMap(RoundState::collected)).containsExactly("ValidationView")
  }
}
