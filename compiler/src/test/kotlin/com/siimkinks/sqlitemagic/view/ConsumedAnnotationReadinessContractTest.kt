package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.IdElement
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.MappingMetadata
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.deferredNames
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.mappingMetadata
import com.siimkinks.sqlitemagic.utils.observing
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ConsumedAnnotationReadinessContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table root scalar, Table, root, scalar",
    "table root array, Table, root, array",
    "table root nested, Table, root, nested",
    "table root class, Table, root, class",
    "view root scalar, View, root, scalar",
    "table helper scalar, Table, helper, scalar",
    "view helper scalar, View, helper, scalar",
    "table persisted property, Table, property, scalar",
    "view persisted property, View, property, scalar",
    "table ignored property, Table, ignored, scalar",
    "view ignored property, View, ignored, scalar",
    "view query, View, query, scalar",
    "table field index, Table, field_index, array",
    "table class index, Table, class_index, scalar",
    "table annotated helper signature, Table, helper, signature",
    "view annotated helper signature, View, helper, signature",
    "table inherited unrelated argument, Table, inherited, scalar",
    "view inherited unrelated argument, View, inherited, scalar",
    "table embedded unrelated argument, Table, embedded, scalar",
    "view embedded unrelated argument, View, embedded, scalar"
  )
  fun `unrelated annotation inputs do not delay consumed metadata`(
    label: String,
    ownerAnnotation: String,
    surface: String,
    argument: String
  ) {
    val isView = ownerAnnotation == "View"
    val probe = when (argument) {
      "array" -> "@Probe(names = [LaterMetadata.NAME])"
      "nested" -> "@Probe(nested = NestedProbe(LaterMetadata.NAME))"
      "class" -> "@Probe(type = LaterType::class)"
      "signature" -> """@Probe(value = "ready")"""
      else -> "@Probe(value = LaterMetadata.NAME)"
    }
    val rootProbe = when (surface) {
      "root", "class_index", "inherited", "embedded" -> probe
      else -> ""
    }
    val rootIndex = when (surface) {
      "class_index" -> """@Index(value = "lookup", unique = true)"""
      else -> ""
    }
    val propertyProbe = when (surface) {
      "property", "field_index" -> probe
      else -> ""
    }
    val mapping = when {
      isView -> """@ViewColumn("selected_value")"""
      surface == "class_index" -> """@Column(value = "stored_value", belongsToIndex = "lookup")"""
      else -> """@Column(value = "stored_value")"""
    }
    val fieldIndex = when (surface) {
      "field_index" -> """@Index(value = "lookup", unique = true)"""
      else -> ""
    }
    val helperProbe = when (surface) {
      "helper" -> probe
      else -> ""
    }
    val helperType = when (argument) {
      "signature" -> "LaterType"
      else -> "String"
    }
    val ignoredProbe = when (surface) {
      "ignored" -> probe
      else -> ""
    }
    val queryProbe = when (surface) {
      "query" -> probe
      else -> ""
    }
    val query = when {
      isView -> """
        companion object {
          $queryProbe
          @ViewQuery val QUERY: SelectQuery<String, *> = error("compile only")
        }
      """
      else -> ""
    }
    val inheritance = when (surface) {
      "inherited" -> ": KnownBase()"
      else -> ""
    }
    val embedded = when (surface) {
      "embedded" -> "@Embedded var details: KnownDetails = KnownDetails()"
      else -> ""
    }
    val detailMapping = when {
      isView -> """@ViewColumn("selected_detail")"""
      else -> """@Column("stored_detail")"""
    }
    val detailKey = when {
      isView -> "selected_detail"
      else -> "stored_detail"
    }
    val valueKey = when {
      isView -> "selected_value"
      else -> "stored_value"
    }
    val columnKeys = when (surface) {
      "inherited" -> listOf(detailKey, valueKey)
      "embedded" -> listOf(valueKey, detailKey)
      else -> listOf(valueKey)
    }
    val collectionResults = mutableListOf<Boolean>()
    val indexResults = mutableListOf<Boolean>()
    val firstRoundMetadata = mutableListOf<MappingMetadata>()
    val expected = MappingMetadata(
      name = "consumed_owner",
      columns = columnKeys,
      defaults = when {
        isView -> emptyList()
        else -> columnKeys.map { "''" }
      },
      indexes = when (surface) {
        "class_index", "field_index" -> listOf("lookup")
        else -> emptyList()
      },
      query = when {
        isView -> "QUERY"
        else -> null
      }
    )
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ConsumedOwner.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.*
            import kotlin.reflect.KClass

            annotation class NestedProbe(val value: String = "")
            @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
            annotation class Probe(
              val value: String = "",
              val names: Array<String> = [],
              val nested: NestedProbe = NestedProbe(),
              val type: KClass<*> = String::class
            )

            open class KnownBase {
              $detailMapping var detail: String = ""
            }
            class KnownDetails {
              $detailMapping var detail: String = ""
            }

            $rootProbe
            $rootIndex
            @$ownerAnnotation
            class ConsumedOwner $inheritance {
              $propertyProbe
              $fieldIndex
              $mapping var value: String = ""
              $embedded
              $ignoredProbe
              @IgnoreColumn var ignored: String = ""
              $helperProbe
              fun helper(value: $helperType): $helperType = value
              $query
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              isView && step is ViewCollectionStep || !isView && step is ModelCollectionStep ->
                step.observing { collectionResults += it == Continue }
              step is IndexCollectionStep -> step.observing { result ->
                indexResults += result == Continue
                if (indexResults.size == 1) {
                  environment
                    .mappingMetadata(isView)
                    ?.let(firstRoundMetadata::add)
                }
              }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterMetadata",
            contents = """
              package $PACKAGE
              class LaterType
              object LaterMetadata { const val NAME = "later" }
            """
          )
        }
      )
      .isOk()
      .apply {
        assertThat(environment.mappingMetadata(isView)).isEqualTo(expected)
        withGeneratedSource(
          when {
            isView -> "SqliteMagic_ConsumedOwner_Dao.kt"
            else -> "SqliteMagic_ConsumedOwner_Adapter.kt"
          }
        ) {
          when {
            isView -> it
              .unwrapped()
              .assertContains("columns = listOf(${columnKeys.joinToString { key -> """"$key"""" }})")
            else -> it.assertContains("CREATE TABLE IF NOT EXISTS consumed_owner", "stored_value TEXT")
          }
        }
      }
    assertThat(collectionResults.first()).isTrue()
    assertThat(indexResults.first()).isTrue()
    assertThat(firstRoundMetadata).containsExactly(expected)
  }

  @Test
  fun `consumed defaults explicit ID and option arrays preserve metadata`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "AnnotationDefaults.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.*

            @Table
            class TableDefaults {
              @Id var id: Long = 0
            }

            @Table
            class EnabledIdTable {
              @Id(autoIncrement = true) var id: Long = 0
            }

            @Table
            class DisabledIdTable {
              @Id(autoIncrement = false) var id: Long = 0
            }

            @Table(options = [TableOption.TEMPORARY, TableOption.WITHOUT_ROWID])
            class TableOptions {
              @Id(autoIncrement = false) var id: Long = 0
            }

            @View
            class ViewDefaults {
              @ViewColumn("selected_value") var value: String = ""
              companion object {
                @ViewQuery val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }

            @View(options = [ViewOption.TEMPORARY])
            class ViewOptions {
              @ViewColumn("selected_value") var value: String = ""
              companion object {
                @ViewQuery val QUERY: SelectQuery<String, *> = error("compile only")
              }
            }
          """
        )
      )
      .isOk()
      .apply {
        val tables = environment.tableElements.values.associate { table ->
          table.modelName to DefaultMetadata(
            name = table.tableName,
            options = table.options
              .map(Enum<*>::name)
              .toSet(),
            columns = table.allColumns.map(ColumnElement::columnName),
            id = table.allColumns.single().id
          )
        }
        val views = environment.viewElements.values.associate { view ->
          view.modelName to DefaultMetadata(
            name = view.viewName,
            options = when (view.identity.schema) {
              TEMPORARY -> setOf("TEMPORARY")
              else -> emptySet()
            },
            columns = view.leaves.map(ViewLeafElement::selectionKey),
            id = null
          )
        }
        assertThat(tables + views).isEqualTo(
          mapOf(
            "TableDefaults" to DefaultMetadata(
              name = "table_defaults",
              options = emptySet(),
              columns = listOf("id"),
              id = IdElement(
                isAutoIncrement = true,
                canAssignGeneratedId = true
              )
            ),
            "EnabledIdTable" to DefaultMetadata(
              name = "enabled_id_table",
              options = emptySet(),
              columns = listOf("id"),
              id = IdElement(
                isAutoIncrement = true,
                canAssignGeneratedId = true
              )
            ),
            "DisabledIdTable" to DefaultMetadata(
              name = "disabled_id_table",
              options = emptySet(),
              columns = listOf("id"),
              id = IdElement(
                isAutoIncrement = false,
                canAssignGeneratedId = true
              )
            ),
            "TableOptions" to DefaultMetadata(
              name = "table_options",
              options = setOf("TEMPORARY", "WITHOUT_ROWID"),
              columns = listOf("id"),
              id = IdElement(
                isAutoIncrement = false,
                canAssignGeneratedId = true
              )
            ),
            "ViewDefaults" to DefaultMetadata(
              name = "view_defaults",
              options = emptySet(),
              columns = listOf("selected_value"),
              id = null
            ),
            "ViewOptions" to DefaultMetadata(
              name = "view_options",
              options = setOf("TEMPORARY"),
              columns = listOf("selected_value"),
              id = null
            )
          )
        )
      }
  }

  private data class DefaultMetadata(
    val name: String,
    val options: Set<String>,
    val columns: List<String>,
    val id: IdElement?
  )

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table inherited required constant, Table, inherited",
    "table embedded required constant, Table, embedded",
    "view inherited required constant, View, inherited",
    "view embedded required constant, View, embedded"
  )
  fun `known nested types retain required generated annotation constants`(
    label: String,
    ownerAnnotation: String,
    surface: String
  ) {
    val isView = ownerAnnotation == "View"
    val delayedMapping = when {
      isView -> "@ViewColumn(LaterMetadata.NAME)"
      else -> "@Column(LaterMetadata.NAME)"
    }
    val localMapping = when {
      isView -> """@ViewColumn("local")"""
      else -> ""
    }
    val inheritance = when (surface) {
      "inherited" -> ": KnownBase()"
      else -> ""
    }
    val embedded = when (surface) {
      "embedded" -> "@Embedded var details: KnownDetails = KnownDetails()"
      else -> ""
    }
    val query = when {
      isView -> """
        companion object {
          @ViewQuery val QUERY: SelectQuery<String, *> = error("compile only")
        }
      """
      else -> ""
    }
    val ready = mutableListOf<Boolean>()
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RequiredOwner.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.*

            open class KnownBase { $delayedMapping var delayed: String = "" }
            class KnownDetails { $delayedMapping var delayed: String = "" }

            @$ownerAnnotation
            class RequiredOwner $inheritance {
              $localMapping var local: String = ""
              $embedded
              $query
            }
          """
        ),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).map { step ->
            when {
              isView && step is ViewCollectionStep || !isView && step is ModelCollectionStep ->
                step.observing { ready += it == Continue }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterMetadata",
            contents = """
              package $PACKAGE
              object LaterMetadata { const val NAME = "chosen" }
            """
          )
        }
      )
      .isOk()
      .apply {
        val columns = when (surface) {
          "inherited" -> listOf("chosen", "local")
          else -> listOf("local", "chosen")
        }
        assertThat(environment.mappingMetadata(isView)).isEqualTo(
          MappingMetadata(
            name = "required_owner",
            columns = columns,
            defaults = when {
              isView -> emptyList()
              else -> columns.map { "''" }
            },
            indexes = emptyList(),
            query = when {
              isView -> "QUERY"
              else -> null
            }
          )
        )
        withGeneratedSource(
          when {
            isView -> "SqliteMagic_RequiredOwner_Dao.kt"
            else -> "SqliteMagic_RequiredOwner_Adapter.kt"
          }
        ) {
          when {
            isView -> it
              .unwrapped()
              .assertContains("columns = listOf(${columns.joinToString { key -> """"$key"""" }})")
            else -> it.assertContains("chosen TEXT")
          }
        }
      }
    assertThat(ready.first()).isFalse()
    assertThat(ready.drop(1)).doesNotContain(false)
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table ancestor root metadata, Table",
    "view ancestor root metadata, View"
  )
  fun `ancestor table root arguments do not delay a ready child`(
    label: String,
    ownerAnnotation: String
  ) {
    val isView = ownerAnnotation == "View"
    val baseMapping = when {
      isView -> """@ViewColumn("selected_base")"""
      else -> ""
    }
    val localMapping = when {
      isView -> """@ViewColumn("selected_local")"""
      else -> ""
    }
    val query = when {
      isView -> """
        companion object {
          @ViewQuery val QUERY: SelectQuery<String, *> = error("compile only")
        }
      """
      else -> ""
    }
    val firstDeferredNames = mutableListOf<List<String>>()
    val firstRoundChildren = mutableListOf<MappingMetadata>()
    val expected = MappingMetadata(
      name = "child_owner",
      columns = when {
        isView -> listOf("selected_base", "selected_local")
        else -> listOf("base_value", "local_value")
      },
      defaults = when {
        isView -> emptyList()
        else -> listOf("''", "''")
      },
      indexes = emptyList(),
      query = when {
        isView -> "QUERY"
        else -> null
      }
    )

    fun childMetadata(environment: Environment) = when {
      isView -> environment.mappingMetadata(isView = true)
      else -> environment.tableElements.values
        .firstOrNull { it.tableName == "child_owner" }
        ?.let { table ->
          MappingMetadata(
            name = table.tableName,
            columns = table.allColumns.map(ColumnElement::columnName),
            defaults = table.allColumns.map(ColumnElement::defaultValue),
            indexes = emptyList(),
            query = null
          )
        }
    }
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ChildOwner.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.*

            @Table(LaterMetadata.NAME)
            open class KnownBase {
              $baseMapping var baseValue: String = ""
            }

            @$ownerAnnotation
            class ChildOwner : KnownBase() {
              $localMapping var localValue: String = ""
              $query
            }
          """
        ),
        processingStepsFactory = { environment ->
          var observedFirstRound = false
          viewProcessingSteps(environment).map { step ->
            when (step) {
              is ModelCollectionStep -> step.observing { result ->
                if (firstDeferredNames.isEmpty()) {
                  firstDeferredNames += result.deferredNames()
                }
              }
              is IndexCollectionStep -> step.observing {
                if (!observedFirstRound) {
                  observedFirstRound = true
                  childMetadata(environment)?.let(firstRoundChildren::add)
                }
              }
              else -> step
            }
          } + GeneratedSourceStep(
            environment = environment,
            fileName = "LaterMetadata",
            contents = """
              package $PACKAGE
              object LaterMetadata { const val NAME = "base_name" }
            """
          )
        }
      )
      .isOk()
      .apply {
        assertThat(childMetadata(environment)).isEqualTo(expected)
        withGeneratedSource(
          when {
            isView -> "SqliteMagic_ChildOwner_Dao.kt"
            else -> "SqliteMagic_ChildOwner_Adapter.kt"
          }
        ) {
          when {
            isView -> it
              .unwrapped()
              .assertContains("columns = listOf(${expected.columns.joinToString { key -> """"$key"""" }})")
            else -> it.assertContains("base_value TEXT")
          }
        }
      }
    assertThat(firstDeferredNames.single()).containsExactly("KnownBase")
    assertThat(firstRoundChildren).containsExactly(expected)
  }
}
