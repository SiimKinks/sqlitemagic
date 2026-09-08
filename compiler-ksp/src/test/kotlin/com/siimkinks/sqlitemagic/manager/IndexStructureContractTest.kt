package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.indexProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class IndexStructureContractTest : ProcessingStepsTest {
  override val processingSteps = ::indexProcessingSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `legacy JSON defaults temporary maps and ignores unknown fields`() {
    val legacyJson = """
      {
        "tables": {},
        "indices": {},
        "futureProperty": {"ignored": true}
      }
    """.trimIndent()

    val structure = DatabaseStructureJson.read(legacyJson)

    assertThat(structure).isEqualTo(DatabaseStructure())
    val encodedStructure = DatabaseStructureJson.write(structure)
    encodedStructure.assertDoesNotContain("futureProperty")
    encodedStructure.assertContains(
      """"temporaryTables":{}""",
      """"temporaryIndices":{}"""
    )
  }

  @Test
  fun `current structure round-trips non-empty temporary maps`() {
    val currentJson = structureJson(
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

    DatabaseStructureJson
      .write(DatabaseStructureJson.read(currentJson))
      .assertContains(
        "temporary_items",
        "temporary_name_index"
      )
  }

  @Test
  fun `release snapshots strip temporary tables and indexes from complete current structures`() {
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

    val releaseJson = Files.readString(
      temporaryDirectory.resolve("db/releases/1.struct")
    )
    releaseJson.assertContains("persistent_items", "persistent_name_index")
    releaseJson.assertDoesNotContain(
      "temporaryTables",
      "temporaryIndices",
      "temporary_items",
      "temporary_name_index"
    )
  }

  @Test
  fun `main compilation rejects a configured submodule table and index collision with ASCII folding`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submodule = SqliteMagicCompilation
      .compile(featureDatabaseDeclaration())
      .isOk()
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

    submodule
      .compile(
        mainDatabaseWithTable(tableName = "Shared"),
        kspOptions = mainCompilationOptions(mainDirectory)
      )
      .assertCompilationError("Duplicate SQLite schema identifier")
  }

  @Test
  fun `main compilation ignores current structures for unconfigured submodules`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submodule = SqliteMagicCompilation
      .compile(featureDatabaseDeclaration())
      .isOk()
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

    submodule
      .compile(
        mainDatabaseWithTable(tableName = "Shared"),
        kspOptions = mainCompilationOptions(mainDirectory)
      )
      .isOk()
  }

  @Test
  fun `main compilation allows equal raw names in persistent and temporary schemas`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submodule = SqliteMagicCompilation
      .compile(featureDatabaseDeclaration())
      .isOk()
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

    submodule
      .compile(
        mainDatabaseWithTable(tableName = "Shared"),
        kspOptions = mainCompilationOptions(mainDirectory)
      )
      .isOk()
  }

  @Test
  fun `main compilation rejects an ASCII-folded table and index collision in the temporary schema`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submodule = SqliteMagicCompilation
      .compile(featureDatabaseDeclaration())
      .isOk()
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

    submodule
      .compile(
        mainDatabaseWithoutTables(),
        kspOptions = mainCompilationOptions(mainDirectory)
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
    val releaseJson = Files.readString(
      temporaryDirectory.resolve("db/releases/1.struct")
    )
    releaseJson.assertContains(""""Ä"""", """"ä"""")
  }

  @Test
  fun `rejects an ASCII-folded table and index collision in the temporary namespace`() {
    writeStructure(
      file = temporaryDirectory.resolve("db/module.struct"),
      json = structureJson(
        temporaryTables = """
          {
            "SessionCache": {"name": "SessionCache"}
          }
        """.trimIndent(),
        temporaryIndices = indexJson(name = "sessioncache")
      )
    )

    assertThrows<IllegalStateException>(::migrateRelease)
  }

  private fun migrateRelease() {
    ReleaseMigrationCoordinator.migrate(
      projectDir = temporaryDirectory.toFile(),
      databaseDirectory = temporaryDirectory.resolve("db").toFile(),
      variantName = "release"
    )
  }

  private fun mainCompilationOptions(mainDirectory: Path) = mapOf(
    "sqlitemagic.migrate.debug" to "true",
    "sqlitemagic.project.dir" to mainDirectory.toString(),
    "sqlitemagic.variant.name" to "debug",
    "sqlitemagic.variant.debug" to "true"
  )

  private fun writeStructure(
    file: Path,
    json: String
  ) {
    file.toFile().apply {
      parentFile?.mkdirs()
      writeText(json)
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

private fun featureDatabaseDeclaration() = SourceFile.kotlin(
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
)

private fun mainDatabaseWithTable(
  tableName: String
) = SourceFile.kotlin(
  name = "MainDatabase.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Database
    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Table

    @Database(submodules = [FeatureDatabase::class])
    class MainDatabase

    @Table("$tableName")
    data class MainTable(@Id val id: Long)
  """
)

private fun mainDatabaseWithoutTables() = SourceFile.kotlin(
  name = "MainDatabase.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Database

    @Database(submodules = [FeatureDatabase::class])
    class MainDatabase
  """
)
