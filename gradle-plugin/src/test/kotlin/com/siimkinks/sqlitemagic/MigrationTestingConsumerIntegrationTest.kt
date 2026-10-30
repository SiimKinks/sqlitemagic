package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
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

internal class MigrationTestingConsumerIntegrationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `real selected release permanently tests growing retained history`() {
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
      if (version == 2) {
        applicationBuild.writeText(
          configuredBuild(
            original = initialBuild,
            version = 1
          )
        )
        declaredModel.writeText(modelSource(version))
        val released = releaseHashes(application)
        println("Consumer migration fixture: previewing release 2 before raising the database version")
        val preview = run(
          project = project,
          arguments = listOf(":app:previewReleaseDbMigration")
        )
        assertThat(preview.output).contains(
          "Release 2 preview (latest release 1)\nDatabase version 1 is not above the latest release; " +
              "set @Database(version = 2) before publishing this release."
        )
        assertThat(releaseHashes(application)).isEqualTo(released)
      }
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
        if (version == 2) {
          assertThat(sql.readText()).isEqualTo(
            application
              .resolve("build/sqlitemagic/release/migration-preview/2.sql")
              .readText()
          )
        }
        sql.appendText(
          when (version) {
            2 -> "\nUPDATE migration_person SET name=upper(name), name_length=length(name)\n"
            else -> "\nUPDATE migration_person SET slug=lower(name)\n"
          }
        )
      }
      val manifest = MigrationMetadataJson.readManifest(application.resolve("db/releases/manifest.json").readText())
      val reference = manifest.schema(version)
      val releasedStructure = application.resolve("db/releases/$version.struct").readText()
      val snapshot = MigrationMetadataJson.readStructure(
        text = releasedStructure,
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
      assertThat(exported.views.map(MigrationViewSchema::name))
        .containsExactlyElementsIn(MigrationMetadataJson.structureViewNames(releasedStructure))
      assertThat(exported.viewSql.any { it.contains("z_submodule_dependency_base") }).isTrue()

      if (version == 1) {
        val beforeAdoption = releaseHashes(application)
        check(application.resolve("db/releases/manifest.json").delete())
        val cleanup = run(
          project = project,
          arguments = listOf(":app:cleanSqliteMagicMetadata", "--configuration-cache")
        )
        assertThat(cleanup.task(":submodule:cleanSqliteMagicMetadata")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val adoptionArguments = listOf(":app:generateSqliteMagicMigrationManifest", "--configuration-cache")
        run(
          project = project,
          arguments = adoptionArguments
        )
        val repeatedAdoption = run(
          project = project,
          arguments = adoptionArguments
        )
        assertThat(repeatedAdoption.output).contains("Reusing configuration cache")
        assertThat(repeatedAdoption.output).contains("migration manifest already exists")
        assertThat(releaseHashes(application)).isEqualTo(beforeAdoption)
      }

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
          .contains("""override val releaseVariant = "release"""")
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

    println("Consumer migration fixture: selecting coverage in code while earlier history is unusable")
    val oldSchema = application.resolve("db/releases/1.struct")
    val oldScript = application.resolve("src/release/assets/2.sql")
    val oldEvidence = listOf(oldSchema, oldScript).associateWith(File::readBytes)
    val baselineTest = permanentTest.resolveSibling("BaselineMigrationTest.kt")
    baselineTest.writeText(
      """
      package com.siimkinks.sqlitemagic

      import androidx.sqlite.driver.bundled.BundledSQLiteDriver
      import com.google.common.truth.Truth.assertThat
      import org.junit.jupiter.api.Test

      internal class BaselineMigrationTest {
        @Test
        fun `validates from the code-selected baseline`() {
          SqliteMagicMigrationTestHelper(
            descriptor = SqliteMagicMigrationDatabase,
            driver = BundledSQLiteDriver()
          ).use { helper ->
            val report = helper.validateAllMigrations(fromVersion = 2)
            assertThat(report).isEqualTo(
              MigrationValidationReport(
                databaseId = SqliteMagicMigrationDatabase.databaseId,
                releaseVariant = "release",
                baselineVersion = 2,
                currentVersion = 3,
                excludedVersions = listOf(1),
                paths = listOf(
                  MigrationValidationPath(
                    fromVersion = 2,
                    toVersion = 3
                  )
                ),
                mode = MigrationValidationMode.EVERY_VERSION_TO_CURRENT,
                driverImplementation = BundledSQLiteDriver::class.java.name,
                sqliteVersion = report.sqliteVersion
              )
            )
          }
        }
      }
      """.trimIndent() + "\n"
    )
    try {
      oldSchema.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
      check(oldScript.delete())
      val selectedEvidence = releaseHashes(application)
      run(
        project = project,
        arguments = listOf(":app:testDebugUnitTest", "--tests", "*BaselineMigrationTest", "--configuration-cache")
      )
      assertThat(releaseHashes(application)).isEqualTo(selectedEvidence)
    } finally {
      oldEvidence.forEach { (file, bytes) -> file.writeBytes(bytes) }
      check(baselineTest.delete())
    }
    assertThat(releaseHashes(application)).isEqualTo(frozen)

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
    assertThat(dependencyNames).contains("com.siimkinks.sqlitemagic:sqlitemagic-coroutines:")
    assertThat(dependencyNames).contains("com.siimkinks.sqlitemagic:sqlitemagic-rxjava2:")
    assertThat(dependencyNames).contains("com.siimkinks.sqlitemagic:sqlitemagic-rxjava3:")
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

    val ordinaryTest = permanentTest.resolveSibling("OrdinaryUnitTest.kt")
    ordinaryTest.writeText(
      """
      package com.siimkinks.sqlitemagic

      import com.google.common.truth.Truth.assertThat
      import org.junit.jupiter.api.Test

      internal class OrdinaryUnitTest {
        @Test
        fun `runs without instantiating a migration helper`() {
          assertThat(SqliteMagicMigrationDatabase.releaseVariant).isEqualTo("release")
        }
      }
      """.trimIndent() + "\n"
    )
    val manifestFile = application.resolve("db/releases/manifest.json")
    val currentStructure = application.resolve("db/releases/3.struct")
    val currentEvidence = listOf(manifestFile, currentStructure).associateWith(File::readBytes)
    val pendingModel = modelSource(3)
      .replace(
        oldValue = "val slug: String? = null",
        newValue = "val slug: String? = null,\n  val pendingNote: String? = null"
      )
    val cases = mapOf(
      "schema changed without a version bump" to "Advance the database version",
      "unpublished version" to "version 4 differs from published 3",
      "missing manifest" to "Missing migration manifest",
      "malformed manifest" to "Invalid migration manifest",
      "missing current schema" to "Missing or invalid current release snapshot",
      "malformed current schema" to "Missing or invalid current release snapshot"
    )
    try {
      cases.forEach { (label, diagnostic) ->
        println("Consumer migration fixture: ordinary tests run with $label; migration tests fail at execution")
        currentEvidence.forEach { (file, bytes) -> file.writeBytes(bytes) }
        applicationBuild.writeText(
          configuredBuild(
            original = initialBuild,
            version = when (label) {
              "unpublished version" -> 4
              else -> 3
            }
          )
        )
        declaredModel.writeText(
          when (label) {
            "schema changed without a version bump", "unpublished version" -> pendingModel
            else -> modelSource(3)
          }
        )
        when (label) {
          "missing manifest" -> check(manifestFile.delete())
          "malformed manifest" -> manifestFile.writeText("malformed")
          "missing current schema" -> check(currentStructure.delete())
          "malformed current schema" -> currentStructure.writeText("malformed")
        }
        val pendingEvidence = releaseHashes(application)
        val ordinary = run(
          project = project,
          arguments = listOf(":app:testDebugUnitTest", "--tests", "*OrdinaryUnitTest", "--configuration-cache")
        )
        assertThat(ordinary.task(":app:compileDebugUnitTestKotlin")?.outcome)
          .isIn(setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE))
        assertThat(ordinary.task(":app:testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val failure = runner(
          project = project,
          arguments = listOf(":app:testDebugUnitTest", "--tests", "*DatabaseMigrationTest", "--configuration-cache")
        ).buildAndFail()
        assertThat(failure.task(":app:prepareSqliteMagicMigrationTests")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(failure.task(":app:compileDebugUnitTestKotlin")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(failure.task(":app:testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.FAILED)
        val report = application
          .resolve("build/test-results/testDebugUnitTest/TEST-com.siimkinks.sqlitemagic.DatabaseMigrationTest.xml")
          .readText()
        assertThat(report).contains(diagnostic)
        assertThat(report).contains(":app:migrateReleaseDb")
        assertThat(releaseHashes(application)).isEqualTo(pendingEvidence)
      }
    } finally {
      currentEvidence.forEach { (file, bytes) -> file.writeBytes(bytes) }
      declaredModel.writeText(modelSource(3))
      applicationBuild.writeText(
        configuredBuild(
          original = initialBuild,
          version = 3
        )
      )
      check(ordinaryTest.delete())
    }
    assertThat(releaseHashes(application)).isEqualTo(frozen)

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
      .onEnter { it.name !in setOf("build", ".gradle", ".kotlin", "db", "migration-testing") }
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
        val properties = Properties().apply { file.inputStream().use(::load) }
        properties.getProperty("sdk.dir")?.let { value ->
          File(value).takeIf(File::isAbsolute) ?: file.parentFile.resolve(value)
        }
      }
    val sdk = (environmentLocations + configuredLocations).firstOrNull(File::isDirectory)
      ?: error(
        "The real consumer integration fixture requires the configured Android SDK. Set ANDROID_HOME or " +
            "ANDROID_SDK_ROOT, or sdk.dir in sqlitemagic-tests/local.properties or repository/local.properties."
      )
    val properties = Properties().apply { setProperty("sdk.dir", sdk.canonicalPath) }
    project
      .resolve("local.properties")
      .outputStream()
      .use { properties.store(it, "SDK location for the isolated consumer integration fixture") }
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
      oldValue = """value = if (variant.buildType == "release") "2" else "3"""",
      newValue = """value = if (variant.buildType == "release") "$version" else "1038""""
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
    val derived = listOf(
      """@Column("name_length") val nameLength: Int? = null""",
      """@Index("migration_person_slug") val slug: String? = null"""
    )
      .take(version - 1)
      .joinToString(separator = "") { ",\n  $it" }
    return """
package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class MigrationPerson(
  @Id(autoIncrement = false) val id: Long,
  val name: String$derived
)
""".trimIndent() + "\n"
  }

  private fun releaseHashes(application: File) = listOf(
    application.resolve("db/releases"),
    application.resolve("src/release/assets")
  )
    .flatMap {
      it.walkTopDown()
        .filter(File::isFile)
        .toList()
    }
    .associate { it.relativeTo(application).invariantSeparatorsPath to digest(it) }

  private fun digest(file: File) = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString(separator = "") { "%02x".format(it) }

  private fun findRepository() = generateSequence(
    seed = File(System.getProperty("user.dir")),
    nextFunction = File::getParentFile
  )
    .first { it.resolve("sqlitemagic-tests/settings.gradle.kts").isFile }
}
