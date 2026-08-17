package com.siimkinks.sqlitemagic.manager

import java.io.File

object ReleaseMigrationCoordinator {
  fun migrate(
    projectDir: File,
    databaseDirectory: File,
    variantName: String
  ) {
    val releaseAssetsDirectory = projectDir.resolve("src/$variantName/assets")
    val releaseStructuresDirectory = databaseDirectory.resolve("releases")
    val latestRelease = latestVersionedFile(
      directory = releaseStructuresDirectory,
      extension = "struct"
    )
    val previousVersion = latestRelease?.version
      ?: latestVersionedFile(
        directory = releaseAssetsDirectory,
        extension = "sql"
      )?.version
      ?: 0L
    val releaseVersion = previousVersion.inc()
    val currentStructure = readCurrentStructure(databaseDirectory)
    val previousStructure = latestRelease?.let { versionedFile ->
      readStructure(
        file = versionedFile.file,
        description = "latest release"
      )
    }

    MigrationsHandler(
      currentStructure = currentStructure,
      previousStructure = previousStructure,
      outputStructureFile = releaseStructuresDirectory.resolve("$releaseVersion.struct"),
      migrationOutputFile = releaseAssetsDirectory.resolve("$releaseVersion.sql")
    ).migrate()
  }

  private fun latestVersionedFile(
    directory: File,
    extension: String
  ): VersionedFile? {
    val seenVersions = linkedMapOf<Long, File>()
    var latest: VersionedFile? = null
    listDirectoryFiles(directory)
      .asSequence()
      .filter(File::isFile)
      .filter { it.extension == extension }
      .sortedBy(File::getName)
      .forEach { file ->
        val version = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
        val previousFile = seenVersions.putIfAbsent(version, file)
        if (previousFile != null) {
          error(
            "Duplicate numeric $extension version $version in ${directory.absolutePath}: " +
                "${previousFile.name}, ${file.name}"
          )
        }
        val versionedFile = VersionedFile(
          file = file,
          version = version
        )
        val currentLatest = latest
        latest = when {
          currentLatest == null -> versionedFile
          versionedFile.version > currentLatest.version -> versionedFile
          else -> currentLatest
        }
      }
    return latest
  }

  private fun readCurrentStructure(databaseDirectory: File): DatabaseStructure {
    val structureFiles = listDirectoryFiles(databaseDirectory)
      .filter { it.isFile && it.extension == "struct" }
      .sortedBy(File::getName)
    check(structureFiles.isNotEmpty()) {
      "No current database structure snapshots found in ${databaseDirectory.absolutePath}"
    }

    val tables = linkedMapOf<String, TableStructure>()
    val indices = linkedMapOf<String, IndexStructure>()
    val schemaObjectOwners = linkedMapOf<String, StructureOwner>()
    structureFiles.forEach { structureFile ->
      val structure = readStructure(
        file = structureFile,
        description = "current"
      )
      mergeStructureObjects(
        objects = structure.tables,
        destination = tables,
        structureFile = structureFile,
        objectKind = StructureObjectKind.TABLE,
        owners = schemaObjectOwners
      )
      mergeStructureObjects(
        objects = structure.indices,
        destination = indices,
        structureFile = structureFile,
        objectKind = StructureObjectKind.INDEX,
        owners = schemaObjectOwners
      )
    }
    return DatabaseStructure(
      tables = tables,
      indices = indices
    )
  }

  private fun <T> mergeStructureObjects(
    objects: Map<String, T>,
    destination: MutableMap<String, T>,
    structureFile: File,
    objectKind: StructureObjectKind,
    owners: MutableMap<String, StructureOwner>
  ) {
    objects.forEach { (name, value) ->
      val normalizedName = name.normalizedSqlIdentifier()
      val previousOwner = owners[normalizedName]
      if (previousOwner != null) {
        error(
          when {
            previousOwner.kind == objectKind -> "Duplicate ${objectKind.label} '$name' in current " +
                "database structure snapshots: ${previousOwner.name} (${previousOwner.file.name}) and " +
                "$name (${structureFile.name})"
            else -> "Duplicate SQLite schema identifier '$name' in current database structure snapshots: " +
                "${previousOwner.kind.label} '${previousOwner.name}' (${previousOwner.file.name}) and " +
                "${objectKind.label} '$name' (${structureFile.name})"
          }
        )
      }
      destination[name] = value
      owners[normalizedName] = StructureOwner(
        kind = objectKind,
        name = name,
        file = structureFile
      )
    }
  }

  private fun listDirectoryFiles(directory: File) = when {
    !directory.exists() -> emptyArray()
    !directory.isDirectory -> error("${directory.absolutePath} cannot be listed as a directory")
    else -> directory
      .listFiles()
      ?: error("${directory.absolutePath} cannot be listed as a directory")
  }

  private fun readStructure(
    file: File,
    description: String
  ): DatabaseStructure = try {
    DatabaseStructureJson.read(file.readText())
  } catch (exception: Exception) {
    throw IllegalStateException("Malformed $description database structure snapshot ${file.absolutePath}", exception)
  }
}

private data class VersionedFile(
  val file: File,
  val version: Long
)

private data class StructureOwner(
  val kind: StructureObjectKind,
  val name: String,
  val file: File
)

private enum class StructureObjectKind(
  val label: String
) {
  TABLE("table"),
  INDEX("index")
}
