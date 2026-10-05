package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Real Android/KSP consumers use published artifacts; release publication is explicit in this isolated project. */
internal class MigrationTestingConsumerIntegrationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `real selected release exports all views and permanently tests growing retained history`() {
    val repository = findRepository()
    val original = repository.resolve("sqlitemagic-tests")
    val project = temporaryDirectory.resolve("consumer").toFile()
    copyConsumer(
      source = original,
      destination = project
    )
    configureSdk(
      repository = repository,
      original = original,
      project = project
    )
    val application = project.resolve("app")
    val applicationBuild = application.resolve("build.gradle.kts")
    val initialBuild = applicationBuild.readText()
    val template = checkNotNull(javaClass.getResourceAsStream("/migration-consumer/DatabaseMigrationTest.kt"))
      .bufferedReader()
      .use(BufferedReader::readText)
    val permanentTest = application.resolve("src/test/kotlin/com/siimkinks/sqlitemagic/DatabaseMigrationTest.kt")
    permanentTest.writeText(template)
    val testHash = digest(permanentTest)
    val declaredModel = application.resolve("src/main/kotlin/com/siimkinks/sqlitemagic/MigrationPerson.kt")

    for (version in 1..3) {
      applicationBuild.writeText(
        configuredBuild(
          original = initialBuild,
          version = version
        )
      )
      declaredModel.writeText(modelSource(version))
      println("Consumer migration fixture: explicitly publishing release $version")
      run(
        project = project,
        arguments = listOf(":app:migrateReleaseDb")
      )
      if (version > 1) {
        val sql = application.resolve("src/release/assets/$version.sql")
        assertThat(sql.isFile)
          .isTrue()
        sql.appendText(
          when (version) {
            2 -> "\nUPDATE migration_person SET name=upper(name), name_length=length(name)\n"
            else -> "\nUPDATE migration_person SET slug=lower(name)\n"
          }
        )
      }
      val manifest = MigrationMetadataJson.readManifest(application.resolve("db/releases/manifest.json").readText())
      val reference = manifest.schema(version)
      val snapshot = MigrationMetadataJson.readStructure(
        text = application.resolve("db/releases/$version.struct").readText(),
        manifest = manifest,
        reference = reference
      )
      assertThat(snapshot.version).isEqualTo(version)
      assertThat(snapshot.modules).containsExactly("Submodule", ":app").inOrder()
      assertThat(snapshot.configuration.foreignKeysEnabled).isTrue()
      assertThat(snapshot.tableSql.any { it.contains("submodule_persistent_value") }).isTrue()
      assertThat(application.resolve("db/releases/$version.schema.json").exists()).isFalse()
      val exported = MigrationMetadataJson.readSchema(
        application.resolve("build/sqlitemagic/release/current.schema.json").readText()
      )
      assertThat(exported.tableSql.toSet()).isEqualTo(snapshot.tableSql.toSet())
      assertThat(exported.tableSql.any { it.contains("migration_person") }).isTrue()
      assertThat(exported.tableSql.any { it.contains("submodule_persistent_value") }).isTrue()
      assertThat(exported.views).hasSize(39)
      assertThat(exported.viewSql.any { it.contains("z_submodule_dependency_base") }).isTrue()

      if (version > 1) {
        val frozen = releaseHashes(application)
        println("Consumer migration fixture: unchanged permanent native tests against release $version")
        val result = run(
          project = project,
          arguments = listOf(":app:testDebugUnitTest", "--tests", "*DatabaseMigrationTest", "--rerun-tasks")
        )
        assertThat(result.output)
          .contains("BUILD SUCCESSFUL")
        assertThat(digest(permanentTest))
          .isEqualTo(testHash)
        assertThat(releaseHashes(application))
          .isEqualTo(frozen)
        val descriptor = application.resolve(
          "build/sqlitemagic/migration-testing/sources/com/siimkinks/sqlitemagic/SqliteMagicMigrationDatabase.kt"
        )
          .readText()
        assertThat(descriptor)
          .contains("override val currentVersion = $version")
        assertThat(descriptor)
          .contains("override val releaseVariant = \"release\"")
      }
    }

    val frozen = releaseHashes(application)
    println("Consumer migration fixture: verifying configuration cache reuse and up-to-date preparation")
    val cacheArguments = listOf(
      ":app:testDebugUnitTest", "--tests", "*DatabaseMigrationTest", "--configuration-cache"
    )
    run(
      project = project,
      arguments = cacheArguments
    )
    val repeated = run(
      project = project,
      arguments = cacheArguments
    )
    assertThat(repeated.output)
      .contains("Reusing configuration cache")
    assertThat(repeated.task(":app:prepareSqliteMagicMigrationTests")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)
    assertThat(releaseHashes(application))
      .isEqualTo(frozen)

    println("Consumer migration fixture: recreating deleted independent export")
    val export = application.resolve("build/sqlitemagic/release/current.schema.json")
    assertThat(export.isFile)
      .isTrue()
    assertThat(export.delete())
      .isTrue()
    run(
      project = project,
      arguments = listOf(":app:prepareSqliteMagicMigrationTests")
    )
    assertThat(export.isFile)
      .isTrue()
    assertThat(releaseHashes(application))
      .isEqualTo(frozen)

    val runtimeDependencies = run(
      project = project,
      arguments = listOf(":app:inspectMigrationConsumerRuntimeDependencies")
    )
    assertThat(runtimeDependencies.output).contains("CONSUMER_APP_TEST_HELPER=true")
    assertThat(runtimeDependencies.output).contains("CONSUMER_LIBRARY_TEST_HELPER=false")
    assertThat(runtimeDependencies.output).contains("CONSUMER_LIBRARY_PREPARE=false")
    val dependencyNames = runtimeDependencies.output.substringAfter("CONSUMER_RUNTIME_BEGIN")
      .substringBefore("CONSUMER_RUNTIME_END")
    assertThat(dependencyNames).contains("com.siimkinks.sqlitemagic:sqlitemagic:")
    assertThat(dependencyNames).doesNotContain("migration-core")
    assertThat(dependencyNames).doesNotContain("migration-tooling")
    assertThat(dependencyNames).doesNotContain("migration-testing")
    assertThat(dependencyNames).doesNotContain("kotlinx-serialization-json")
    assertThat(dependencyNames).doesNotContain("sqlite-bundled")
    println("Consumer migration fixture: verifying conventional SQL-only release assets")
    val releaseAssembly = run(
      project = project,
      arguments = listOf(":app:assembleRelease")
    )
    assertThat(releaseAssembly.task(":app:exportSqliteMagicReleaseSchema")).isNull()
    assertThat(releaseAssembly.task(":app:prepareSqliteMagicMigrationTests")).isNull()
    val apk = application.resolve("build/outputs/apk/release")
      .walkTopDown()
      .single { it.isFile && it.extension == "apk" }
    ZipFile(apk).use { archive ->
      application.resolve("src/release/assets").listFiles().orEmpty()
        .filter { it.extension == "sql" }
        .forEach { file ->
          val entry = checkNotNull(archive.getEntry("assets/${file.name}"))
          assertThat(archive.getInputStream(entry).use(InputStream::readBytes)).isEqualTo(file.readBytes())
        }
      val entries = archive.entries().asSequence().map(ZipEntry::getName).toList()
      assertThat(entries.any { entry ->
        entry.endsWith(".struct") || entry.endsWith(".schema.json") || entry.endsWith("manifest.json") ||
            entry.contains("migration-tooling") || entry.contains("migration-testing") ||
            entry.endsWith(".so") && "sqlite" in entry
      }).isFalse()
    }
    assertThat(releaseHashes(application))
      .isEqualTo(frozen)

    val assembly = run(
      project = project,
      arguments = listOf(":app:assembleDebug", "--dry-run")
    )
    assertThat(assembly.output)
      .doesNotContain("exportSqliteMagicReleaseSchema")
    assertThat(assembly.output)
      .doesNotContain("prepareSqliteMagicMigrationTests")
    assertThat(assembly.output)
      .doesNotContain("migrateReleaseDb")
    assertThat(releaseHashes(application))
      .isEqualTo(frozen)

    println("Consumer migration fixture: refusing an unpublished selected release version")
    applicationBuild.writeText(
      configuredBuild(
        original = initialBuild,
        version = 4
      )
    )
    try {
      val failure = runner(
        project = project,
        arguments = listOf(":app:prepareSqliteMagicMigrationTests")
      ).buildAndFail()
      assertThat(failure.output)
        .contains("migrateReleaseDb")
      assertThat(failure.output)
        .contains("4")
      assertThat(releaseHashes(application))
        .isEqualTo(frozen)
    } finally {
      applicationBuild.writeText(
        configuredBuild(
          original = initialBuild,
          version = 3
        )
      )
    }

    applicationBuild.appendText(
      """

sqlitemagic {
  migrationTesting {
    enabled = false
  }
}
"""
    )
    val optedOut = run(
      project = project,
      arguments = listOf(":app:assembleDebug", ":app:assembleRelease", ":app:testDebugUnitTest", "--dry-run")
    )
    assertThat(optedOut.output).doesNotContain("exportSqliteMagic")
    assertThat(optedOut.output).doesNotContain("prepareSqliteMagic")
    val optedOutDependencies = run(
      project = project,
      arguments = listOf(":app:inspectMigrationConsumerRuntimeDependencies")
    )
    assertThat(optedOutDependencies.output).contains("CONSUMER_APP_TEST_HELPER=false")
  }

  private fun run(
    project: File,
    arguments: List<String>
  ) = runner(
    project = project,
    arguments = arguments
  ).build()

  private fun runner(
    project: File,
    arguments: List<String>
  ) = GradleRunner.create()
    .withProjectDir(project)
    .withGradleVersion("9.8.0")
    .withArguments(arguments + listOf("--console=plain", "--stacktrace"))
    .forwardOutput()

  private fun copyConsumer(
    source: File,
    destination: File
  ) {
    check(destination.mkdirs())
    source.walkTopDown()
      .onEnter { directory -> directory.name !in setOf("build", ".gradle", ".kotlin", "db", "migration-testing") }
      .filter(File::isFile)
      .filterNot { it.name == "local.properties" }
      .forEach { file ->
        val relative = file.relativeTo(source)
        val target = destination.resolve(relative.path)
        check(target.parentFile.isDirectory || target.parentFile.mkdirs())
        file.copyTo(target)
      }
  }

  private fun configureSdk(
    repository: File,
    original: File,
    project: File
  ) {
    val environmentLocations = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
      .mapNotNull(System::getenv)
      .map(::File)
    val configuredLocations = listOf(original.resolve("local.properties"), repository.resolve("local.properties"))
      .filter(File::isFile)
      .mapNotNull { file ->
        val properties = Properties()
        file.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")?.let { value ->
          File(value).takeIf(File::isAbsolute) ?: file.parentFile.resolve(value)
        }
      }
    val sdk = (environmentLocations + configuredLocations).firstOrNull(File::isDirectory)
      ?: error(
        "The real consumer integration fixture requires the configured Android SDK. Set ANDROID_HOME or " +
            "ANDROID_SDK_ROOT, or sdk.dir in sqlitemagic-tests/local.properties or repository/local.properties."
      )
    val properties = Properties()
    properties.setProperty("sdk.dir", sdk.canonicalPath)
    project.resolve("local.properties").outputStream().use { stream ->
      properties.store(stream, "SDK location for the isolated consumer integration fixture")
    }
  }

  private fun configuredBuild(
    original: String,
    version: Int
  ) = original
    .replace(
      oldValue = """
// This runtime/compiler fixture has no published release history for migration testing.
sqlitemagic {
  migrationTesting {
    enabled = false
  }
}
""",
      newValue = ""
    )
    .replace(
      oldValue = "value = if (variant.buildType == \"release\") \"2\" else \"3\"",
      newValue = "value = if (variant.buildType == \"release\") \"$version\" else \"1038\""
    ) + """

dependencies {
  testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.6.0")
}

tasks.register("inspectMigrationConsumerRuntimeDependencies") {
  doLast {
    println("CONSUMER_RUNTIME_BEGIN")
    configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
        .map { it.id.displayName }
        .sorted()
        .forEach(::println)
    println("CONSUMER_RUNTIME_END")
    println("CONSUMER_APP_TEST_HELPER=" + configurations.getByName("testImplementation").dependencies.any {
      it.name == "sqlitemagic-migration-testing"
    })
    println("CONSUMER_LIBRARY_TEST_HELPER=" + project(":submodule")
        .configurations.getByName("testImplementation").dependencies.any {
          it.name == "sqlitemagic-migration-testing"
        })
    println("CONSUMER_LIBRARY_PREPARE=" + project(":submodule").tasks.names.any {
      it.startsWith("prepareSqliteMagic") || it.startsWith("exportSqliteMagic")
    })
  }
}
"""

  private fun modelSource(version: Int): String {
    val derived = when (version) {
      1 -> ""
      2 -> ",\n  @Column(\"name_length\") val nameLength: Int? = null"
      else -> ",\n  @Column(\"name_length\") val nameLength: Int? = null,\n" +
          "  @Index(\"migration_person_slug\") val slug: String? = null"
    }
    return """
package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class MigrationPerson(
  @Id(autoIncrement = false) val id: Long,
  val name: String${derived}
)
""".trimIndent() + "\n"
  }

  private fun releaseHashes(application: File): Map<String, String> = listOf(
    application.resolve("db/releases"),
    application.resolve("src/release/assets")
  )
    .flatMap { directory ->
      directory.walkTopDown()
        .filter(File::isFile)
        .toList()
    }
    .associate { file -> file.relativeTo(application).invariantSeparatorsPath to digest(file) }

  private fun digest(file: File) = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString(separator = "") { "%02x".format(it) }

  private fun findRepository(): File = generateSequence(
    seed = File(System.getProperty("user.dir")),
    nextFunction = File::getParentFile
  )
    .first { it.resolve("sqlitemagic-tests/settings.gradle.kts").isFile }
}
