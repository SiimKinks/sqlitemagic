package com.siimkinks.sqlitemagic.migration

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

internal class MigrationMetadataTest {
  @Test
  fun `temporary compiled schema round-trips`() {
    val schema = snapshot()
    assertThat(MigrationMetadataJson.readSchema(MigrationMetadataJson.writeSchema(schema))).isEqualTo(schema)
  }

  @Test
  fun `schema rejects unsupported formats and missing evidence`() {
    val valid = MigrationMetadataJson.writeSchema(snapshot())
    val cases = mapOf(
      "new format" to valid.replace(oldValue = "\"formatVersion\":1", newValue = "\"formatVersion\":2"),
      "missing configuration" to valid.replace(
        oldValue = "\"configuration\":{\"foreignKeysEnabled\":true},",
        newValue = ""
      ),
      "missing view evidence" to valid.replace(oldValue = "\"views\":[],", newValue = ""),
      "missing foreign key policy" to valid.replace(oldValue = "\"foreignKeysEnabled\":true", newValue = "")
    )
    cases.forEach { (label, text) ->
      assertThrows(label, IllegalArgumentException::class.java) { MigrationMetadataJson.readSchema(text) }
    }
  }

  @Test
  fun `manifest round-trips and resolves the complete plan`() {
    val manifest = manifest()
    assertThat(MigrationMetadataJson.readManifest(MigrationMetadataJson.writeManifest(manifest))).isEqualTo(manifest)
    assertThat(manifest.resolve(fromVersion = 1, toVersion = 3)).isEqualTo(
      MigrationPlan(
        fromVersion = 1,
        toVersion = 3,
        steps = manifest.steps
      )
    )
  }

  @Test
  fun `manifest rejects invalid metadata with labeled cases`() {
    val valid = manifest()
    val cases = mapOf(
      "new format" to valid.copy(formatVersion = 2),
      "duplicate modules" to valid.copy(modules = listOf("main", "main")),
      "duplicate versions" to valid.copy(schemas = valid.schemas + valid.schemas.last()),
      "unordered versions" to valid.copy(schemas = valid.schemas.reversed()),
      "missing target schema" to valid.copy(schemas = valid.schemas.dropLast(1)),
      "empty adopted view name" to valid.copy(
        schemas = valid.schemas.map { it.copy(viewNames = listOf("")) }
      ),
      "duplicate adopted view names" to valid.copy(
        schemas = valid.schemas.map { it.copy(viewNames = listOf("titles", "titles")) }
      ),
      "traversal" to valid.copy(schemas = listOf(ReleaseSchemaResource(version = 3, name = "../3.struct"))),
      "absolute path" to valid.copy(schemas = listOf(ReleaseSchemaResource(version = 3, name = "/3.struct"))),
      "duplicate resource" to valid.copy(
        schemas = valid.schemas.map { it.copy(name = "schemas/same.struct") }
      ),
      "optional listed script" to valid.copy(
        steps = listOf(
          MigrationStep(
            toVersion = 2,
            sqlResources = listOf(MigrationResource(name = "2.sql", required = false))
          )
        )
      )
    )
    cases.forEach { (label, value) ->
      assertThrows(label, IllegalArgumentException::class.java, value::validate)
    }
  }

  @Test
  fun `missing intervening steps fail instead of skipping versions`() {
    val manifest = manifest().copy(steps = manifest().steps.drop(1))
    assertThrows(IllegalArgumentException::class.java) {
      manifest.resolve(fromVersion = 1, toVersion = 3)
    }
  }

  @Test
  fun `same version needs no upgrade steps`() {
    assertThat(manifest().resolve(fromVersion = 3, toVersion = 3)).isEqualTo(
      MigrationPlan(
        fromVersion = 3,
        toVersion = 3,
        steps = emptyList()
      )
    )
  }

  @Test
  fun `retained schemas must match manifest identity and module order`() {
    val manifest = manifest()
    val schema = snapshot()
    manifest.validateSchema(schema)
    val cases = mapOf(
      "different database" to schema.copy(databaseId = "other"),
      "different flavor" to schema.copy(releaseVariant = "otherRelease"),
      "main module first" to schema.copy(modules = schema.modules.reversed()),
      "unregistered module" to schema.copy(modules = listOf("Other", "main")),
      "unretained version" to schema.copy(version = 4)
    )
    cases.forEach { (label, value) ->
      assertThrows(label, RuntimeException::class.java) {
        value.validate()
        manifest.validateSchema(value)
      }
    }
  }

  @Test
  fun `historical module additions, removals and order remain valid`() {
    val manifest = manifest().copy(modules = listOf("Removed", "Feature", "Added", "main"))
    val cases = mapOf(
      "removed module" to listOf("Removed", "main"),
      "added module" to listOf("Feature", "Added", "main"),
      "changed release order" to listOf("Added", "Feature", "main")
    )
    cases.forEach { (_, modules) -> manifest.validateSchema(snapshot().copy(modules = modules)) }
  }

  @Test
  fun `evidence plans can start before retained schema history`() {
    val manifest = manifest().copy(schemas = manifest().schemas.drop(1))
    assertThat(manifest.resolve(fromVersion = 1, toVersion = 3)).isEqualTo(
      MigrationPlan(
        fromVersion = 1,
        toVersion = 3,
        steps = manifest.steps
      )
    )
  }

  private fun snapshot() = ReleaseSchemaSnapshot(
    formatVersion = 1,
    databaseId = "main",
    releaseVariant = "release",
    version = 3,
    modules = listOf("Feature", "main"),
    configuration = MigrationConfiguration(foreignKeysEnabled = true),
    tableSql = listOf("CREATE TABLE books (id INTEGER PRIMARY KEY)"),
    views = emptyList(),
    indexSql = emptyList()
  )

  private fun manifest() = MigrationManifest(
    formatVersion = 1,
    databaseId = "main",
    releaseVariant = "release",
    currentVersion = 3,
    modules = listOf("Feature", "main"),
    schemas = (1..3).map { version ->
      ReleaseSchemaResource(
        version = version,
        name = "schemas/$version.struct"
      )
    },
    steps = (2..3).map { version ->
      MigrationStep(
        toVersion = version,
        sqlResources = listOf(MigrationResource("$version.sql"))
      )
    }
  )
}
