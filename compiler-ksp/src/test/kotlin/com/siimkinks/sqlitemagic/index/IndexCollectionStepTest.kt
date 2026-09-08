package com.siimkinks.sqlitemagic.index

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.index.IndexKind.COMPOSITE
import com.siimkinks.sqlitemagic.index.IndexKind.FIELD
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import com.siimkinks.sqlitemagic.index.SqliteSchema.TEMPORARY as TEMPORARY_SCHEMA

internal class IndexCollectionStepTest : ProcessingStepsTest {
  override val processingSteps = ::indexCollectionProcessingSteps

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
    val titlePath = mockPropertyPath("title")
    val rankPath = mockPropertyPath("rank")

    assertThat(compilation.environment.indexElements.values)
      .containsExactly(
        IndexElement(
          identity = SqliteSchemaIdentity(
            schema = TEMPORARY_SCHEMA,
            identifier = SqliteIdentifier.from("entry_lookup")
          ),
          tableType = tableType,
          tableName = "search_entries",
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
            schema = TEMPORARY_SCHEMA,
            identifier = SqliteIdentifier.from("title_lookup")
          ),
          tableType = tableType,
          tableName = "search_entries",
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
    assertThat(compilation.environment.tableElements.values.single().options)
      .contains(TEMPORARY)
    assertThat(roundSnapshots.map(IndexRoundSnapshot::index))
      .containsExactlyElementsIn(compilation.environment.indexElements.values)
      .inOrder()
    assertThat(roundSnapshots.map(IndexRoundSnapshot::fileNames))
      .containsExactly(listOf("SearchEntry.kt"), listOf("SearchEntry.kt"))
      .inOrder()
    assertThat(roundSnapshots.all(IndexRoundSnapshot::isComplete))
      .isTrue()
  }

  @Test
  fun `collects stable indexes across embedded inherited selective and optional id tables`() {
    val compilation = SqliteMagicCompilation
      .compile(validIndexSources())
      .isOk()

    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly(
        "book_lookup",
        "index_books_title",
        "book_isbn",
        "index_books_author",
        "index_unnamed_composites_id_first_part_second_part",
        "selective_lookup",
        "selective_value",
        "inherited_value",
        "local_value",
        "index_profiles_home_geo_city",
        "index_profiles_work_geo_city",
        "temporary_value",
        "no_id_value",
        "string_value"
      )
      .inOrder()
  }

  @Test
  fun `allows equal raw names in separate schemas and keeps non ASCII identity distinct`() {
    val compilation = SqliteMagicCompilation
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
    val compilation = SqliteMagicCompilation
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
        classpaths = listOf(binaryBase.result.outputDirectory)
      )
      .isOk()

    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("binary_value", "local_value")
      .inOrder()
  }

  @Test
  fun `collects a generated later round index exactly once`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Initial.kt",
          contents = "package $PACKAGE\nclass Initial"
        ),
        processingStepsFactory = { environment ->
          val steps = indexCollectionProcessingSteps(environment)
          steps.dropLast(1) + listOf(LateIndexGenerationStep(environment), steps.last())
        }
      )
      .isOk()

    assertThat(compilation.environment.processingRounds)
      .isAtLeast(2)
    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("late_value")
  }

  @Test
  fun `defers an indexed table until its property type resolves`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DeferredIndexedEntry.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("deferred_indexed_entries")
            data class DeferredIndexedEntry(
              @Index("deferred_value") val value: GeneratedIndexedValue
            )
          """
        ),
        processingStepsFactory = { environment ->
          listOf(GeneratedIndexedValueStep(environment)) + indexCollectionProcessingSteps(environment)
        }
      )
      .isOk()

    assertThat(compilation.environment.processingRounds)
      .isAtLeast(2)
    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("deferred_value")
  }

  @Test
  fun `collects an indexed column from a deferred persistAll false table`() {
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "DeferredSelectiveIndexTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

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
          listOf(GeneratedIndexedValueStep(environment)) + indexCollectionProcessingSteps(environment)
        }
      )
      .isOk()

    assertThat(compilation.environment.indexElements.values.map(IndexElement::name))
      .containsExactly("indexed_value")
  }
}

private class IndexRoundSnapshotStep(
  private val environment: Environment,
  private val snapshots: MutableList<IndexRoundSnapshot>
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    environment.indexRoundElementsForCurrentRound
      .mapTo(
        destination = snapshots,
        transform = { roundIndex ->
          IndexRoundSnapshot(
            index = roundIndex.index,
            fileNames = roundIndex.originatingFiles.files.map(KSFile::fileName),
            isComplete = roundIndex.originatingFiles.isComplete
          )
        }
      )
    return Continue
  }
}

private data class IndexRoundSnapshot(
  val index: IndexElement,
  val fileNames: List<String>,
  val isComplete: Boolean
)

private class LateIndexGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver): ProcessingStepResult {
    if (generated) return Continue
    generated = true
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = false),
        packageName = PACKAGE,
        fileName = "LateIndexedEntry"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("late_indexed_entries")
            data class LateIndexedEntry(
              @Index("late_value") val value: String
            )
          """
        )
      }
    return Continue
  }
}

private class GeneratedIndexedValueStep(
  private val environment: Environment
) : ProcessingStep {
  private var generated = false

  override fun process(resolver: Resolver): ProcessingStepResult {
    if (generated) return Continue
    generated = true
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = false),
        packageName = PACKAGE,
        fileName = "GeneratedIndexedValue"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("generated_index_values")
            data class GeneratedIndexedValue(
              @Id(autoIncrement = false) val value: String
            )
          """
        )
      }
    return Continue
  }
}
