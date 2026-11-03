package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.migration.MigrationScriptValidation
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import java.io.File

object ReleaseMigrationCoordinator {
  fun migrate(
    projectDir: File,
    databaseDirectory: File,
    variantName: String,
    databaseVersion: Int,
    currentStructureFiles: List<File>? = null,
    compiledRelease: ReleaseSchemaSnapshot? = null,
    evidenceAssetsDirectory: File = projectDir.resolve("src/$variantName/assets"),
    warn: (String) -> Unit = {}
  ) {
    val releaseStructuresDirectory = databaseDirectory.resolve("releases")
    val evidencePublisher = evidencePublisher(
      databaseDirectory = databaseDirectory,
      evidenceAssetsDirectory = evidenceAssetsDirectory,
      compiledRelease = compiledRelease
    )
    evidencePublisher?.validatePublication()
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
    val release = prepareRelease(
      projectDir = projectDir,
      databaseDirectory = databaseDirectory,
      variantName = variantName,
      databaseVersion = databaseVersion,
      currentStructureFiles = currentStructureFiles,
      evidenceAssetsDirectory = evidenceAssetsDirectory
    )
    check(release.version == databaseVersion) {
      "Database version $databaseVersion is not above the latest release ${release.previousVersion} in " +
          "${releaseStructuresDirectory.absolutePath}. Set @Database(version) to ${release.version} or higher " +
          "to publish the next release."
    }
    compiledRelease?.let(release::checkCompiledRelease)
    val transaction = FileSnapshotTransaction()
    try {
      release.publish(transaction)
      evidencePublisher?.publish(
        transaction = transaction,
        generatedCurrentSql = release.sqlFile.takeIf { release.generatesSql }
      )
    } catch (failure: Exception) {
      transaction.restore(failure)
      throw failure
    }
    release.warnings.forEach(warn)
  }

  fun preview(
    projectDir: File,
    databaseDirectory: File,
    variantName: String,
    databaseVersion: Int,
    currentStructureFiles: List<File>? = null,
    compiledRelease: ReleaseSchemaSnapshot? = null,
    evidenceAssetsDirectory: File = projectDir.resolve("src/$variantName/assets")
  ): ReleaseMigrationPreview {
    val evidencePublisher = evidencePublisher(
      databaseDirectory = databaseDirectory,
      evidenceAssetsDirectory = evidenceAssetsDirectory,
      compiledRelease = compiledRelease
    )
    evidencePublisher?.validatePreview()
    if (evidencePublisher?.adoptsExistingStructure == true) {
      return ReleaseMigrationPreview(
        version = databaseVersion,
        report = "Release $databaseVersion preview (adopts retained release $databaseVersion)\n\n" +
            "Publishing adopts the retained release history and generates no migration.",
        sql = null
      )
    }
    val release = prepareRelease(
      projectDir = projectDir,
      databaseDirectory = databaseDirectory,
      variantName = variantName,
      databaseVersion = databaseVersion,
      currentStructureFiles = currentStructureFiles,
      evidenceAssetsDirectory = evidenceAssetsDirectory
    )
    compiledRelease?.let(release::checkCompiledRelease)
    return ReleaseMigrationPreview(
      version = release.version,
      report = release.previewReport(
        projectDir = projectDir,
        databaseVersion = databaseVersion
      ),
      sql = release.sql
    )
  }

  private fun evidencePublisher(
    databaseDirectory: File,
    evidenceAssetsDirectory: File,
    compiledRelease: ReleaseSchemaSnapshot?
  ) = compiledRelease?.let { schema ->
    ReleaseEvidencePublisher(
      releaseDirectory = databaseDirectory.resolve("releases"),
      assetsDirectory = evidenceAssetsDirectory,
      compiledRelease = schema
    )
  }

  private fun prepareRelease(
    projectDir: File,
    databaseDirectory: File,
    variantName: String,
    databaseVersion: Int,
    currentStructureFiles: List<File>?,
    evidenceAssetsDirectory: File
  ): PreparedRelease {
    val releaseStructuresDirectory = databaseDirectory.resolve("releases")
    val latestRelease = latestRelease(releaseStructuresDirectory)
    val version = when {
      latestRelease == null || databaseVersion > latestRelease.version -> databaseVersion
      else -> latestRelease.version + 1
    }
    val currentStructure = readCurrentStructure(
      databaseDirectory = databaseDirectory,
      currentStructureFiles = currentStructureFiles
    )
    val migration = latestRelease?.let { release ->
      planSchemaMigration(
        previous = readStructure(
          file = release.file,
          description = "latest release"
        ).persistentOnly(),
        current = currentStructure
      )
    }
    val sqlFile = projectDir.resolve("src/$variantName/assets/$version.sql")
    val effectiveSqlFile = evidenceAssetsDirectory.resolve("$version.sql")
    val effectiveSql = effectiveSqlFile
      .takeIf(File::isFile)
      ?.let(::readPendingReleaseResource)
      ?.also { text ->
        MigrationScriptValidation.validate(
          text = text,
          resourceName = effectiveSqlFile.absolutePath
        )
      }
    val pendingSql = when {
      migration?.plan != null || sqlFile == effectiveSqlFile -> effectiveSql
      else -> sqlFile
        .takeIf(File::isFile)
        ?.let(::readPendingReleaseResource)
    }
    val sqlLines = pendingSql
      ?.reader()
      ?.readLines()
      .orEmpty() + migration?.statements.orEmpty()
    return PreparedRelease(
      version = version,
      previousVersion = latestRelease?.version,
      migration = migration,
      structureFile = releaseStructuresDirectory.resolve("$version.struct"),
      structure = currentStructure.persistentOnly(),
      sqlFile = sqlFile,
      sql = sqlLines
        .takeIf(List<String>::isNotEmpty)
        ?.let(::migrationScript)
    )
  }

  private fun readPendingReleaseResource(file: File): String = try {
    MigrationScriptValidation.readUtf8(file.inputStream())
  } catch (failure: Exception) {
    throw IllegalStateException("Cannot read pending release resource ${file.absolutePath} as UTF-8", failure)
  }

  private fun latestRelease(directory: File): VersionedFile? {
    val versionedFiles = listDirectoryFiles(directory)
      .asSequence()
      .filter(File::isFile)
      .filter { it.extension == "struct" }
      .sortedBy(File::getName)
      .mapNotNull { file ->
        file.nameWithoutExtension
          .toIntOrNull()
          ?.let { version ->
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
          "Duplicate numeric struct version $version in ${directory.absolutePath}: " +
              files.joinToString { it.file.name }
        }
      }
    return versionedFiles.maxByOrNull(VersionedFile::version)
  }

  private fun readCurrentStructure(
    databaseDirectory: File,
    currentStructureFiles: List<File>?
  ): DatabaseStructure {
    val structures = readCurrentStructures(
      databaseDirectory = databaseDirectory,
      currentStructureFiles = currentStructureFiles
    )
    validateCurrentStructures(structures)
    return aggregateStructures(structures)
  }

  private fun validateCurrentStructures(structures: List<Pair<String, DatabaseStructure>>) {
    val conflicts = findSchemaIdentityConflicts(structures)
    check(conflicts.isEmpty()) {
      conflicts.joinToString(separator = "\n") { conflict ->
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
    }
  }

  private fun aggregateStructures(
    structures: Iterable<Pair<String, DatabaseStructure>>
  ): DatabaseStructure {
    val tables = linkedMapOf<String, TableStructure>()
    val indices = linkedMapOf<String, IndexStructure>()
    val temporaryTables = linkedMapOf<String, TableStructure>()
    val temporaryIndices = linkedMapOf<String, IndexStructure>()
    val views = linkedMapOf<String, ViewStructure>()
    val temporaryViews = linkedMapOf<String, ViewStructure>()
    structures.forEach { (_, structure) ->
      tables.putAll(structure.tables)
      indices.putAll(structure.indices)
      temporaryTables.putAll(structure.temporaryTables)
      temporaryIndices.putAll(structure.temporaryIndices)
      views.putAll(structure.views)
      temporaryViews.putAll(structure.temporaryViews)
    }
    return DatabaseStructure(
      tables = tables,
      indices = indices,
      temporaryTables = temporaryTables,
      temporaryIndices = temporaryIndices,
      views = views,
      temporaryViews = temporaryViews
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
  val version: Int
)

private fun PreparedRelease.checkCompiledRelease(compiled: ReleaseSchemaSnapshot) = check(
  structure.hasSchemaOf(compiled)
) { "Compiled selected release differs from current compiler structures" }
