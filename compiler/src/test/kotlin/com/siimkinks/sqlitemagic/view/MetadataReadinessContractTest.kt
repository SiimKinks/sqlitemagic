package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.MappingMetadata
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.mappingMetadata
import com.siimkinks.sqlitemagic.utils.observing
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class MetadataReadinessContractTest {
  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table persistence policy, policy",
    "table name, table",
    "column name, column",
    "column default, default",
    "view name, view",
    "view selection key, selection",
    "class index metadata, index",
    "composite membership, membership",
    "table metadata without delayed helper, policy_ready"
  )
  fun `helper readiness retains pending mapping annotation values`(
    label: String,
    surface: String
  ) {
    val mappingSurface = surface.removeSuffix("_ready")
    val helperType = when {
      surface.endsWith("_ready") -> "String"
      else -> "LaterType"
    }
    val isView = mappingSurface in setOf("view", "selection")
    val deferred = mutableListOf<Boolean>()
    val rootAnnotation = when (mappingSurface) {
      "policy" -> "@Table(persistAll = LaterMetadata.PERSIST_ALL)"
      "table" -> "@Table(LaterMetadata.NAME)"
      "view" -> "@View(LaterMetadata.NAME)"
      "selection" -> "@View"
      "index" -> "@Table @Index(LaterMetadata.NAME)"
      "membership" -> """@Table @Index("chosen")"""
      else -> "@Table"
    }
    val propertyAnnotation = when (mappingSurface) {
      "column" -> "@Column(LaterMetadata.NAME)"
      "default" -> "@Column(defaultValue = LaterMetadata.DEFAULT)"
      "selection" -> "@ViewColumn(LaterMetadata.NAME)"
      "view" -> """@ViewColumn("value")"""
      "index" -> """@Column(belongsToIndex = "chosen")"""
      "membership" -> "@Column(belongsToIndex = LaterMetadata.NAME)"
      else -> "@Column"
    }
    val queryAnnotation = when {
      isView -> "@ViewQuery"
      else -> ""
    }
    val optionalProperty = when (mappingSurface) {
      "policy" -> """var optional: String = """""
      else -> ""
    }
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MetadataReadiness.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            $rootAnnotation
            class MetadataOwner {
              $propertyAnnotation var value: String = ""
              $optionalProperty
              fun helper(value: $helperType): $helperType = value
              companion object {
                $queryAnnotation val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              isView && step is ViewCollectionStep || !isView && step is ModelCollectionStep ->
                step.observing { deferred += it is Deferred }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterMetadata",
            contents = """
              package $PACKAGE
              class LaterType
              object LaterMetadata {
                const val PERSIST_ALL = true
                const val NAME = "chosen"
                const val DEFAULT = "'later'"
              }
            """
          )
        }
      )
      .isOk()
      .apply {
        val columnNames = when (mappingSurface) {
          "column", "selection" -> listOf("chosen")
          "policy" -> listOf("value", "optional")
          else -> listOf("value")
        }
        val expected = MappingMetadata(
          name = when (mappingSurface) {
            "table", "view" -> "chosen"
            else -> "metadata_owner"
          },
          columns = columnNames,
          defaults = when {
            isView -> emptyList()
            mappingSurface == "default" -> listOf("'later'")
            else -> columnNames.map { "''" }
          },
          indexes = when (mappingSurface) {
            "index", "membership" -> listOf("chosen")
            else -> emptyList()
          },
          query = when {
            isView -> "QUERY"
            else -> null
          }
        )
        assertThat(environment.mappingMetadata(isView)).isEqualTo(expected)
        withGeneratedSource(
          when {
            isView -> "SqliteMagic_MetadataOwner_Dao.kt"
            else -> "SqliteMagic_MetadataOwner_Adapter.kt"
          }
        ) { generated ->
          when {
            isView -> generated.assertContains(
              """viewName = "${expected.name}"""",
              "columns = listOf(${expected.columns.joinToString { """"$it"""" }})"
            )
            else -> {
              generated.assertContains("CREATE TABLE IF NOT EXISTS ${expected.name}")
              expected.columns.forEach { generated.assertContains("$it TEXT") }
              if (mappingSurface == "default") {
                generated.assertContains("DEFAULT 'later'")
              }
            }
          }
        }
      }
    assertThat(deferred.first()).isTrue()
    assertThat(deferred.drop(1)).doesNotContain(true)
  }
}
