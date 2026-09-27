package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class SqliteMagicKspPluginStagingTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `adding a view-only module marks the main structure changed`() {
    val destination = databaseDirectory()
    val stagedDirectory = stagedDirectory()
    writeStructure(
      directory = stagedDirectory,
      name = "latest_feature.struct",
      json = """{"views":{"feature_view":{"name":"feature_view","moduleName":"feature"}}}"""
    )

    publishStagedStructures(
      stagedDirectories = listOf(stagedDirectory),
      destination = destination
    )

    assertThat(destination.resolve("latest_feature.struct").isFile)
      .isTrue()
    assertThat(destination.resolve("submodules.changed").isFile)
      .isTrue()
  }

  @Test
  fun `removing a view-only module marks the main structure changed`() {
    val destination = databaseDirectory()
    writeStructure(
      directory = destination,
      name = "latest_feature.struct",
      json = """{"views":{"feature_view":{"name":"feature_view","moduleName":"feature"}}}"""
    )

    publishStagedStructures(
      stagedDirectories = listOf(stagedDirectory()),
      destination = destination
    )

    assertThat(destination.resolve("latest_feature.struct").exists())
      .isFalse()
    assertThat(destination.resolve("submodules.changed").readText())
      .isEqualTo("feature_view\n")
  }

  @Test
  fun `removing the last view-only module preserves existing marker names`() {
    val destination = databaseDirectory()
    val previousMarker = destination.resolve("submodules.changed")
    previousMarker.writeText("earlier_view\nfeature_view\n")
    writeStructure(
      directory = destination,
      name = "latest_feature.struct",
      json = """{"views":{"feature_view":{"name":"feature_view","moduleName":"feature"}}}"""
    )

    publishStagedStructures(
      stagedDirectories = emptyList(),
      destination = destination
    )

    assertThat(destination.resolve("latest_feature.struct").exists())
      .isFalse()
    assertThat(previousMarker.readText())
      .isEqualTo("earlier_view\nfeature_view\n")
  }

  @Test
  fun `view rename then module removal records both prior names across main versions`() {
    val destination = databaseDirectory()
    val stagedDirectory = stagedDirectory()
    writeStructure(
      directory = destination,
      name = "latest_feature.struct",
      json = """{"views":{"view_a":{"name":"view_a","moduleName":"feature"}}}"""
    )
    writeStructure(
      directory = stagedDirectory,
      name = "latest_feature.struct",
      json = """{"views":{"view_b":{"name":"view_b","moduleName":"feature"}}}"""
    )
    val marker = destination.resolve("submodules.changed")

    publishStagedStructures(
      stagedDirectories = listOf(stagedDirectory),
      destination = destination
    )

    val versionNMarker = marker.readText()
    assertThat(versionNMarker)
      .isEqualTo("view_a\n")
    check(marker.delete())

    publishStagedStructures(
      stagedDirectories = emptyList(),
      destination = destination
    )

    val versionNPlusOneMarker = marker.readText()
    assertThat(versionNPlusOneMarker)
      .isEqualTo("view_b\n")
    assertThat(versionNMarker + versionNPlusOneMarker)
      .isEqualTo("view_a\nview_b\n")
  }

  @Test
  fun `a temporary-view-only module does not mark the main structure changed`() {
    val destination = databaseDirectory()
    val stagedDirectory = stagedDirectory()
    writeStructure(
      directory = stagedDirectory,
      name = "latest_feature.struct",
      json = """{"temporaryViews":{"session_view":{"name":"session_view","moduleName":"feature"}}}"""
    )

    publishStagedStructures(
      stagedDirectories = listOf(stagedDirectory),
      destination = destination
    )

    assertThat(destination.resolve("latest_feature.struct").isFile)
      .isTrue()
    assertThat(destination.resolve("submodules.changed").exists())
      .isFalse()
  }

  @Test
  fun `a write failure restores the previously published structures`() {
    val destination = databaseDirectory()
    val previousJson = """{"views":{"old_view":{"name":"old_view","moduleName":"feature"}}}"""
    writeStructure(
      directory = destination,
      name = "latest_feature.struct",
      json = previousJson
    )
    val blockedJson = """{"views":{"blocked_view":{"name":"blocked_view","moduleName":"blocked"}}}"""
    writeStructure(
      directory = destination,
      name = "latest_blocked.struct",
      json = blockedJson
    )
    val firstStage = temporaryDirectory.resolve("first-stage").toFile()
    val secondStage = temporaryDirectory.resolve("second-stage").toFile()
    writeStructure(
      directory = secondStage,
      name = "latest_fail.struct",
      json = """{"views":{"failed_view":{"name":"failed_view","moduleName":"failed"}}}"""
    )
    val blocker = destination.resolve("latest_fail.struct")
    check(blocker.mkdirs())
    val blockerContents = blocker.resolve("keep.txt")
    blockerContents.writeText("leave this directory intact")
    val previousMarker = destination.resolve("submodules.changed")
    previousMarker.writeText("earlier_view\n")
    writeStructure(
      directory = firstStage,
      name = "latest_new.struct",
      json = """{"views":{"new_view":{"name":"new_view","moduleName":"feature"}}}"""
    )

    assertThrows<Exception> {
      publishStagedStructures(
        stagedDirectories = listOf(firstStage, secondStage),
        destination = destination
      )
    }

    assertThat(destination.resolve("latest_feature.struct").readText())
      .isEqualTo(previousJson)
    assertThat(destination.resolve("latest_blocked.struct").readText())
      .isEqualTo(blockedJson)
    assertThat(destination.resolve("latest_new.struct").exists())
      .isFalse()
    assertThat(blocker.isDirectory)
      .isTrue()
    assertThat(blockerContents.readText())
      .isEqualTo("leave this directory intact")
    assertThat(previousMarker.readText())
      .isEqualTo("earlier_view\n")
  }

  @Test
  fun `release input selection uses release staging after debug publication`() {
    val destination = databaseDirectory()
    val stagedMain = temporaryDirectory.resolve("release-staged/latest.struct").toFile()
    val releaseFeatureDirectory = temporaryDirectory.resolve("release-feature").toFile()
    val releaseExtraDirectory = temporaryDirectory.resolve("release-extra").toFile()
    val debugDirectory = temporaryDirectory.resolve("debug-feature").toFile()
    writeStructure(
      directory = checkNotNull(stagedMain.parentFile),
      name = stagedMain.name,
      json = "{}"
    )
    writeStructure(
      directory = releaseFeatureDirectory,
      name = "latest_feature.struct",
      json = "{}"
    )
    writeStructure(
      directory = releaseExtraDirectory,
      name = "latest_extra.struct",
      json = "{}"
    )
    writeStructure(
      directory = releaseExtraDirectory,
      name = "stale.struct",
      json = "{}"
    )
    writeStructure(
      directory = debugDirectory,
      name = "latest_feature.struct",
      json = "{}"
    )
    publishStagedStructures(
      stagedDirectories = listOf(debugDirectory),
      destination = destination
    )

    assertThat(destination.resolve("latest_feature.struct").isFile)
      .isTrue()
    assertThat(
      releaseCurrentStructureFiles(
        stagedMainFile = stagedMain,
        stagedSubmoduleDirectories = listOf(releaseFeatureDirectory, releaseExtraDirectory)
      )
    ).containsExactly(
      stagedMain,
      releaseExtraDirectory.resolve("latest_extra.struct"),
      releaseFeatureDirectory.resolve("latest_feature.struct")
    ).inOrder()
  }

  @Test
  fun `release input selection with no submodules contains only staged main`() {
    val stagedMain = temporaryDirectory.resolve("release-staged/latest.struct").toFile()

    assertThat(
      releaseCurrentStructureFiles(
        stagedMainFile = stagedMain,
        stagedSubmoduleDirectories = emptyList()
      )
    ).containsExactly(stagedMain)
  }

  private fun databaseDirectory() = temporaryDirectory.resolve("db").toFile().apply {
    check(mkdirs())
    resolve("latest.struct").writeText("{}")
  }

  private fun stagedDirectory() = temporaryDirectory.resolve("staged").toFile().apply {
    check(mkdirs())
  }

  private fun writeStructure(
    directory: File,
    name: String,
    json: String
  ) {
    check(directory.isDirectory || directory.mkdirs())
    directory.resolve(name).writeText(json)
  }
}
