package com.siimkinks.sqlitemagic.migration

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseSchemaResource(
  val version: Int,
  val name: String,
  val configuration: MigrationConfiguration? = null,
  val modules: List<String>? = null
)

data class MigrationManifest(
  val formatVersion: Int,
  val databaseId: String,
  val releaseVariant: String,
  val currentVersion: Int,
  val modules: List<String>,
  val schemas: List<ReleaseSchemaResource>,
  val steps: List<MigrationStep>
) {
  fun validate() {
    require(formatVersion == 1) {
      "Unsupported migration manifest format $formatVersion"
    }
    validateReleaseIdentity(
      databaseId = databaseId,
      releaseVariant = releaseVariant,
      modules = modules
    )
    require(currentVersion > 0) {
      "Invalid manifest current version $currentVersion"
    }
    val versions = schemas.map(ReleaseSchemaResource::version)
    require(versions.isNotEmpty() && versions.all { it in 1..currentVersion }) {
      "Manifest release schema versions must be positive and no later than $currentVersion"
    }
    require(versions == versions.distinct().sorted()) {
      "Manifest schema versions must be unique and ordered"
    }
    require(versions.last() == currentVersion) {
      "Manifest is missing current release schema $currentVersion"
    }
    val stepVersions = steps.map(MigrationStep::toVersion)
    require(stepVersions == stepVersions.distinct().sorted() && stepVersions.all { it in 1..currentVersion }) {
      "Manifest migration versions must be unique and ordered through $currentVersion"
    }
    val resources = steps.flatMap(MigrationStep::sqlResources)
    require(resources.all(MigrationResource::required)) {
      "Manifest-listed resources must be required"
    }
    val names = schemas.map(ReleaseSchemaResource::name) + resources.map(MigrationResource::name)
    names.forEach(::validateMigrationResourceName)
    require(names.distinct().size == names.size) {
      "Manifest lists a resource more than once"
    }
  }

  fun schema(version: Int) = schemas
    .singleOrNull { it.version == version }
    ?: error("No retained release schema for version $version")

  fun validateSchema(snapshot: ReleaseSchemaSnapshot) {
    schema(snapshot.version)
    require(
      snapshot.databaseId == databaseId && snapshot.releaseVariant == releaseVariant &&
          modules.containsAll(snapshot.modules)
    ) {
      "Release schema ${snapshot.version} belongs to a different database, release variant, or unknown module"
    }
  }

  /** Resolves retained host evidence for completeness checks; production uses conventional resource discovery. */
  fun resolve(
    fromVersion: Int,
    toVersion: Int
  ): MigrationPlan {
    val coveredSteps = steps.filter { it.toVersion in (fromVersion + 1)..toVersion }
    require(coveredSteps.size == toVersion - fromVersion && coveredSteps.withIndex().all { (index, step) ->
      step.toVersion == fromVersion + index + 1
    }) {
      "Manifest migration steps must cover every version in order for $fromVersion -> $toVersion"
    }
    return MigrationPlan(
      fromVersion = fromVersion,
      toVersion = toVersion,
      steps = coveredSteps
    )
  }
}

fun validateMigrationResourceName(name: String) {
  require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name) {
    "Invalid migration resource path '$name'"
  }
  require(name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
    "Invalid migration resource path '$name'"
  }
}
