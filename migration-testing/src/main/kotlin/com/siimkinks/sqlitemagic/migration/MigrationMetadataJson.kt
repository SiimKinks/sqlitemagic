package com.siimkinks.sqlitemagic.migration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object MigrationMetadataJson {
  private val json = Json { encodeDefaults = true }
  private val structureJson = Json { ignoreUnknownKeys = true }

  fun readSchema(text: String) = json
    .decodeFromString<ReleaseSchemaSnapshot>(text)
    .also(ReleaseSchemaSnapshot::validate)

  fun writeSchema(schema: ReleaseSchemaSnapshot): String {
    schema.validate()
    return json.encodeToString(schema)
  }

  fun readStructure(
    text: String,
    manifest: MigrationManifest,
    reference: ReleaseSchemaResource
  ): ReleaseSchemaSnapshot {
    val structure = structureJson.decodeFromString<Structure>(text)
    require(structure.views.values.all { it.name.isNotBlank() }) {
      "Structure contains an empty view name"
    }
    return ReleaseSchemaSnapshot(
      formatVersion = 1,
      databaseId = manifest.databaseId,
      releaseVariant = manifest.releaseVariant,
      version = reference.version,
      modules = reference.modules ?: manifest.modules,
      configuration = reference.configuration ?: MigrationConfiguration(
        foreignKeysEnabled = structure.tables.values.any { table ->
          table.columns.any(StructureColumn::onDeleteCascade)
        }
      ),
      tableSql = structure.tables.values.map(StructureTable::schema),
      views = emptyList(),
      indexSql = structure.indices.values.map(StructureIndex::indexSql)
    ).also { snapshot ->
      snapshot.validate()
      require(manifest.modules.containsAll(snapshot.modules)) {
        "Release structure ${reference.name} contains unknown modules: ${snapshot.modules}"
      }
    }
  }

  fun structureViewNames(text: String) = structureJson
    .decodeFromString<Structure>(text)
    .views.values.map(StructureView::name)

  fun readManifest(text: String) = json
    .decodeFromString<ManifestJson>(text)
    .toManifest()
    .also(MigrationManifest::validate)

  fun writeManifest(manifest: MigrationManifest): String {
    manifest.validate()
    return json.encodeToString(manifest.toJson())
  }
}

@Serializable
private data class Structure(
  val tables: Map<String, StructureTable> = emptyMap(),
  val indices: Map<String, StructureIndex> = emptyMap(),
  val views: Map<String, StructureView> = emptyMap()
)

@Serializable
private data class StructureTable(
  val schema: String,
  val columns: List<StructureColumn> = emptyList()
)

@Serializable
private data class StructureColumn(val onDeleteCascade: Boolean = false)

@Serializable
private data class StructureIndex(val indexSql: String)

@Serializable
private data class StructureView(val name: String)

@Serializable
private data class ManifestJson(
  val formatVersion: Int,
  val databaseId: String,
  val releaseVariant: String,
  val currentVersion: Int,
  val modules: List<String>,
  val schemas: List<ReleaseSchemaResource>,
  val steps: List<StepJson>
) {
  fun toManifest() = MigrationManifest(
    formatVersion = formatVersion,
    databaseId = databaseId,
    releaseVariant = releaseVariant,
    currentVersion = currentVersion,
    modules = modules,
    schemas = schemas,
    steps = steps.map(StepJson::toStep)
  )
}

@Serializable
private data class StepJson(
  val toVersion: Int,
  val sqlResources: List<ResourceJson>
) {
  fun toStep() = MigrationStep(
    toVersion = toVersion,
    sqlResources = sqlResources.map(ResourceJson::toResource)
  )
}

@Serializable
private data class ResourceJson(
  val name: String,
  val required: Boolean = true,
  val allowEmpty: Boolean = false
) {
  fun toResource() = MigrationResource(
    name = name,
    required = required,
    allowEmpty = allowEmpty
  )
}

private fun MigrationManifest.toJson() = ManifestJson(
  formatVersion = formatVersion,
  databaseId = databaseId,
  releaseVariant = releaseVariant,
  currentVersion = currentVersion,
  modules = modules,
  schemas = schemas,
  steps = steps.map(MigrationStep::toJson)
)

private fun MigrationStep.toJson() = StepJson(
  toVersion = toVersion,
  sqlResources = sqlResources.map(MigrationResource::toJson)
)

private fun MigrationResource.toJson() = ResourceJson(
  name = name,
  required = required,
  allowEmpty = allowEmpty
)
