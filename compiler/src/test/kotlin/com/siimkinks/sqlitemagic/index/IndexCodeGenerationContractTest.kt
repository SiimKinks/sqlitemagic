package com.siimkinks.sqlitemagic.index

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class IndexCodeGenerationContractTest {
  private val compiler = SqliteMagicCompiler(::indexProcessingSteps)

  @Test
  fun `collects and stably generates field and composite indexes in physical column order`() {
    val compilation = compiler
      .compile(validIndexSources())
      .isOk()
    val repeated = compiler
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
    compilation
      .assertGeneratedSources(
        "SqliteMagicDatabase.kt",
        "SqliteMagic_Book_Adapter.kt",
        "SqliteMagic_Profile_Adapter.kt",
        "SqliteMagic_InheritedEntry_Adapter.kt",
        "SqliteMagic_SelectiveEntry_Adapter.kt",
        "SqliteMagic_NoIdEntry_Adapter.kt",
        "SqliteMagic_StringIdEntry_Adapter.kt"
      )
      .withGeneratedSource("SqliteMagic_Book_Adapter.kt") {
        it.assertContainsInOrder(
          "id INTEGER PRIMARY KEY",
          "title TEXT DEFAULT ''",
          "isbn TEXT DEFAULT ''",
          "author TEXT DEFAULT ''",
          "edition INTEGER DEFAULT 0"
        )
      }
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it
          .assertContains(
            "CREATE INDEX",
            "index_books_title",
            "index_books_author",
            "CREATE UNIQUE INDEX",
            "book_isbn",
            "book_lookup",
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
          .assertContainsInOrder(
            "book_lookup",
            "author",
            "edition"
          )
          .assertContainsInOrder(
            "index_unnamed_composites_id_first_part_second_part",
            "id",
            "first_part",
            "second_part"
          )
          .assertContainsInOrder(
            "inherited_value",
            "local_value",
            "index_profiles_home_geo_city",
            "index_profiles_work_geo_city"
          )
      }
      .withGeneratedSource("SqliteMagic_Profile_Adapter.kt") {
        it.assertContainsInOrder(
          "id TEXT PRIMARY KEY",
          "home_geo_city TEXT DEFAULT ''",
          "home_geo_country TEXT DEFAULT ''",
          "work_geo_city TEXT DEFAULT ''",
          "work_geo_country TEXT DEFAULT ''"
        )
      }
      .withGeneratedSource("SqliteMagic_InheritedEntry_Adapter.kt") {
        it.assertContainsInOrder(
          "inherited_value TEXT DEFAULT ''",
          "local_value INTEGER DEFAULT 0"
        )
      }
      .withGeneratedSource("SqliteMagic_SelectiveEntry_Adapter.kt") {
        it
          .assertContains("persisted_value")
          .assertDoesNotContain("excluded_value")
      }
      .withGeneratedSource("SqliteMagic_NoIdEntry_Adapter.kt") {
        it
          .assertContains("CREATE TABLE IF NOT EXISTS no_id_entries (value TEXT DEFAULT '')")
          .assertDoesNotContain("PRIMARY KEY")
      }
      .withGeneratedSource("SqliteMagic_StringIdEntry_Adapter.kt") {
        it.assertContains("id TEXT PRIMARY KEY")
      }
    assertThat(compilation.generatedSource("SqliteMagicDatabase.kt"))
      .isEqualTo(repeated.generatedSource("SqliteMagicDatabase.kt"))
  }

  @Test
  fun `routes temporary indexes after temporary tables and excludes them from persistent schema`(
    @TempDir temporaryDirectory: Path
  ) {
    compiler
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
        generatedSource
          .functionBody("createSchemaTables")
          .unwrapped()
          .assertContainsInOrder(
            "if (temporary)",
            "SqliteMagic_TemporaryEntry_Adapter.TABLE_SCHEMA",
            "else"
          )
          .substringAfter("else")
          .assertDoesNotContain("SqliteMagic_TemporaryEntry_Adapter.TABLE_SCHEMA")
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder(
            "if (temporary)",
            "CREATE INDEX IF NOT EXISTS",
            "temporary_value",
            "else"
          )
          .substringAfter("else")
          .assertDoesNotContain("temporary_value")
      }

    Files
      .readString(temporaryDirectory.resolve("db/latest.struct"))
      .assertContains(
        """"indices"""",
        "book_lookup",
        "index_books_title",
        """"temporaryTables"""",
        """"temporaryIndices"""",
        "temporary_entries",
        "temporary_value"
      )
  }
}
