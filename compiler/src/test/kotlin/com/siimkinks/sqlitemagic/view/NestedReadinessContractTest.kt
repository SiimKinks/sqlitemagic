package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.deferredNames
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.observing
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class NestedReadinessContractTest {
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
            import com.siimkinks.sqlitemagic.SelectQuery
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
                $queryAnnotation val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              annotation == "Table" && step is ModelCollectionStep ||
                  annotation == "View" && step is ViewCollectionStep ->
                step.observing { deferredRounds += it.deferredNames() }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterType",
            contents = """
              package $PACKAGE
              @com.siimkinks.sqlitemagic.annotation.Table
              data class LaterType(@com.siimkinks.sqlitemagic.annotation.Id val id: String)
            """
          )
        }
      )
      .isOk()
    assertThat(deferredRounds.first()).containsExactly("NestedOwner")
    assertThat(deferredRounds.drop(1).flatten()).isEmpty()
  }
}
