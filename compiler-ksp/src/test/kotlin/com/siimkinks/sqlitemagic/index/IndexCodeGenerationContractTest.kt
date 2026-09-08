package com.siimkinks.sqlitemagic.index

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class IndexCodeGenerationContractTest : ProcessingStepsTest {
  override val processingSteps = ::indexProcessingSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `generates field and composite indexes in physical column order`() {
    SqliteMagicCompilation
      .compile(validIndexSources())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt", "SqliteMagic_Book_Adapter.kt")
      .withGeneratedSource("SqliteMagic_Book_Adapter.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "id INTEGER PRIMARY KEY",
          "title TEXT DEFAULT ''",
          "isbn TEXT UNIQUE",
          "author TEXT DEFAULT ''",
          "edition INTEGER DEFAULT 0"
        )
      }
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE INDEX",
          "index_books_title",
          "index_books_author",
          "CREATE UNIQUE INDEX",
          "book_isbn",
          "book_lookup"
        )
        generatedSource.assertContainsInOrder(
          "book_lookup",
          "author",
          "edition"
        )
      }
  }

  @Test
  fun `generates unnamed composites, selective columns, embedded leaves, and inherited leaves`() {
    SqliteMagicCompilation
      .compile(validIndexSources())
      .isOk()
      .assertGeneratedSources(
        "SqliteMagicDatabase.kt",
        "SqliteMagic_Profile_Adapter.kt",
        "SqliteMagic_InheritedEntry_Adapter.kt",
        "SqliteMagic_SelectiveEntry_Adapter.kt",
        "SqliteMagic_NoIdEntry_Adapter.kt",
        "SqliteMagic_StringIdEntry_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "index_unnamed_composites_id_first_part_second_part",
          "selective_lookup",
          "selective_value",
          "index_profiles_home_geo_city",
          "index_profiles_work_geo_city",
          "inherited_value",
          "local_value",
          "no_id_value",
          "string_value"
        )
        generatedSource.assertContainsInOrder(
          "index_unnamed_composites_id_first_part_second_part",
          "id",
          "first_part",
          "second_part"
        )
        generatedSource.assertContainsInOrder(
          "index_profiles_home_geo_city",
          "index_profiles_work_geo_city",
          "inherited_value",
          "local_value"
        )
      }
      .withGeneratedSource("SqliteMagic_Profile_Adapter.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "id TEXT PRIMARY KEY",
          "home_geo_city TEXT DEFAULT ''",
          "home_geo_country TEXT DEFAULT ''",
          "work_geo_city TEXT DEFAULT ''",
          "work_geo_country TEXT DEFAULT ''"
        )
      }
      .withGeneratedSource("SqliteMagic_InheritedEntry_Adapter.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "inherited_value TEXT DEFAULT ''",
          "local_value INTEGER DEFAULT 0"
        )
      }
      .withGeneratedSource("SqliteMagic_SelectiveEntry_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("persisted_value")
        generatedSource.assertDoesNotContain("excluded_value")
      }
      .withGeneratedSource("SqliteMagic_NoIdEntry_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("CREATE TABLE IF NOT EXISTS no_id_entries (value TEXT DEFAULT '')")
        generatedSource.assertDoesNotContain("PRIMARY KEY")
      }
      .withGeneratedSource("SqliteMagic_StringIdEntry_Adapter.kt") { generatedSource ->
        generatedSource.assertContains("id TEXT PRIMARY KEY")
      }
  }

  @Test
  fun `routes temporary indexes after temporary tables and excludes them from persistent schema`() {
    SqliteMagicCompilation
      .compile(
        validIndexSources(),
        kspOptions = mapOf(
          "sqlitemagic.migrate.debug" to "true",
          "sqlitemagic.project.dir" to temporaryDirectory.toString(),
          "sqlitemagic.variant.name" to "debug",
          "sqlitemagic.variant.debug" to "true"
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        val persistentSchema = generatedSource.substring(
          startIndex = generatedSource.indexOf("override fun createSchema"),
          endIndex = generatedSource.indexOf("override fun createTemporarySchema")
        )
        val temporarySchema = generatedSource.substring(
          startIndex = generatedSource.indexOf("override fun createTemporarySchema")
        )
        persistentSchema.assertDoesNotContain("temporary_value")
        temporarySchema.assertContainsInOrder(
          "SqliteMagic_TemporaryEntry_Adapter.TABLE_SCHEMA",
          "temporary_value"
        )
      }

    Files
      .readString(temporaryDirectory.resolve("db/latest.struct"))
      .apply {
        assertContains(
          "\"indices\"",
          "book_lookup",
          "index_books_title",
          "\"temporaryTables\"",
          "\"temporaryIndices\"",
          "temporary_entries",
          "temporary_value"
        )
      }
  }

  @Test
  fun `keeps index output stable across repeated compilations`() {
    val first = SqliteMagicCompilation
      .compile(validIndexSources())
      .isOk()
    val second = SqliteMagicCompilation
      .compile(validIndexSources())
      .isOk()

    assertThat(first.generatedSource("SqliteMagicDatabase.kt"))
      .isEqualTo(second.generatedSource("SqliteMagicDatabase.kt"))
    assertThat(first.generatedSource("SqliteMagicDatabase.kt"))
      .contains("book_lookup")
  }

  @Test
  fun `includes indexes for tables generated in a later round`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Initial.kt",
          contents = "package $PACKAGE\nclass Initial"
        ),
        processingStepsFactory = { environment ->
          val steps = indexProcessingSteps(environment)
          steps.dropLast(1) + listOf(LateIndexedTableStep(environment), steps.last())
        }
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "late_index",
          "late_entries"
        )
      }
  }
}

private class LateIndexedTableStep(
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
        fileName = "LateIndexedTable"
      )
      .bufferedWriter()
      .use { writer ->
        writer.write(
          """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("late_entries")
            data class LateIndexedTable(
              @Index("late_index") val value: String
            )
          """
        )
      }
    return Continue
  }
}
