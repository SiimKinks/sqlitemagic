package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.Properties

class MigrationTestingTaskCacheTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `preparation cache notices added conventional assets without changing committed history`() {
    val projectDir = directory.toFile()
    writeBuild(projectDir)
    val releases = projectDir.resolve("db/releases")
    check(releases.mkdirs())
    val schema = ReleaseSchemaSnapshot(
      formatVersion = 1,
      databaseId = ":app",
      releaseVariant = "release",
      version = 2,
      modules = listOf(":app"),
      configuration = MigrationConfiguration(foreignKeysEnabled = false),
      tableSql = listOf("CREATE TABLE sample (id INTEGER PRIMARY KEY)"),
      views = emptyList(),
      indexSql = emptyList()
    )
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = ":app",
      releaseVariant = "release",
      currentVersion = 2,
      modules = listOf(":app"),
      schemas = listOf(
        ReleaseSchemaResource(
          version = 1,
          name = "schemas/1.struct",
          configuration = schema.configuration,
          modules = schema.modules
        ),
        ReleaseSchemaResource(
          version = 2,
          name = "schemas/2.struct",
          configuration = schema.configuration,
          modules = schema.modules
        )
      ),
      steps = listOf(
        MigrationStep(
          toVersion = 2,
          sqlResources = emptyList()
        )
      )
    )
    val manifestFile = releases.resolve("manifest.json")
    manifestFile.writeText(MigrationMetadataJson.writeManifest(manifest))
    releases.resolve("1.struct").writeText(
      """{"tables":{"sample":{"name":"sample","schema":"CREATE TABLE sample (id INTEGER PRIMARY KEY)",""" +
          """"columns":[]}},"indices":{}}"""
    )
    releases.resolve("1.struct").copyTo(releases.resolve("2.struct"))
    projectDir.resolve("compiled.schema.json").writeText(MigrationMetadataJson.writeSchema(schema))
    val before = manifestFile.readBytes().toList()
    val assets = projectDir.resolve("src/release/assets")
    check(assets.mkdirs())
    val arguments = listOf(
      "prepareMigrationTests",
      "--configuration-cache",
      "--offline",
      "--stacktrace"
    )
    val first = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments(arguments)
      .build()
    val second = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments(arguments)
      .build()
    assertThat(first.task(":prepareMigrationTests")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(second.task(":prepareMigrationTests")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    val discovered = assets.resolve("2.sql")
    discovered.writeText("SELECT 2\n")
    val third = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments(arguments)
      .build()
    assertThat(third.task(":prepareMigrationTests")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val resources = projectDir.resolve("build/generated-resources")
    assertThat(resources.walkTopDown().filter { it.name == "2.sql" }.single().readText()).isEqualTo("SELECT 2\n")
    assertThat(second.output).contains("Reusing configuration cache")
    assertThat(manifestFile.readBytes().toList()).isEqualTo(before)
    val descriptor = projectDir.resolve("build/generated-sources")
      .resolve("com/siimkinks/sqlitemagic/SqliteMagicMigrationDatabase.kt")
    assertThat(descriptor.isFile).isTrue()
  }

  @Test
  fun `Gradle input validation preserves missing history remediation`() {
    val projectDir = directory.toFile()
    writeBuild(projectDir)
    projectDir.resolve("compiled.schema.json").writeText("{}")
    val result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments("prepareMigrationTests", "--configuration-cache", "--offline", "--stacktrace")
      .buildAndFail()
    assertThat(result.output).contains(":app:migrateReleaseDb")
    assertThat(result.output).contains("firstVersion")
  }

  private fun writeBuild(projectDir: File) {
    projectDir.resolve("settings.gradle").writeText("rootProject.name = 'migration-task-contract'\n")
    val properties = Properties()
    checkNotNull(javaClass.classLoader.getResourceAsStream("plugin-under-test-metadata.properties"))
      .use(properties::load)
    val classpath = properties.getProperty("implementation-classpath")
      .split(File.pathSeparator)
      .joinToString(
        separator = ",\n            ",
        transform = ::groovyLiteral
      )
    projectDir.resolve("build.gradle").writeText(
      """
      buildscript {
        dependencies {
          classpath files(
            $classpath
          )
        }
      }

      import com.siimkinks.sqlitemagic.PrepareSqliteMagicMigrationTests

      tasks.register('prepareMigrationTests', PrepareSqliteMagicMigrationTests) {
        databaseId.set(':app')
        releaseVariant.set('release')
        releaseDirectory.set(layout.projectDirectory.dir('db/releases'))
        assetsDirectory.set(layout.projectDirectory.dir('src/release/assets'))
        migrationTaskPath.set(':app:migrateReleaseDb')
        compiledSchema.set(layout.projectDirectory.file('compiled.schema.json'))
        generatedSources.set(layout.buildDirectory.dir('generated-sources'))
        generatedResources.set(layout.buildDirectory.dir('generated-resources'))
      }
    """.trimIndent()
    )
  }
}

private fun groovyLiteral(value: String): String {
  val escaped = value
    .replace(
      oldValue = "\\",
      newValue = "\\\\"
    )
    .replace(
      oldValue = "'",
      newValue = "\\'"
    )
  return "'$escaped'"
}
