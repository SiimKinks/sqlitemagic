package com.siimkinks.sqlitemagic.index

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexKind.COMPOSITE
import com.siimkinks.sqlitemagic.index.IndexKind.FIELD
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.isOk
import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class IndexCollectionStepTest {
  private val compiler = SqliteMagicCompiler(::indexCollectionProcessingSteps)

  @Test
  fun `collects complete durable field and composite index values`() {
    val roundSnapshots = mutableListOf<IndexRoundSnapshot>()
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "SearchEntry.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Index(value = "entry_lookup", unique = true)
            @Table(value = "search_entries", options = [TEMPORARY])
            data class SearchEntry(
              @Index("title_lookup")
              @Column(belongsToIndex = "entry_lookup")
              val title: String,
              @Column(belongsToIndex = "entry_lookup")
              val rank: Long
            )
          """
        ),
        processingStepsFactory = { environment ->
          indexCollectionProcessingSteps(environment) + IndexRoundSnapshotStep(
            environment = environment,
            snapshots = roundSnapshots
          )
        }
      )
      .isOk()
    val tableType = ClassName(PACKAGE, "SearchEntry")
    val tableIdentity = SqliteSchemaIdentity(
      schema = SqliteSchema.TEMPORARY,
      identifier = SqliteIdentifier("search_entries")
    )
    val titlePath = mockPropertyPath("title")
    val rankPath = mockPropertyPath("rank")

    assertThat(compilation.environment.indexElements.values)
      .containsExactly(
        IndexElement(
          identity = SqliteSchemaIdentity(
            schema = SqliteSchema.TEMPORARY,
            identifier = SqliteIdentifier("entry_lookup")
          ),
          tableIdentity = tableIdentity,
          tableType = tableType,
          kind = COMPOSITE,
          columns = listOf(
            IndexColumnElement(
              propertyPath = titlePath,
              columnName = "title"
            ),
            IndexColumnElement(
              propertyPath = rankPath,
              columnName = "rank"
            )
          ),
          isUnique = true,
          sourcePropertyPath = null
        ),
        IndexElement(
          identity = SqliteSchemaIdentity(
            schema = SqliteSchema.TEMPORARY,
            identifier = SqliteIdentifier("title_lookup")
          ),
          tableIdentity = tableIdentity,
          tableType = tableType,
          kind = FIELD,
          columns = listOf(
            IndexColumnElement(
              propertyPath = titlePath,
              columnName = "title"
            )
          ),
          isUnique = false,
          sourcePropertyPath = titlePath
        )
      )
      .inOrder()
    assertThat(roundSnapshots)
      .containsExactlyElementsIn(
        compilation.environment.indexElements.values.map {
          IndexRoundSnapshot(
            index = it,
            fileNames = listOf("SearchEntry.kt"),
            isComplete = true
          )
        }
      )
      .inOrder()
  }

  @Test
  fun `allows equal raw names in separate schemas and keeps non ASCII identity distinct`() {
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "SchemaScopedIndexes.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table("persistent_entries")
            data class PersistentEntry(
              @Index("shared") val first: String,
              @Index("Ä_name") val second: String,
              @Index("ä_name") val third: String
            )

            @Table(value = "temporary_entries", options = [TEMPORARY])
            data class TemporaryEntry(
              @Index("shared") val value: String
            )
          """
        )
      )
      .isOk()

    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("shared", "Ä_name", "ä_name", "shared")
      .inOrder()
  }

  @Test
  fun `collects an inherited binary index in inherited then local order`() {
    val binaryBase = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "BinaryIndexedBase.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index

            open class BinaryIndexedBase {
              @Index("binary_value")
              var binaryValue: String = ""
            }
          """
        ),
        processingStepsFactory = { emptyList() }
      )
      .isOk()
    val compilation = compiler
      .compile(
        SourceFile.kotlin(
          name = "BinaryIndexedEntry.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("binary_indexed_entries")
            class BinaryIndexedEntry : BinaryIndexedBase() {
              @Index("local_value")
              var localValue: Long = 0
            }
          """
        ),
        classpaths = listOf(binaryBase.outputDirectory)
      )
      .isOk()

    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("binary_value", "local_value")
      .inOrder()
  }

  @Test
  fun `defers indexed tables until their property types resolve`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DeferredIndexedEntries.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("deferred_indexed_entries")
            data class DeferredIndexedEntry(
              @Index("deferred_value") val value: GeneratedIndexedValue
            )

            @Table(value = "deferred_selective_indexes", persistAll = false)
            data class DeferredSelectiveIndexTable(
              @Column val unresolvedValue: GeneratedIndexedValue,
              @Column
              @Index("indexed_value")
              val indexedValue: String
            )
          """
        ),
        processingStepsFactory = { environment ->
          listOf(
            GeneratedSourceStep(
              environment = environment,
              fileName = "GeneratedIndexedValue",
              contents = """
                package $PACKAGE

                import com.siimkinks.sqlitemagic.annotation.Id
                import com.siimkinks.sqlitemagic.annotation.Table

                @Table("generated_index_values")
                data class GeneratedIndexedValue(
                  @Id(autoIncrement = false) val value: String
                )
              """
            )
          ) + indexCollectionProcessingSteps(environment)
        }
      )
      .isOk()

    assertThat(compilation.environment.processingRounds)
      .isAtLeast(2)
    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("deferred_value", "indexed_value")
  }
}

private class IndexRoundSnapshotStep(
  private val environment: Environment,
  private val snapshots: MutableList<IndexRoundSnapshot>
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    environment.indexRoundElementsForCurrentRound
      .mapTo(destination = snapshots) { roundIndex ->
        IndexRoundSnapshot(
          index = roundIndex.index,
          fileNames = roundIndex.originatingFiles.files.map(KSFile::fileName),
          isComplete = roundIndex.originatingFiles.isComplete
        )
      }
    return Continue
  }
}

private data class IndexRoundSnapshot(
  val index: IndexElement,
  val fileNames: List<String>,
  val isComplete: Boolean
)
