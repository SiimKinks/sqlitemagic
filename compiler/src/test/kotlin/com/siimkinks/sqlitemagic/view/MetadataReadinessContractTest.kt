package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.model.ColumnElement
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

internal class MetadataReadinessContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

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
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
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
                $queryAnnotation val QUERY: CompiledSelect<String, Select1> = error("compile only")
              }
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              isView && step is ViewCollectionStep || !isView && step is ModelCollectionStep ->
                object : ProcessingStep {
                  override fun process(resolver: Resolver): ProcessingStepResult {
                    val result = step.process(resolver)
                    deferred += result is Deferred
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
                  fileName = "LaterMetadata"
                )
                .bufferedWriter()
                .use { writer ->
                  writer.write(
                    """
                      package $PACKAGE
                      class LaterType
                      object LaterMetadata { const val PERSIST_ALL = true;
                    """.trimIndent() +
                        """ const val NAME = "chosen"; const val DEFAULT = "'later'" }"""
                  )
                }
              return Continue
            }
          }
        }
      )
      .isOk()
      .apply {
        val actual = when {
          isView -> environment.viewElements.values.single().let { view ->
            Metadata(
              name = view.viewName,
              columns = view.leaves.map(ViewLeafElement::selectionKey),
              defaults = emptyList(),
              indexes = emptyList()
            )
          }
          else -> environment.tableElements.values.single().let { table ->
            Metadata(
              name = table.tableName,
              columns = table.allColumns.map(ColumnElement::columnName),
              defaults = table.allColumns.map(ColumnElement::defaultValue),
              indexes = environment.indexElements.values.map(IndexElement::name)
            )
          }
        }
        val columnNames = when (mappingSurface) {
          "column", "selection" -> listOf("chosen")
          "policy" -> listOf("value", "optional")
          else -> listOf("value")
        }
        val expected = Metadata(
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
          }
        )
        assertThat(actual).isEqualTo(expected)
        withGeneratedSource(
          fileName = when {
            isView -> "SqliteMagic_MetadataOwner_Dao.kt"
            else -> "SqliteMagic_MetadataOwner_Adapter.kt"
          }
        ) { generated ->
          when {
            isView -> {
              assertThat(generated).contains(""""${expected.name}"""")
              expected.columns.forEach { name -> assertThat(generated).contains("""".$name"""") }
            }
            else -> {
              assertThat(generated).contains("CREATE TABLE IF NOT EXISTS ${expected.name}")
              expected.columns.forEach { name -> assertThat(generated).contains("$name TEXT") }
              if (mappingSurface == "default") {
                assertThat(generated).contains("DEFAULT 'later'")
              }
            }
          }
        }
      }
    assertThat(deferred.first()).isTrue()
    assertThat(deferred.drop(1)).doesNotContain(true)
  }

  private data class Metadata(
    val name: String,
    val columns: List<String>,
    val defaults: List<String>,
    val indexes: List<String>
  )
}
