package com.siimkinks.sqlitemagic.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class StructureMetadataTest {
  @Test
  fun `legacy structures infer cascade configuration and retain no view SQL`() {
    val cases = mapOf("omitted default" to "{}", "cascade" to "{\"onDeleteCascade\":true}")
    cases.forEach { (label, column) ->
      val text = """{"tables":{"books":{"schema":"CREATE TABLE books(id INTEGER)","columns":[$column]}},
        "views":{"titles":{"name":"titles"}},"temporaryTables":{},"unrelatedCompilerDetail":true}"""
      val reference = ReleaseSchemaResource(version = 1, name = "schemas/1.struct")
      val manifest = MigrationManifest(
        formatVersion = 1,
        databaseId = "main",
        releaseVariant = "release",
        currentVersion = 1,
        modules = listOf("main"),
        schemas = listOf(reference),
        steps = emptyList()
      )
      assertThat(
        MigrationMetadataJson.readStructure(
          text = text,
          manifest = manifest,
          reference = reference
        )
      ).isEqualTo(
        ReleaseSchemaSnapshot(
          formatVersion = 1,
          databaseId = "main",
          releaseVariant = "release",
          version = 1,
          modules = listOf("main"),
          configuration = MigrationConfiguration(foreignKeysEnabled = label == "cascade"),
          tableSql = listOf("CREATE TABLE books(id INTEGER)"),
          views = emptyList(),
          indexSql = emptyList()
        )
      )
      assertThat(MigrationMetadataJson.structureViewNames(text)).containsExactly("titles")
      val configured = reference.copy(configuration = MigrationConfiguration(foreignKeysEnabled = false))
      assertThat(
        MigrationMetadataJson.readStructure(
          text = text,
          manifest = manifest,
          reference = configured
        ).configuration
      ).isEqualTo(MigrationConfiguration(foreignKeysEnabled = false))
    }
  }

  @Test
  fun `missing SQL malformed types and malformed JSON fail strictly`() {
    val cases = listOf(
      "{\"tables\":{\"books\":{}}}",
      "{\"indices\":{\"books_idx\":{}}}",
      "{\"tables\":{\"books\":{\"schema\":null}}}",
      "{\"tables\":{\"books\":{\"schema\":\"\"}}}",
      "{\"views\":{\"titles\":{}}}",
      "broken"
    )
    val reference = ReleaseSchemaResource(version = 1, name = "schemas/1.struct")
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = "main",
      releaseVariant = "release",
      currentVersion = 1,
      modules = listOf("main"),
      schemas = listOf(reference),
      steps = emptyList()
    )
    cases.forEach { text ->
      assertThrows(text, IllegalArgumentException::class.java) {
        MigrationMetadataJson.readStructure(
          text = text,
          manifest = manifest,
          reference = reference
        )
      }
    }
  }
}
