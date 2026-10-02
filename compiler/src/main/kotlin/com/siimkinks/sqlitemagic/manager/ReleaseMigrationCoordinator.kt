package com.siimkinks.sqlitemagic.manager

import java.io.File

object ReleaseMigrationCoordinator {
  fun migrate(
    projectDir: File,
    databaseDirectory: File,
    variantName: String,
    currentStructureFiles: List<File>? = null
  ) {
    val releaseAssetsDirectory = projectDir.resolve("src/$variantName/assets")
    val releaseStructuresDirectory = databaseDirectory.resolve("releases")
    val latestRelease = latestVersionedFile(
      directory = releaseStructuresDirectory,
      extension = "struct"
    )
    val previousVersion = latestRelease
      ?.version
      ?: listOfNotNull(
        latestVersionedFile(
          directory = releaseAssetsDirectory,
          extension = "sql"
        )?.version,
        latestVersionedFile(
          directory = releaseAssetsDirectory,
          extension = "views"
        )?.version
      ).maxOrNull()
      ?: 0L
    val releaseVersion = previousVersion.inc()
    val currentStructures = readCurrentStructures(
      databaseDirectory = databaseDirectory,
      currentStructureFiles = currentStructureFiles
    )
    validateCurrentStructures(currentStructures)
    val currentStructure = aggregatePersistentStructures(currentStructures)
    val previousStructure = latestRelease?.let { versionedFile ->
      readStructure(
        file = versionedFile.file,
        description = "latest release"
      ).persistentOnly()
    }

    MigrationsHandler(
      currentStructure = currentStructure,
      previousStructure = previousStructure,
      outputStructureFile = releaseStructuresDirectory.resolve("$releaseVersion.struct"),
      migrationOutputFile = releaseAssetsDirectory.resolve("$releaseVersion.sql"),
      persistentStructureOnly = true
    ).migrate()
  }

  private fun latestVersionedFile(
    directory: File,
    extension: String
  ): VersionedFile? {
    val versionedFiles = listDirectoryFiles(directory)
      .asSequence()
      .filter(File::isFile)
      .filter { it.extension == extension }
      .sortedBy(File::getName)
      .mapNotNull { file ->
        file.nameWithoutExtension.toLongOrNull()?.let { version ->
          VersionedFile(
            file = file,
            version = version
          )
        }
      }
      .toList()
    versionedFiles
      .groupBy(VersionedFile::version)
      .forEach { (version, files) ->
        check(files.size <= 1) {
          "Duplicate numeric $extension version $version in ${directory.absolutePath}: " +
            files.joinToString { it.file.name }
        }
      }
    return versionedFiles.maxByOrNull(VersionedFile::version)
  }

  private fun validateCurrentStructures(structures: List<Pair<String, DatabaseStructure>>) {
    val conflicts = findSchemaIdentityConflicts(structures)
    if (conflicts.isNotEmpty()) {
      throw IllegalStateException(
        conflicts.joinToString(
          separator = "\n",
          transform = { conflict ->
            conflict.run {
              when {
                previousOwner.objectKind == objectKind -> "Duplicate ${objectKind.label} '$name' in current " +
                    "database structure snapshots: ${previousOwner.name} (${previousOwner.source}) and " +
                    "$name ($source)"
                else -> "Duplicate SQLite schema identifier '$name' in current database structure snapshots: " +
                    "${previousOwner.objectKind.label} '${previousOwner.name}' (${previousOwner.source}) and " +
                    "${objectKind.label} '$name' ($source)"
              }
            }
          }
        )
      )
    }
  }

  private fun aggregatePersistentStructures(
    structures: Iterable<Pair<String, DatabaseStructure>>
  ): DatabaseStructure {
    val tables = linkedMapOf<String, TableStructure>()
    val indices = linkedMapOf<String, IndexStructure>()
    val views = linkedMapOf<String, ViewStructure>()
    structures.forEach { (_, structure) ->
      tables.putAll(structure.tables)
      indices.putAll(structure.indices)
      views.putAll(structure.views)
    }
    return DatabaseStructure(
      tables = tables,
      indices = indices,
      views = views
    )
  }

  private fun readCurrentStructures(
    databaseDirectory: File,
    currentStructureFiles: List<File>?
  ): List<Pair<String, DatabaseStructure>> {
    val structureFiles = when (currentStructureFiles) {
      null -> listDirectoryFiles(databaseDirectory)
        .filter { it.isFile && it.extension == "struct" }
      else -> currentStructureFiles
    }.sortedWith(
      compareBy(
        File::getName,
        File::getAbsolutePath
      )
    )
    check(structureFiles.isNotEmpty()) {
      "No current database structure snapshots found in ${databaseDirectory.absolutePath}"
    }
    structureFiles.forEach { file ->
      check(file.isFile && file.extension == "struct") {
        "Current database structure snapshot is not a .struct file: ${file.absolutePath}"
      }
    }

    return structureFiles.map { structureFile ->
      structureFile.name to readStructure(
        file = structureFile,
        description = "current"
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
