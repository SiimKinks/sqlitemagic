package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import java.io.File

internal class ReleaseEvidencePublisher(
  private val releaseDirectory: File,
  private val assetsDirectory: File,
  private val compiledRelease: ReleaseSchemaSnapshot
) {
  private val manifestFile = releaseDirectory.resolve("manifest.json")
  private val schemaFile = releaseDirectory.resolve("${compiledRelease.version}.struct")
  private val previousManifest = manifestFile
    .takeIf(File::isFile)
    ?.readText()
    ?.let(MigrationMetadataJson::readManifest)
  private val retainedSchemas = retainedStructureFiles()
  private val moduleRegistry = (
      previousManifest?.modules.orEmpty() + previousManifest?.schemas.orEmpty()
        .mapNotNull(ReleaseSchemaResource::modules)
        .flatten() + compiledRelease.modules
      )
    .filterNot { it == compiledRelease.databaseId }
    .distinct() + compiledRelease.databaseId

  private val latestRetainedVersion = retainedSchemas.maxOfOrNull(VersionedFile::version)

  val adoptsExistingStructure = previousManifest == null && latestRetainedVersion == compiledRelease.version

  fun validatePublication() {
    previousManifest?.let { manifest ->
      check(
        manifest.databaseId == compiledRelease.databaseId &&
            manifest.releaseVariant == compiledRelease.releaseVariant
      ) {
        "Release publication cannot mix database identities or selected release variants"
      }
      check(latestRetainedVersion == manifest.currentVersion) {
        "Release compiler baseline ${manifest.currentVersion}.struct is missing or mismatched. " +
            "Restore the published compiler baseline before creating another release migration."
      }
    }
    check(adoptsExistingStructure || latestRetainedVersion == null || compiledRelease.version > latestRetainedVersion) {
      "Release version ${compiledRelease.version} is already published or precedes " +
          "published version $latestRetainedVersion. " +
          "Choose a new database version; published release evidence is immutable."
    }
    check(adoptsExistingStructure || !schemaFile.exists()) {
      "Release evidence already exists: ${schemaFile.absolutePath}"
    }
    if (adoptsExistingStructure) {
      val retained = DatabaseStructureJson.read(schemaFile.readText())
      check(
        retained.tables.values.map(TableStructure::schema).toSet() == compiledRelease.tableSql.toSet() &&
            retained.indices.values.map(IndexStructure::indexSql).toSet() == compiledRelease.indexSql.toSet() &&
            retained.views.keys == compiledRelease.views.map(MigrationViewSchema::name).toSet()
      ) { "Compiled selected release differs from the retained current .struct; adoption cannot rewrite history" }
    }
    check(!manifestFile.exists() || manifestFile.isFile) {
      "Release manifest is not a regular file: $manifestFile"
    }
    validateMigrationAssetNames()
  }

  fun publish(
    transaction: FileSnapshotTransaction,
    generatedCurrentSql: File? = null
  ) {
    val firstStep = previousManifest?.currentVersion?.inc() ?: 1
    val newSteps = (firstStep..compiledRelease.version).map { version ->
      migrationStep(
        version = version,
        generatedCurrentSql = generatedCurrentSql
      )
    }
    val discoveredSchemas = retainedSchemas.associate { retained ->
      retained.version to ReleaseSchemaResource(
        version = retained.version,
        name = "schemas/${retained.file.name}"
      )
    }
    val previousReferences = previousManifest?.schemas
      .orEmpty()
      .associateBy(ReleaseSchemaResource::version)
    val currentReference = ReleaseSchemaResource(
      version = compiledRelease.version,
      name = "schemas/${schemaFile.name}",
      configuration = compiledRelease.configuration,
      modules = compiledRelease.modules
    )
    val schemaFiles = discoveredSchemas + previousReferences + mapOf(compiledRelease.version to currentReference)
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = compiledRelease.databaseId,
      releaseVariant = compiledRelease.releaseVariant,
      currentVersion = compiledRelease.version,
      modules = moduleRegistry,
      schemas = schemaFiles
        .toSortedMap()
        .values.toList(),
      steps = previousManifest?.steps.orEmpty() + newSteps
    )
    transaction.writeTextIfChanged(
      file = manifestFile,
      text = MigrationMetadataJson.writeManifest(manifest)
    )
  }

  private fun migrationStep(
    version: Int,
    generatedCurrentSql: File?
  ) = MigrationStep(
    toVersion = version,
    sqlResources = sqlResources(
      version = version,
      modules = compiledRelease.modules,
      generatedCurrentSql = generatedCurrentSql
    )
  )

  private fun sqlResources(
    version: Int,
    modules: List<String>,
    generatedCurrentSql: File?
  ) = modules.mapNotNull { module ->
    val prefix = when (module) {
      compiledRelease.databaseId -> ""
      else -> module
    }
    val script = when {
      module == compiledRelease.databaseId &&
          version == compiledRelease.version &&
          generatedCurrentSql != null -> generatedCurrentSql
      else -> assetsDirectory.resolve("$prefix$version.sql")
    }
    script.takeIf(File::isFile)
      ?.let { file ->
        val text = MigrationScriptValidation.readUtf8(file.inputStream())
        MigrationScriptValidation.validate(
          text = text,
          resourceName = file.absolutePath
        )
        MigrationResource(
          name = file.name,
          required = true,
          allowEmpty = text.lineSequence().all {
            it.isBlank() || it.trimStart().startsWith("--")
          }
        )
      }
  }

  private fun validateMigrationAssetNames() {
    val assets = files(assetsDirectory).filter { it.isFile && it.extension == "sql" }
    moduleRegistry.forEach { module ->
      val prefix = when (module) {
        compiledRelease.databaseId -> ""
        else -> module
      }
      assets.filter { it.nameWithoutExtension.startsWith(prefix) }
        .mapNotNull { file ->
          file.nameWithoutExtension
            .removePrefix(prefix)
            .toIntOrNull()
            ?.let { version ->
              VersionedFile(
                version = version,
                file = file
              )
            }
        }
        .groupBy(VersionedFile::version)
        .forEach { (version, files) ->
          check(files.size <= 1) { "Duplicate numeric sql version $version for module '$module'" }
        }
    }
    assets.forEach { file ->
      val versions = moduleRegistry.mapNotNull { module ->
        val prefix = when (module) {
          compiledRelease.databaseId -> ""
          else -> module
        }
        when {
          file.nameWithoutExtension.startsWith(prefix) -> file.nameWithoutExtension
            .removePrefix(prefix)
            .toIntOrNull()
          else -> null
        }
      }
      check(versions.size <= 1) {
        "Ambiguous conventional migration resource ${file.name}; review module identities"
      }
      check(versions.isNotEmpty() || file.nameWithoutExtension.lastOrNull()?.isDigit() != true) {
        "Unknown module prefix in conventional migration resource ${file.name}. " +
            "Retain trusted module evidence or select a history with known ordered modules before publication."
      }
      versions.singleOrNull()?.also { version ->
        check(version > 0) { "Migration asset version must be positive: ${file.name}" }
        val matchesCanonicalName = moduleRegistry.any { module ->
          val prefix = when (module) {
            compiledRelease.databaseId -> ""
            else -> module
          }
          file.name == "$prefix$version.sql"
        }
        check(matchesCanonicalName) {
          "Noncanonical conventional migration resource ${file.name}; use the exact numeric version filename"
        }
      }
    }
  }

  private fun retainedStructureFiles(): List<VersionedFile> {
    val matches = files(releaseDirectory)
      .filter { it.isFile && it.name.endsWith(".struct") }
      .mapNotNull { file ->
        file.name.removeSuffix(".struct")
          .toIntOrNull()
          ?.let { version ->
            VersionedFile(
              version = version,
              file = file
            )
          }
      }
      .sortedBy(VersionedFile::version)
    check(
      matches
        .map(VersionedFile::version)
        .distinct()
        .size == matches.size
    ) {
      "Duplicate numeric struct release versions in $releaseDirectory"
    }
    check(matches.all { it.version > 0 }) {
      "Release versions must be positive in $releaseDirectory"
    }
    return matches
  }

  private data class VersionedFile(
    val version: Int,
    val file: File
  )

  private fun files(directory: File) = when {
    !directory.exists() -> emptyList()
    !directory.isDirectory -> error("${directory.absolutePath} is not a directory")
    else -> checkNotNull(directory.listFiles()) { "Cannot list ${directory.absolutePath}" }.toList()
  }
}
