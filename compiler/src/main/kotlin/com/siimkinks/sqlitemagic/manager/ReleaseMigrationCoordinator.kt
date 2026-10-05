package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import java.io.File

object ReleaseMigrationCoordinator {
  fun migrate(
    projectDir: File,
    databaseDirectory: File,
    variantName: String,
    currentStructureFiles: List<File>? = null,
    compiledRelease: ReleaseSchemaSnapshot? = null,
    evidenceAssetsDirectory: File = projectDir.resolve("src/$variantName/assets")
  ) {
    val releaseAssetsDirectory = projectDir.resolve("src/$variantName/assets")
    val releaseStructuresDirectory = databaseDirectory.resolve("releases")
    val latestRelease = latestVersionedFile(
      directory = releaseStructuresDirectory,
      extension = "struct"
    )
    val previousVersion = latestRelease
      ?.version
      ?: latestVersionedFile(
        directory = evidenceAssetsDirectory,
        extension = "sql"
      )?.version
      ?: 0L
    val evidencePublisher = compiledRelease?.let { schema ->
      ReleaseEvidencePublisher(
        releaseDirectory = releaseStructuresDirectory,
        assetsDirectory = evidenceAssetsDirectory,
        compiledRelease = schema
      ).also(ReleaseEvidencePublisher::validatePublication)
    }
    if (evidencePublisher?.adoptsExistingStructure == true) {
      val transaction = FileSnapshotTransaction()
      try {
        evidencePublisher.publish(transaction)
      } catch (failure: Exception) {
        transaction.restore(failure)
        throw failure
      }
      return
    }
    val releaseVersion = compiledRelease?.version?.toLong() ?: previousVersion.inc()
    val currentStructures = readCurrentStructures(
      databaseDirectory = databaseDirectory,
      currentStructureFiles = currentStructureFiles
    )
    validateCurrentStructures(currentStructures)
    val currentStructure = aggregatePersistentStructures(currentStructures)
    compiledRelease?.let { compiled ->
      check(
        currentStructure.tables.values.map(TableStructure::schema).toSet() == compiled.tableSql.toSet() &&
            currentStructure.indices.values.map(IndexStructure::indexSql).toSet() == compiled.indexSql.toSet() &&
            currentStructure.views.keys == compiled.views.map(MigrationViewSchema::name).toSet()
      ) { "Compiled selected release differs from current compiler structures" }
    }
    val previousStructure = latestRelease?.let { versionedFile ->
      readStructure(
        file = versionedFile.file,
        description = "latest release"
      ).persistentOnly()
    }

    val mainSqlFile = releaseAssetsDirectory.resolve("$releaseVersion.sql")
    val effectiveSqlFile = evidenceAssetsDirectory.resolve("$releaseVersion.sql")
    val pendingSqlText = when {
      effectiveSqlFile.isFile -> readPendingReleaseResource(effectiveSqlFile)
        .also { text ->
          MigrationScriptValidation.validate(
            text = text,
            resourceName = effectiveSqlFile.absolutePath
          )
        }
      else -> null
    }

    val generatesSql = previousStructure?.let {
      SchemaDiffer.diff(
        from = it,
        to = currentStructure
      ).hasPersistentTableOrIndexChanges
    } == true
    val destinationPendingSql = when {
      generatesSql -> pendingSqlText
      mainSqlFile.isFile -> readPendingReleaseResource(mainSqlFile)
      else -> null
    }
    val transaction = FileSnapshotTransaction()
    try {
      MigrationsHandler(
        currentStructure = currentStructure,
        previousStructure = previousStructure,
        outputStructureFile = releaseStructuresDirectory.resolve("$releaseVersion.struct"),
        migrationOutputFile = mainSqlFile,
        persistentStructureOnly = true,
        pendingMigrationStatements = destinationPendingSql
          ?.reader()
          ?.readLines()
          .orEmpty(),
        externalTransaction = transaction
      ).migrate()
      if (destinationPendingSql != null && !mainSqlFile.isFile) transaction.writeTextIfChanged(
        file = mainSqlFile,
        text = destinationPendingSql
      )
      evidencePublisher?.publish(
        transaction = transaction,
        generatedCurrentSql = mainSqlFile.takeIf { generatesSql && it.isFile }
      )
    } catch (failure: Exception) {
      transaction.restore(failure)
      throw failure
    }
  }

  private fun readPendingReleaseResource(file: File): String = try {
    MigrationScriptValidation.readUtf8(file.inputStream())
  } catch (failure: Exception) {
    throw IllegalStateException("Cannot read pending release resource ${file.absolutePath} as UTF-8", failure)
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
