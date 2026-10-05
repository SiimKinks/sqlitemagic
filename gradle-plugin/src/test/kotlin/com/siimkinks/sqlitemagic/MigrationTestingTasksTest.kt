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

class MigrationTestingTasksTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `preparation excludes old evidence IO and preserves every committed byte`() {
    val fixture = fixture()
    fixture.projectDir.resolve("db/releases/1.struct").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
    fixture.projectDir.resolve("src/release/assets/2.sql").writeText("INVALID MULTILINE SQL\nSELECT '")
    fixture.task.firstVersion.set(2)
    val before = fixture.sourceBytes()

    fixture.task.prepare()

    assertThat(fixture.sourceBytes().mapValues { it.value.toList() })
      .isEqualTo(before.mapValues { it.value.toList() })
    assertThat(fixture.task.committedInputs.map(File::getName))
      .containsExactly("manifest.json", "2.struct", "3.struct", "3.sql")
    val root = fixture.task.generatedResources.get().asFile.resolve(
      migrationResourceRoot(
        databaseId = ":app",
        releaseVariant = "release"
      )
    )
    assertThat(root.resolve("schemas/1.struct").exists()).isFalse()
    assertThat(root.resolve("2.sql").exists()).isFalse()
    assertThat(root.resolve("manifest.json").readBytes().toList())
      .isEqualTo(fixture.projectDir.resolve("db/releases/manifest.json").readBytes().toList())
    assertThat(root.resolve("3.sql").readText()).isEqualTo("SELECT 3\n")
    assertThat(root.resolve("current.schema.json").readText())
      .isEqualTo(fixture.projectDir.resolve("compiled.schema.json").readText())
    val descriptor = fixture.task.generatedSources.get().asFile
      .resolve("com/siimkinks/sqlitemagic/SqliteMagicMigrationDatabase.kt")
      .readText()
    assertThat(descriptor).contains("object SqliteMagicMigrationDatabase : MigrationDatabaseDescriptor")
    assertThat(descriptor).contains("override val firstVersion: Int? = 2")
  }

  @Test
  fun `new optional conventional SQL is copied validated and added to cache inputs`() {
    val fixture = fixture()
    val manifestFile = fixture.projectDir.resolve("db/releases/manifest.json")
    val manifest = MigrationMetadataJson.readManifest(manifestFile.readText())
    manifestFile.writeText(
      MigrationMetadataJson.writeManifest(
        manifest.copy(
          steps = manifest.steps.map { step ->
            when (step.toVersion) {
              2 -> step.copy(sqlResources = emptyList())
              else -> step
            }
          }
        )))
    val script = fixture.projectDir.resolve("src/release/assets/2.sql")
    check(script.delete())
    val originalInputs = fixture.task.committedInputs
    fixture.task.prepare()
    val root = fixture.task.generatedResources.get().asFile.resolve(
      migrationResourceRoot(
        databaseId = ":app",
        releaseVariant = "release"
      )
    )
    assertThat(root.resolve("2.sql").exists()).isFalse()

    script.writeText("UPDATE sample SET id = id + 1\n")
    assertThat(fixture.task.committedInputs.toSet() - originalInputs.toSet()).containsExactly(script)
    fixture.task.prepare()
    assertThat(root.resolve("2.sql").readText()).isEqualTo(script.readText())

    script.writeText("SELECT 1; SELECT 2\n")
    val failure = assertThrows<IllegalStateException>(fixture.task::prepare)
    assertThat(failure.message).contains("2.sql at physical line 1")
  }

  @Test
  fun `missing manifest identifies exact publication task and adoption baseline`() {
    val fixture = fixture()
    fixture.projectDir.resolve("db/releases/manifest.json").delete()
    val failure = assertThrows<IllegalStateException>(fixture.task::prepare)
    assertThat(failure.message).contains(":app:migrateReleaseDb")
    assertThat(failure.message).contains("firstVersion")
  }

  @Test
  fun `included incomplete schema fails instead of synthesizing history`() {
    val fixture = fixture()
    fixture.projectDir.resolve("db/releases/1.struct").writeText("malformed")
    val before = fixture.sourceBytes()
    val failure = assertThrows<org.gradle.api.GradleException>(fixture.task::prepare)
    assertThat(failure.message).contains("Incomplete release schema 1")
    assertThat(fixture.sourceBytes().mapValues { it.value.toList() })
      .isEqualTo(before.mapValues { it.value.toList() })
  }

  @Test
  fun `included malformed SQL fails through shared physical line reader`() {
    val fixture = fixture()
    fixture.task.firstVersion.set(2)
    fixture.projectDir.resolve("src/release/assets/3.sql").writeText("-- comment\n\nSELECT 1; SELECT 2;\n")
    val failure = assertThrows<IllegalStateException>(fixture.task::prepare)
    assertThat(failure.message).contains("3.sql at physical line 3")
  }

  @Test
  fun `required SQL rejects empty or comment-only source evidence`() {
    mapOf("empty" to "", "comments" to "-- reviewed comment\n\n").forEach { (label, text) ->
      val fixture = fixture(label)
      fixture.projectDir.resolve("src/release/assets/3.sql").writeText(text)

      val failure = assertThrows<IllegalStateException>(fixture.task::prepare)

      assertWithMessage(label).that(failure.message).contains("Empty migration script 3.sql")
      assertWithMessage(label).that(fixture.task.generatedResources.get().asFile.exists()).isFalse()
    }
  }

  @Test
  fun `schema and manifest identities must match selected history`() {
    listOf("database", "variant").forEach { label ->
      val fixture = fixture(label)
      when (label) {
        "database" -> fixture.task.databaseId.set(":other")
        else -> fixture.task.releaseVariant.set("otherRelease")
      }
      val failure = assertThrows<IllegalStateException>(fixture.task::prepare)
      assertThat(failure.message).contains("Published migration history belongs")
    }
  }

  @Test
  fun `preparation rejects same version table index and view name changes`() {
    val published = schema(3)
    val cases = mapOf(
      "table" to published.copy(tableSql = listOf("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT)")),
      "index" to published.copy(indexSql = listOf("CREATE INDEX sample_id ON sample(id)")),
      "view" to published.copy(
        views = listOf(
          MigrationViewSchema(
            name = "sample_view",
            createSql = "CREATE VIEW sample_view AS SELECT id FROM sample"
          )
        )
      )
    )
    cases.forEach { (label, compiled) ->
      val fixture = fixture(label)
      fixture.projectDir.resolve("compiled.schema.json").writeText(MigrationMetadataJson.writeSchema(compiled))
      val failure = assertThrows<IllegalStateException>(fixture.task::prepare)
      assertWithMessage(label).that(failure.message).contains("published current .struct")
    }
  }

  @Test
  fun `typed flavored publication reads merged assets and writes generated SQL to build type assets`() {
    val projectDir = directory.toFile()
    val database = projectDir.resolve("db")
    check(database.resolve("releases").mkdirs())
    val previous = database.resolve("releases/1.struct")
    previous.writeText(structureJson("books"))
    val shared = database.resolve("latest.struct")
    shared.writeText(structureJson("unrelated_debug_table"))
    val staged = projectDir.resolve("build/sqlitemagic/paidRelease/structures/latest.struct")
    check(staged.parentFile.mkdirs())
    staged.writeText(structureJson("books", "authors"))
    val oldScript = projectDir.resolve("src/release/assets/1.sql")
    check(oldScript.parentFile.mkdirs())
    oldScript.writeText("SELECT 1\n")
    val frozen = listOf(previous, shared, staged, oldScript).associateWith(File::readText)
    val project = ProjectBuilder.builder()
      .withProjectDir(projectDir)
      .build()
    val task = project.tasks.create("migratePaidReleaseDb", PublishSqliteMagicRelease::class.java)
    task.projectDirectory.set(projectDir)
    task.databaseDirectory.set(database)
    task.releaseVariant.set("paidRelease")
    task.assetsBuildType.set("release")
    task.currentStructures.from(staged)
    val merged = projectDir.resolve("build/merged-assets/paidRelease")
    check(merged.mkdirs())
    merged.resolve("2.sql").writeText("UPDATE books SET id = id + 2;\n")
    task.assetsDirectory.set(merged)

    task.publish()

    assertThat(projectDir.resolve("src/release/assets/2.sql").readText())
      .isEqualTo("UPDATE books SET id = id + 2;\nCREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)\n")
    assertThat(projectDir.resolve("src/paidRelease/assets").exists()).isFalse()
    assertThat(database.resolve("releases/2.struct").readText()).doesNotContain("unrelated_debug_table")
    assertThat(frozen.keys.associateWith(File::readText)).isEqualTo(frozen)
  }

  @Test
  fun `publication rejects an export from another full release variant`() {
    val fixture = fixture()
    fixture.projectDir.resolve("compiled.schema.json").writeText(
      MigrationMetadataJson.writeSchema(
        schema(3).copy(releaseVariant = "otherRelease")
      )
    )
    val task = fixture.task.project.tasks.create("migrateReleaseDb", PublishSqliteMagicRelease::class.java)
    task.projectDirectory.set(fixture.projectDir)
    task.databaseDirectory.set(fixture.projectDir.resolve("db"))
    task.releaseVariant.set("release")
    task.assetsBuildType.set("release")
    task.compiledSchema.set(fixture.projectDir.resolve("compiled.schema.json"))
    val before = fixture.sourceBytes()

    val failure = assertThrows<IllegalStateException>(task::publish)

    assertThat(failure.message).contains("different selected release variant")
    assertThat(fixture.sourceBytes().mapValues { it.value.toList() })
      .isEqualTo(before.mapValues { it.value.toList() })
  }

  @Test
  fun `source publication tasks do not claim compiler inputs as generated artifacts`() {
    val fixture = fixture()
    val project = fixture.task.project
    val release = project.tasks.create("migrateReleaseDb", PublishSqliteMagicRelease::class.java)
    release.projectDirectory.set(fixture.projectDir)
    release.databaseDirectory.set(fixture.projectDir.resolve("db"))
    release.releaseVariant.set("release")
    release.assetsBuildType.set("release")
    val structures = project.tasks.create("publishReleaseStructures", PublishSqliteMagicStructures::class.java)
    structures.destinationDirectory.set(fixture.projectDir.resolve("db"))
    assertThat(release.outputs.files.files).isEmpty()
    assertThat(structures.outputs.files.files).isEmpty()
    assertThat(fixture.task.taskDependencies.getDependencies(fixture.task)).doesNotContain(release)
  }

  @Test
  fun `release selection defaults to registration order and supports explicit overrides`() {
    mapOf(
      "single release" to ("release" to listOf("release")),
      "unsorted registration order" to ("zetaRelease" to listOf("zetaRelease", "alphaRelease"))
    )
      .forEach { (label, case) ->
        val (expected, candidates) = case
        assertWithMessage(label)
          .that(
            selectMigrationReleaseVariant(
              candidates = candidates,
              requested = null
            )
          )
          .isEqualTo(expected)
      }
    assertWithMessage("explicit override")
      .that(
        selectMigrationReleaseVariant(
          candidates = listOf("zetaRelease", "alphaRelease"),
          requested = "alphaRelease"
        )
      )
      .isEqualTo("alphaRelease")
    val unavailable = assertThrows<IllegalStateException> {
      selectMigrationReleaseVariant(
        candidates = listOf("release"),
        requested = "disabledRelease"
      )
    }
    assertWithMessage("unavailable override").that(unavailable.message)
      .contains("disabledRelease")
    val empty = assertThrows<org.gradle.api.GradleException> {
      selectMigrationReleaseVariant(
        candidates = emptyList(),
        requested = null
      )
    }
    assertWithMessage("no eligible releases").that(empty.message)
      .contains("none were registered")
  }

  private fun fixture(name: String = "fixture"): TaskFixture {
    val projectDir = directory.resolve(name).toFile()
    check(projectDir.mkdirs())
    val releases = projectDir.resolve("db/releases")
    check(releases.mkdirs())
    val assets = projectDir.resolve("src/release/assets")
    check(assets.mkdirs())
    listOf(1, 2, 3).forEach { version ->
      releases.resolve("$version.struct").writeText(
        """{"tables":{"sample":{"name":"sample","schema":"CREATE TABLE sample (id INTEGER PRIMARY KEY)",""" +
            """"columns":[]}},"indices":{}}"""
      )
    }
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = ":app",
      releaseVariant = "release",
      currentVersion = 3,
      modules = listOf(":app"),
      schemas = listOf(1, 2, 3).map { version ->
        ReleaseSchemaResource(
          version = version,
          name = "schemas/$version.struct",
          configuration = MigrationConfiguration(foreignKeysEnabled = true),
          modules = listOf(":app")
        )
      },
      steps = listOf(2, 3).map { version ->
        MigrationStep(
          toVersion = version,
          sqlResources = listOf(MigrationResource("$version.sql"))
        )
      }
    )
    releases.resolve("manifest.json").writeText(MigrationMetadataJson.writeManifest(manifest))
    assets.resolve("2.sql").writeText("SELECT 2\n")
    assets.resolve("3.sql").writeText("SELECT 3\n")
    projectDir.resolve("compiled.schema.json").writeText(MigrationMetadataJson.writeSchema(schema(3)))
    val project = ProjectBuilder.builder()
      .withProjectDir(projectDir)
      .build()
    val task = project.tasks.create("prepareMigrationTests", PrepareSqliteMagicMigrationTests::class.java)
    task.databaseId.set(":app")
    task.releaseVariant.set("release")
    task.releaseDirectory.set(releases)
    task.assetsDirectory.set(assets)
    task.migrationTaskPath.set(":app:migrateReleaseDb")
    task.compiledSchema.set(projectDir.resolve("compiled.schema.json"))
    task.generatedSources.set(projectDir.resolve("build/generated-sources"))
    task.generatedResources.set(projectDir.resolve("build/generated-resources"))
    return TaskFixture(
      projectDir = projectDir,
      task = task
    )
  }
}

private data class TaskFixture(
  val projectDir: File,
  val task: PrepareSqliteMagicMigrationTests
) {
  fun sourceBytes() = projectDir.walkTopDown()
    .filter(File::isFile)
    .filterNot { "build" in it.relativeTo(projectDir).path.split(File.separatorChar) }
    .associate { it.relativeTo(projectDir).path to it.readBytes() }
}

private fun schema(version: Int) = ReleaseSchemaSnapshot(
  formatVersion = 1,
  databaseId = ":app",
  releaseVariant = "release",
  version = version,
  modules = listOf(":app"),
  configuration = MigrationConfiguration(foreignKeysEnabled = true),
  tableSql = listOf("CREATE TABLE sample (id INTEGER PRIMARY KEY)"),
  views = emptyList(),
  indexSql = emptyList()
)

private fun structureJson(vararg tables: String) = tables.joinToString(
  separator = ",",
  prefix = "{\"tables\":{",
  postfix = "}}"
) { name ->
  """"$name":{"name":"$name","schema":"CREATE TABLE IF NOT EXISTS $name (id INTEGER PRIMARY KEY)","columns":[""" +
      """{"id":true,"name":"id","sqlType":"INTEGER","schema":"id INTEGER PRIMARY KEY"}]}"""
}
