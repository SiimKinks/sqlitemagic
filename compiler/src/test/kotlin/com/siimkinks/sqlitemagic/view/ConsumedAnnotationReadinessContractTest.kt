package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.model.AutoIncrementMode
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.IdElement
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

internal class ConsumedAnnotationReadinessContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

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
          @ViewQuery val QUERY: CompiledSelect<String, Select1> = error("compile only")
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
    val firstRoundMetadata = mutableListOf<Metadata>()
    val expected = Metadata(
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
            import kotlin.reflect.KClass
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
            import com.siimkinks.sqlitemagic.annotation.*

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
                object : ProcessingStep {
                  override fun process(resolver: Resolver): ProcessingStepResult {
                    val result = step.process(resolver)
                    collectionResults += result == Continue
                    return result
                  }
                }
              step is IndexCollectionStep -> object : ProcessingStep {
                override fun process(resolver: Resolver): ProcessingStepResult {
                  val result = step.process(resolver)
                  indexResults += result == Continue
                  if (indexResults.size == 1) {
                    metadata(
                      environment = environment,
                      isView = isView
                    )?.let(firstRoundMetadata::add)
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
                  fileName = "LaterMetadata"
                )
                .bufferedWriter()
                .use { writer ->
                  writer.write(
                    """
                      package $PACKAGE
                      class LaterType
                      object LaterMetadata { const val NAME = "later" }
                    """.trimIndent()
                  )
                }
              return Continue
            }
          }
        }
      )
      .isOk()
      .apply {
        assertThat(
          metadata(
            environment = environment,
            isView = isView
          )
        ).isEqualTo(expected)
        withGeneratedSource(
          fileName = when {
            isView -> "SqliteMagic_ConsumedOwner_Dao.kt"
            else -> "SqliteMagic_ConsumedOwner_Adapter.kt"
          }
        ) { generated ->
          when {
            isView -> assertThat(generated).contains("""".selected_value"""")
            else -> {
              assertThat(generated).contains("CREATE TABLE IF NOT EXISTS consumed_owner")
              assertThat(generated).contains("stored_value TEXT")
            }
          }
        }
      }
    assertThat(collectionResults.first()).isTrue()
    assertThat(indexResults.first()).isTrue()
    assertThat(firstRoundMetadata).containsExactly(expected)
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "table defaults, Table, omitted, default",
    "table explicit enabled ID, Table, true, default",
    "table explicit disabled ID, Table, false, default",
    "table enum array, Table, false, array",
    "view defaults, View, omitted, default",
    "view enum array, View, omitted, array"
  )
  fun `consumed defaults explicit ID and option arrays preserve metadata`(
    label: String,
    ownerAnnotation: String,
    idArgument: String,
    optionsArgument: String
  ) {
    val isView = ownerAnnotation == "View"
    val options = when {
      optionsArgument == "default" -> ""
      isView -> "(options = [ViewOption.TEMPORARY])"
      else -> "(options = [TableOption.TEMPORARY, TableOption.WITHOUT_ROWID])"
    }
    val member = when {
      isView -> """@ViewColumn("selected_value") var value: String = """""
      idArgument == "omitted" -> "@Id var id: Long = 0"
      else -> "@Id(autoIncrement = $idArgument) var id: Long = 0"
    }
    val query = when {
      isView -> """
        companion object {
          @ViewQuery val QUERY: CompiledSelect<String, Select1> = error("compile only")
        }
      """
      else -> ""
    }
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AnnotationDefaults.kt",
          contents = """
            package $PACKAGE
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
            import com.siimkinks.sqlitemagic.annotation.*

            @$ownerAnnotation$options
            class AnnotationDefaults {
              $member
              $query
            }
          """
        )
      )
      .isOk()
      .apply {
        val actual = when {
          isView -> environment.viewElements.values.single().let { view ->
            DefaultMetadata(
              name = view.viewName,
              options = when (view.identity.schema) {
                SqliteSchema.TEMPORARY -> setOf("TEMPORARY")
                else -> emptySet()
              },
              columns = view.leaves.map(ViewLeafElement::selectionKey),
              id = null
            )
          }
          else -> environment.tableElements.values.single().let { table ->
            DefaultMetadata(
              name = table.tableName,
              options = table.options.map(Enum<*>::name)
                .toSet(),
              columns = table.allColumns.map(ColumnElement::columnName),
              id = table.allColumns.single().id
            )
          }
        }
        val expected = DefaultMetadata(
          name = "annotation_defaults",
          options = when {
            optionsArgument == "default" -> emptySet()
            isView -> setOf("TEMPORARY")
            else -> setOf("TEMPORARY", "WITHOUT_ROWID")
          },
          columns = when {
            isView -> listOf("selected_value")
            else -> listOf("id")
          },
          id = when {
            isView -> null
            else -> IdElement(
              autoIncrementMode = when (idArgument) {
                "true" -> AutoIncrementMode.ENABLED
                "false" -> AutoIncrementMode.DISABLED
                else -> AutoIncrementMode.AUTOMATIC
              },
              isAutoIncrement = idArgument != "false",
              canAssignGeneratedId = true
            )
          }
        )
        assertThat(actual).isEqualTo(expected)
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
          @ViewQuery val QUERY: CompiledSelect<String, Select1> = error("compile only")
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
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
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
                object : ProcessingStep {
                  override fun process(resolver: Resolver): ProcessingStepResult {
                    val result = step.process(resolver)
                    ready += result == Continue
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
                      object LaterMetadata { const val NAME = "chosen" }
                    """.trimIndent()
                  )
                }
              return Continue
            }
          }
        }
      )
      .isOk()
      .apply {
        val columns = when (surface) {
          "inherited" -> listOf("chosen", "local")
          else -> listOf("local", "chosen")
        }
        assertThat(
          metadata(
            environment = environment,
            isView = isView
          )
        ).isEqualTo(
          Metadata(
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
          fileName = when {
            isView -> "SqliteMagic_RequiredOwner_Dao.kt"
            else -> "SqliteMagic_RequiredOwner_Adapter.kt"
          }
        ) { generated ->
          when {
            isView -> assertThat(generated).contains("""".chosen"""")
            else -> assertThat(generated).contains("chosen TEXT")
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
          @ViewQuery val QUERY: CompiledSelect<String, Select1> = error("compile only")
        }
      """
      else -> ""
    }
    val firstDeferredNames = mutableListOf<List<String>>()
    val firstRoundChildren = mutableListOf<Metadata>()
    val expected = Metadata(
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

    fun childMetadata(environment: Environment): Metadata? = when {
      isView -> metadata(
        environment = environment,
        isView = true
      )
      else -> environment.tableElements.values
        .firstOrNull { table -> table.tableName == "child_owner" }
        ?.let { table ->
          Metadata(
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
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
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
              is ModelCollectionStep -> object : ProcessingStep {
                override fun process(resolver: Resolver): ProcessingStepResult {
                  val result = step.process(resolver)
                  if (firstDeferredNames.isEmpty()) {
                    firstDeferredNames += when (result) {
                      is Deferred -> result.symbols
                        .filterIsInstance<KSDeclaration>()
                        .map(KSDeclaration::simpleName)
                        .map(KSName::asString)
                      else -> emptyList()
                    }
                  }
                  return result
                }
              }
              is IndexCollectionStep -> object : ProcessingStep {
                override fun process(resolver: Resolver): ProcessingStepResult {
                  val result = step.process(resolver)
                  if (!observedFirstRound) {
                    observedFirstRound = true
                    childMetadata(environment)?.let(firstRoundChildren::add)
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
                  fileName = "LaterMetadata"
                )
                .bufferedWriter()
                .use { writer ->
                  writer.write(
                    """
                      package $PACKAGE
                      object LaterMetadata { const val NAME = "base_name" }
                    """.trimIndent()
                  )
                }
              return Continue
            }
          }
        }
      )
      .isOk()
      .apply {
        assertThat(childMetadata(environment)).isEqualTo(expected)
        withGeneratedSource(
          fileName = when {
            isView -> "SqliteMagic_ChildOwner_Dao.kt"
            else -> "SqliteMagic_ChildOwner_Adapter.kt"
          }
        ) { generated ->
          when {
            isView -> assertThat(generated).contains("""".selected_base"""")
            else -> assertThat(generated).contains("base_value TEXT")
          }
        }
      }
    assertThat(firstDeferredNames.single()).containsExactly("KnownBase")
    assertThat(firstRoundChildren).containsExactly(expected)
  }

  private fun metadata(
    environment: Environment,
    isView: Boolean
  ): Metadata? = when {
    isView -> environment.viewElements.values.singleOrNull()?.let { view ->
      Metadata(
        name = view.viewName,
        columns = view.leaves.map(ViewLeafElement::selectionKey),
        defaults = emptyList(),
        indexes = environment.indexElements.values.map(IndexElement::name),
        query = view.query.propertyName
      )
    }
    else -> environment.tableElements.values.singleOrNull()?.let { table ->
      Metadata(
        name = table.tableName,
        columns = table.allColumns.map(ColumnElement::columnName),
        defaults = table.allColumns.map(ColumnElement::defaultValue),
        indexes = environment.indexElements.values.map(IndexElement::name),
        query = null
      )
    }
  }

  private data class Metadata(
    val name: String,
    val columns: List<String>,
    val defaults: List<String>,
    val indexes: List<String>,
    val query: String?
  )
}
