package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationStep
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

/** Exercises AGP's actual asset merge and published KSP artifacts, including native SQLite data migrations. */
internal class MergedMigrationAssetsConsumerIntegrationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `paid debug migration tests consume packaged paid release assets and observe asset changes`() {
    val repository = generateSequence(
      seed = File(System.getProperty("user.dir")),
      nextFunction = File::getParentFile
    ).first { it.resolve("sqlitemagic-tests/settings.gradle.kts").isFile }
    val project = temporaryDirectory.resolve("merged-assets-consumer").toFile()
    val original = repository.resolve("sqlitemagic-tests")
    writeFile(
      file = project.resolve("settings.gradle.kts"),
      text = original.resolve("settings.gradle.kts")
        .readText()
        .substringBefore("include(\":app\")") + "include(\":app\", \":asset-library\")\n"
    )
    writeFile(
      file = project.resolve("build.gradle.kts"),
      text = original.resolve("build.gradle.kts").readText()
    )
    writeFile(
      file = project.resolve("gradle.properties"),
      text = original.resolve("gradle.properties").readText()
    )
    writeFile(
      file = project.resolve("gradle/libs.versions.toml"),
      text = original.resolve("gradle/libs.versions.toml").readText()
    )
    val configuredProperties = listOf(original.resolve("local.properties"), repository.resolve("local.properties"))
      .filter(File::isFile)
      .map { file -> Properties().apply { file.inputStream().use(::load) } }
    val sdk = (listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").mapNotNull(System::getenv) +
        configuredProperties.mapNotNull { it.getProperty("sdk.dir") })
      .map(::File)
      .firstOrNull(File::isDirectory)
      ?: error("The merged migration asset consumer integration test requires a configured Android SDK")
    val sdkProperties = Properties()
    sdkProperties.setProperty("sdk.dir", sdk.canonicalPath)
    project.resolve("local.properties").outputStream().use { stream ->
      sdkProperties.store(stream, "SDK for merged migration assets regression")
    }
    writeFile(
      file = project.resolve("app/build.gradle.kts"),
      text = checkNotNull(javaClass.getResourceAsStream("/migration-consumer/merged-assets-app.gradle.kts"))
        .bufferedReader()
        .use(BufferedReader::readText)
    )
    writeFile(
      file = project.resolve("asset-library/build.gradle.kts"),
      text = """
plugins {
  alias(libs.plugins.android.library)
}
android {
  namespace = "com.siimkinks.assetlibrary"
  compileSdk = libs.versions.android.compile.sdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.android.min.sdk.get().toInt()
  }
}
""".trimIndent() + "\n"
    )
    listOf("app", "asset-library").forEach { module ->
      writeFile(
        file = project.resolve("$module/src/main/AndroidManifest.xml"),
        text = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />\n"
      )
    }
    writeFile(
      file = project.resolve("app/src/main/kotlin/com/siimkinks/sqlitemagic/AssetDatabase.kt"),
      text = """
package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Database(
  name = BuildConfig.DB_NAME,
  version = BuildConfig.DB_VERSION
)
object AssetDatabase

@Table
data class AssetRecord(
  @Id(autoIncrement = false) val id: Long,
  val value: String
)
""".trimIndent() + "\n"
    )
    val application = project.resolve("app")
    val scripts = mapOf(
      "src/main/assets/2.sql" to "main",
      "src/paid/assets/3.sql" to "paid",
      "src/paidRelease/assets/4.sql" to "variant",
      "src/main/assets/6.sql" to "wrong-main-overlay",
      "src/paid/assets/6.sql" to "wrong-paid-overlay",
      "src/release/assets/6.sql" to "release-overlay",
      "src/debug/assets/2.sql" to "wrong-debug",
      "src/free/assets/3.sql" to "wrong-free",
      "custom-assets/7.sql" to "custom"
    )
    scripts.forEach { (path, label) ->
      writeFile(
        file = application.resolve(path),
        text = sql(label)
      )
    }
    writeFile(
      file = project.resolve("asset-library/src/main/assets/5.sql"),
      text = sql("dependency")
    )

    run(
      project = project,
      arguments = listOf(":app:migratePaidReleaseDb", "-PreleaseVersion=1")
    )
    run(
      project = project,
      arguments = listOf(":app:migratePaidReleaseDb", "-PreleaseVersion=9")
    )
    val manifestFile = application.resolve("db/releases/manifest.json")
    val manifestText = manifestFile.readText()
    val manifest = MigrationMetadataJson.readManifest(manifestText)
    assertThat(manifest.releaseVariant).isEqualTo("paidRelease")
    assertThat(
      manifest.steps.flatMap(MigrationStep::sqlResources)
        .map(MigrationResource::name)
    )
      .containsExactly("2.sql", "3.sql", "4.sql", "5.sql", "6.sql", "7.sql", "8.sql")
      .inOrder()
    // A data-only main asset must not become a newly generated build-type overlay.
    assertThat(application.resolve("src/release/assets/2.sql").exists()).isFalse()
    assertThat(application.resolve("src/paidRelease/assets/2.sql").exists()).isFalse()

    val testTemplate = checkNotNull(javaClass.getResourceAsStream("/migration-consumer/MergedAssetsMigrationTest.kt"))
      .bufferedReader()
      .use(BufferedReader::readText)
    val testFile = application.resolve("src/test/kotlin/com/siimkinks/sqlitemagic/MergedAssetsMigrationTest.kt")
    writeFile(
      file = testFile,
      text = testTemplate.replace(
        oldValue = "EXPECTED_VALUE",
        newValue = "seed|main|paid|variant|dependency|release-overlay|custom|generated"
      )
    )
    val arguments = listOf(
      ":app:testPaidDebugUnitTest", "--tests", "*MergedAssetsMigrationTest", ":app:assemblePaidRelease",
      "-PreleaseVersion=9", "--configuration-cache"
    )
    run(
      project = project,
      arguments = arguments
    )
    assertPackagedResources(application)
    val repeated = run(
      project = project,
      arguments = arguments
    )
    assertThat(repeated.output).contains("Reusing configuration cache")
    assertThat(repeated.task(":app:prepareSqliteMagicMigrationTests")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    val changes = listOf(
      AssetChange(
        label = "add selected variant overlay",
        path = "src/paidRelease/assets/6.sql",
        suffix = "added-variant-overlay"
      ),
      AssetChange(
        label = "edit selected variant overlay",
        path = "src/paidRelease/assets/6.sql",
        suffix = "edited-variant-overlay"
      )
    )
    changes.forEach { change ->
      println("Merged migration asset regression: ${change.label}")
      writeFile(
        file = application.resolve(change.path),
        text = sql(change.suffix)
      )
      testFile.writeText(
        testTemplate.replace(
          oldValue = "EXPECTED_VALUE",
          newValue = "seed|main|paid|variant|dependency|${change.suffix}|custom|generated"
        )
      )
      val changed = run(
        project = project,
        arguments = arguments
      )
      assertThat(changed.output).contains("Reusing configuration cache")
      assertThat(changed.task(":app:prepareSqliteMagicMigrationTests")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
      assertPackagedResources(application)
      assertThat(manifestFile.readText()).isEqualTo(manifestText)
    }
    // Optional conventional SQL is discovered even when the immutable published step had no SQL.
    writeFile(
      file = application.resolve("src/main/assets/9.sql"),
      text = sql("optional")
    )
    testFile.writeText(
      testTemplate.replace(
        oldValue = "EXPECTED_VALUE",
        newValue = "seed|main|paid|variant|dependency|edited-variant-overlay|custom|generated|optional"
      )
    )
    val added = run(
      project = project,
      arguments = arguments
    )
    assertThat(added.output).contains("Reusing configuration cache")
    assertThat(added.task(":app:prepareSqliteMagicMigrationTests")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertPackagedResources(application)
    val copiedAddition = application.resolve("build/sqlitemagic/migration-testing/resources")
      .walkTopDown()
      .single { it.isFile && it.name == "9.sql" }
    assertThat(copiedAddition.readBytes()).isEqualTo(application.resolve("src/main/assets/9.sql").readBytes())
    assertThat(manifestFile.readText()).isEqualTo(manifestText)
  }

  private fun assertPackagedResources(application: File) {
    val apk = application.resolve("build/outputs/apk/paid/release")
      .walkTopDown()
      .single { it.isFile && it.extension == "apk" }
    val resources = application.resolve("build/sqlitemagic/migration-testing/resources")
    ZipFile(apk).use { archive ->
      val versions = when {
        application.resolve("src/main/assets/9.sql").exists() -> 2..9
        else -> 2..8
      }
      versions.forEach { version ->
        val name = "$version.sql"
        val copied = resources.walkTopDown().single { it.isFile && it.name == name }
        val entry = checkNotNull(archive.getEntry("assets/$name"))
        assertThat(copied.readBytes())
          .isEqualTo(archive.getInputStream(entry).use(InputStream::readBytes))
      }
    }
  }


  private fun run(
    project: File,
    arguments: List<String>
  ) = GradleRunner.create()
    .withProjectDir(project)
    .withGradleVersion("9.8.0")
    .withArguments(arguments + listOf("--console=plain", "--stacktrace"))
    .forwardOutput()
    .build()

  private fun sql(label: String) = "UPDATE asset_record SET value=value || '|$label'\n"

  private fun writeFile(
    file: File,
    text: String
  ) {
    check(file.parentFile.isDirectory || file.parentFile.mkdirs())
    file.writeText(text)
  }

  private data class AssetChange(
    val label: String,
    val path: String,
    val suffix: String
  )
}
