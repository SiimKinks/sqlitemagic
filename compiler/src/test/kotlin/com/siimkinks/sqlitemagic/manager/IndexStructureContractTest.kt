package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.indexProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.mainDatabaseWithSubmodule
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.debugMigrationOptions
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class IndexStructureContractTest {
  private val compiler = SqliteMagicCompiler(::indexProcessingSteps)

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `release snapshots contain only persistent tables and indexes from complete current structures`() {
    writeStructure(
      file = temporaryDirectory.resolve("db/module.struct"),
      json = structureJson(
        tables = """
          {
            "persistent_items": {"name": "persistent_items"}
          }
        """.trimIndent(),
        indices = """
          {
            "persistent_name_index": {
              "name": "persistent_name_index",
              "indexSql": "CREATE INDEX persistent_name_index ON persistent_items (name)",
              "forTable": "persistent_items"
            }
          }
        """.trimIndent(),
        temporaryTables = """
          {
            "temporary_items": {"name": "temporary_items"}
          }
        """.trimIndent(),
        temporaryIndices = """
          {
            "temporary_name_index": {
              "name": "temporary_name_index",
              "indexSql": "CREATE INDEX temporary_name_index ON temporary_items (name)",
              "forTable": "temporary_items"
            }
          }
        """.trimIndent()
      )
    )

    migrateRelease()

    val releaseStructure = DatabaseStructureJson.read(
      temporaryDirectory
        .resolve("db/releases/1.struct")
        .readText()
    )

    assertThat(releaseStructure).isEqualTo(
      DatabaseStructure(
        tables = mapOf(
          "persistent_items" to TableStructure(name = "persistent_items")
        ),
        indices = mapOf(
          "persistent_name_index" to IndexStructure(
            name = "persistent_name_index",
            indexSql = "CREATE INDEX persistent_name_index ON persistent_items (name)",
            forTable = "persistent_items"
          )
        )
      )
    )
  }

  @Test
  fun `main compilation rejects a configured submodule table and index collision with ASCII folding`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    writeStructure(
      file = mainDirectory.resolve("db/latest_feature.struct"),
      json = structureJson(
        indices = """
          {
            "sHaReD": {
              "name": "sHaReD",
              "indexSql": "CREATE INDEX sHaReD ON feature_items (name)",
              "forTable": "feature_items"
            }
          }
        """.trimIndent()
      )
    )

    compiler
      .compile(
        mainDatabaseWithSharedTable(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(mainDirectory.resolve("db"))
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .assertCompilationError("Duplicate SQLite schema identifier")
  }

  @Test
  fun `main compilation ignores unrelated staged snapshots`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    writeStructure(
      file = mainDirectory.resolve("db/latest_feature.struct"),
      json = structureJson()
    )
    writeStructure(
      file = mainDirectory.resolve("db/latest_unrelated.struct"),
      json = structureJson(
        indices = """
          {
            "Shared": {
              "name": "Shared",
              "indexSql": "CREATE INDEX Shared ON unrelated_items (name)",
              "forTable": "unrelated_items"
            }
          }
        """.trimIndent()
      )
    )

    compiler
      .compile(
        mainDatabaseWithSharedTable(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(mainDirectory.resolve("db"))
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .isOk()
  }

  @Test
  fun `main compilation rejects a missing configured submodule snapshot`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val stagedDirectory = temporaryDirectory.resolve("staged")
    writeStructure(
      file = stagedDirectory.resolve("latest_unrelated.struct"),
      json = structureJson()
    )

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(stagedDirectory)
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .assertCompilationError("Missing staged current structure snapshot")
  }

  @Test
  fun `main compilation rejects duplicate configured submodule snapshots`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val firstStagedDirectory = temporaryDirectory.resolve("staged-one")
    val secondStagedDirectory = temporaryDirectory.resolve("staged-two")
    listOf(firstStagedDirectory, secondStagedDirectory).forEach { directory ->
      writeStructure(
        file = directory.resolve("latest_feature.struct"),
        json = structureJson()
      )
    }

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(firstStagedDirectory, secondStagedDirectory)
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .assertCompilationError("Multiple staged current structure snapshots")
  }

  @Test
  fun `main compilation allows equal raw names in persistent and temporary schemas`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    writeStructure(
      file = mainDirectory.resolve("db/latest_feature.struct"),
      json = structureJson(
        temporaryTables = """
          {
            "TemporaryItems": {"name": "TemporaryItems"}
          }
        """.trimIndent(),
        temporaryIndices = """
          {
            "Shared": {
              "name": "Shared",
              "indexSql": "CREATE INDEX Shared ON TemporaryItems (name)",
              "forTable": "TemporaryItems"
            }
          }
        """.trimIndent()
      )
    )

    compiler
      .compile(
        mainDatabaseWithSharedTable(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(mainDirectory.resolve("db"))
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .isOk()
  }

  @Test
  fun `main compilation rejects an ASCII-folded table and index collision in the temporary schema`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    writeStructure(
      file = mainDirectory.resolve("db/latest_feature.struct"),
      json = structureJson(
        temporaryTables = """
          {
            "SessionCache": {"name": "SessionCache"}
          }
        """.trimIndent(),
        temporaryIndices = """
          {
            "sessioncache": {
              "name": "sessioncache",
              "indexSql": "CREATE INDEX sessioncache ON SessionCache (value)",
              "forTable": "SessionCache"
            }
          }
        """.trimIndent()
      )
    )

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        kspOptions = debugMigrationOptions(
          projectDirectory = mainDirectory,
          structureInputDirectories = listOf(mainDirectory.resolve("db"))
        ),
        classpaths = listOf(FEATURE_SUBMODULE_CLASSES)
      )
      .assertCompilationError("Duplicate SQLite schema identifier")
  }

  @Test
  fun `keeps non-ASCII case variants distinct when aggregating persistent structures`() {
    writeStructure(
      file = temporaryDirectory.resolve("db/a.struct"),
      json = structureJson(
        indices = indexJson(name = "Ä")
      )
    )
    writeStructure(
      file = temporaryDirectory.resolve("db/b.struct"),
      json = structureJson(
        indices = indexJson(name = "ä")
      )
    )

    assertDoesNotThrow(::migrateRelease)
    val releaseJson = temporaryDirectory
      .resolve("db/releases/1.struct")
      .readText()
    releaseJson.assertContains(""""Ä"""", """"ä"""")
  }

  private fun migrateRelease() = ReleaseMigrationCoordinator.migrate(
    projectDir = temporaryDirectory.toFile(),
    databaseDirectory = temporaryDirectory
      .resolve("db")
      .toFile(),
    variantName = "release",
    databaseVersion = 1
  )

  private fun writeStructure(
    file: Path,
    json: String
  ) = file
    .apply { parent.createDirectories() }
    .writeText(json)

  private companion object {
    val FEATURE_SUBMODULE_CLASSES by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "FeatureDatabase.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
              import com.siimkinks.sqlitemagic.annotation.Table

              @SubmoduleDatabase("feature")
              class FeatureDatabase

              @Table("feature_items")
              data class FeatureItem(@Id val id: Long)
            """
          ),
          processingStepsFactory = ::indexProcessingSteps
        )
        .isOk()
        .outputDirectory
    }
  }
}

private fun structureJson(
  tables: String = "{}",
  indices: String = "{}",
  temporaryTables: String = "{}",
  temporaryIndices: String = "{}"
) = """
  {
    "tables": $tables,
    "indices": $indices,
    "temporaryTables": $temporaryTables,
    "temporaryIndices": $temporaryIndices
  }
""".trimIndent()

private fun indexJson(name: String) = """
  {
    "$name": {
      "name": "$name",
      "indexSql": "CREATE INDEX $name ON SessionCache (value)",
      "forTable": "SessionCache"
    }
  }
""".trimIndent()

private fun mainDatabaseWithSharedTable() = SourceFile.kotlin(
  name = "MainDatabase.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Database
    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Table

    @Database(submodules = [FeatureDatabase::class])
    class MainDatabase

    @Table("Shared")
    data class MainTable(@Id val id: Long)
  """
)
