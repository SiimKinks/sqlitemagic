package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MigrationMetadataTasksTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `generates missing manifest for 141 releases and ordered modules preserving every source byte`() {
    val task = fixture("large")
    val releases = task.releaseDirectory.get().asFile
    val assets = task.assetsDirectory.get().asFile
    val compiledFile = task.compiledSchema.get().asFile
    val compiled = MigrationMetadataJson.readSchema(compiledFile.readText())
    val versions = (1..141).toList()
    versions.forEach { version ->
      releases.resolve("$version.struct")
        .writeText(STRUCTURE)
      assets.resolve("$version.sql")
        .writeText("SELECT $version\n")
      assets.resolve("Feature$version.sql")
        .writeText("-- module step\nSELECT $version\n")
    }
    val before = sourceBytes(task.project.projectDir)

    task.generate()

    val expected = MigrationManifest(
      formatVersion = 1,
      databaseId = compiled.databaseId,
      releaseVariant = compiled.releaseVariant,
      currentVersion = 141,
      modules = compiled.modules,
      schemas = versions.map { version ->
        ReleaseSchemaResource(
          version = version,
          name = "schemas/$version.struct",
          configuration = compiled.configuration.takeIf { version == 141 },
          modules = compiled.modules.takeIf { version == 141 },
          viewNames = compiled.views.map(MigrationViewSchema::name)
            .takeIf { version == 141 }
        )
      },
      steps = versions.map { version ->
        MigrationStep(
          toVersion = version,
          sqlResources = listOf(
            MigrationResource(name = "Feature$version.sql"),
            MigrationResource(name = "$version.sql")
          )
        )
      }
    )
    val manifestFile = releases.resolve("manifest.json")
    assertThat(MigrationMetadataJson.readManifest(manifestFile.readText()))
      .isEqualTo(expected)
    assertThat(sourceBytes(task.project.projectDir) - "db/releases/manifest.json")
      .isEqualTo(before)
    val after = sourceBytes(task.project.projectDir)

    task.generate()

    assertThat(sourceBytes(task.project.projectDir))
      .isEqualTo(after)
    val prepare = task.project.tasks
      .register("prepareTests", PrepareSqliteMagicMigrationTests::class.java)
      .get()
    prepare.databaseId.set(compiled.databaseId)
    prepare.releaseVariant.set(compiled.releaseVariant)
    prepare.releaseDirectory.set(task.releaseDirectory)
    prepare.assetsDirectory.set(task.assetsDirectory)
    prepare.compiledSchema.set(task.compiledSchema)
    prepare.migrationTaskPath.set(":app:migrateEeReleaseDb")
    prepare.generatedSources.set(task.project.layout.buildDirectory.dir("migration-test-sources"))
    prepare.generatedResources.set(task.project.layout.buildDirectory.dir("migration-test-resources"))

    prepare.prepare()

    assertThat(sourceBytes(task.project.projectDir))
      .isEqualTo(after)
  }

  @Test
  fun `adopts last shipped schema before publishing an already advanced compiled version`() {
    val task = fixture("advanced")
    val releases = task.releaseDirectory.get().asFile
    releases.resolve("140.struct")
      .writeText(STRUCTURE)
    task.generate()

    val manifest = MigrationMetadataJson.readManifest(
      releases.resolve("manifest.json")
        .readText()
    )
    assertThat(manifest.schema(140))
      .isEqualTo(
        ReleaseSchemaResource(
          version = 140,
          name = "schemas/140.struct"
        )
      )
    assertThat(manifest.currentVersion)
      .isEqualTo(140)
    assertThat(manifest.steps.last())
      .isEqualTo(
        MigrationStep(
          toVersion = 140,
          sqlResources = emptyList()
        )
      )
  }

  @Test
  fun `invalid history fails before publishing any manifest or modifying source evidence`() {
    mapOf(
      "no schemas" to "No retained release schemas",
      "duplicate version" to "Manifest schema versions must be unique",
      "malformed schema" to "Cannot read retained release schema",
      "unknown module" to "Unrecognized migration asset",
      "changed schema" to "Compiled schema differs",
      "changed view" to "Compiled view names differ"
    ).forEach { (label, diagnostic) ->
      val task = fixture(label)
      val releases = task.releaseDirectory.get().asFile
      val assets = task.assetsDirectory.get().asFile
      val currentSchema = releases.resolve("141.struct")
      currentSchema.writeText(STRUCTURE)
      when (label) {
        "no schemas" -> check(currentSchema.delete())
        "duplicate version" -> releases.resolve("0141.struct")
          .writeText(STRUCTURE)
        "malformed schema" -> currentSchema.writeText("malformed")
        "unknown module" -> assets.resolve("RemovedFeature140.sql")
          .writeText("SELECT 1\n")
        "changed schema" -> currentSchema.writeText("{}")
        "changed view" -> currentSchema.writeText(STRUCTURE.dropLast(1) + ",\"views\":{}}")
      }
      val before = sourceBytes(task.project.projectDir)

      val failure = assertThrows<Exception>(task::generate)
      val manifestFile = releases.resolve("manifest.json")

      assertWithMessage(label)
        .that(failure.message)
        .contains(diagnostic)
      assertWithMessage(label)
        .that(manifestFile.exists())
        .isFalse()
      assertWithMessage(label)
        .that(sourceBytes(task.project.projectDir))
        .isEqualTo(before)
    }
  }

  @Test
  fun `adoption inventories incomplete older history for code selected validation without rewriting it`() {
    val task = fixture("baseline")
    val releases = task.releaseDirectory.get().asFile
    releases.resolve("1.struct")
      .writeText("old incomplete metadata")
    releases.resolve("141.struct")
      .writeText(STRUCTURE)
    task.assetsDirectory.get().asFile.resolve("2.sql")
      .writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
    task.assetsDirectory.get().asFile.resolve("3.sql")
      .writeText("COMMIT\n")
    val before = sourceBytes(task.project.projectDir)

    task.generate()

    assertThat(sourceBytes(task.project.projectDir) - "db/releases/manifest.json")
      .isEqualTo(before)
  }

  @Test
  fun `cleanup removes old and new generated metadata and KSP caches preserving production history`() {
    val task = fixture("cleanup")
    val root = task.project.projectDir
    val preserved = listOf(
      "db/releases/141.struct",
      "db/releases/manifest.json",
      "src/release/assets/141.sql",
      "src/debug/assets/1001.sql",
      "db/notes.txt",
      "src/main/kotlin/Model.kt",
      "build/unrelated.txt"
    )
    val generated = listOf(
      "db/latest.struct",
      "db/latest_feature.struct",
      "db/latest_eeDebug.version",
      "db/feature.changed",
      "db/submodules.changed",
      "build/sqlitemagic/eeRelease/structures/latest.struct",
      "build/sqlitemagic/migration-testing/resources/manifest.json",
      "build/kspCaches/eeRelease/backups/kotlin/com/siimkinks/Generated.kt",
      "build/generated/ksp/eeRelease/kotlin/Generated.kt"
    )
    (preserved + generated).forEach { path ->
      val file = root.resolve(path)
      file.parentFile.mkdirs()
      file.writeText(path)
    }
    val before = preserved.associateWith { fileBytes(root.resolve(it)) }
    val cleanup = task.project.tasks
      .register("cleanMetadata", CleanSqliteMagicMetadata::class.java)
      .get()
    cleanup.databaseDirectory.set(root.resolve("db"))
    cleanup.buildDirectory.set(root.resolve("build"))

    cleanup.clean()
    cleanup.clean()

    assertThat(preserved.associateWith { fileBytes(root.resolve(it)) })
      .isEqualTo(before)
    assertThat(generated.filter { root.resolve(it).exists() })
      .isEmpty()
  }

  private fun fixture(name: String): GenerateSqliteMagicMigrationManifest {
    val root = directory.resolve(name)
      .toFile()
    check(root.mkdirs())
    val project = ProjectBuilder.builder()
      .withProjectDir(root)
      .build()
    val task = project.tasks
      .register("generateManifest", GenerateSqliteMagicMigrationManifest::class.java)
      .get()
    task.releaseDirectory.set(root.resolve("db/releases"))
    task.assetsDirectory.set(root.resolve("src/release/assets"))
    check(task.releaseDirectory.get().asFile.mkdirs())
    check(task.assetsDirectory.get().asFile.mkdirs())
    val compiled = root.resolve("compiled.schema.json")
    compiled.writeText(
      MigrationMetadataJson.writeSchema(
        ReleaseSchemaSnapshot(
          formatVersion = 1,
          databaseId = ":app",
          releaseVariant = "eeRelease",
          version = 141,
          modules = listOf("Feature", ":app"),
          configuration = MigrationConfiguration(foreignKeysEnabled = false),
          tableSql = listOf("CREATE TABLE sample (id INTEGER PRIMARY KEY)"),
          views = listOf(
            MigrationViewSchema(
              name = "sample_view",
              createSql = "CREATE VIEW sample_view AS SELECT id FROM sample"
            )
          ),
          indexSql = emptyList()
        )
      )
    )
    task.compiledSchema.set(compiled)
    return task
  }

  private fun sourceBytes(root: File) = root.walkTopDown()
    .filter(File::isFile)
    .filterNot { file ->
      file.relativeTo(root)
        .path.startsWith("build/")
    }
    .associate { it.relativeTo(root).path to fileBytes(it) }

  private fun fileBytes(file: File) = file.readBytes()
    .toList()
}

private const val STRUCTURE = """{"tables":{"sample":{"name":"sample",""" +
    """"schema":"CREATE TABLE sample (id INTEGER PRIMARY KEY)","columns":[]}},"indices":{}}"""
