package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.KSPLogger
import com.siimkinks.sqlitemagic.CompilerOptions
import com.siimkinks.sqlitemagic.manager.DebugMigrationOutcome.Companion.NO_DATABASE_VERSION_OVERRIDE
import java.io.File
import java.util.Locale

private const val INITIAL_DEBUG_DATABASE_VERSION = 1000
private const val DEBUG_MIGRATION_HISTORY_LIMIT = 10

internal data class DebugMigrationConfiguration(
  val enabled: Boolean,
  val projectDir: String?,
  val variantName: String?,
  val mainModulePath: String?,
  val structureOutputDirectory: String?
) {
  companion object {
    fun from(options: CompilerOptions) = DebugMigrationConfiguration(
      enabled = options.isDebugVariant && options.migrateDebug,
      projectDir = options.projectDir,
      variantName = options.variantName,
      mainModulePath = options.mainModulePath,
      structureOutputDirectory = options.structureOutputDirectory
    )
  }
}

internal data class DebugMigrationOutcome(
  val databaseVersionOverride: Int? = null
) {
  companion object {
    val NO_DATABASE_VERSION_OVERRIDE = DebugMigrationOutcome()
  }
}

private data class DebugMigrationPublication(
  val outcome: DebugMigrationOutcome,
  val nextDatabaseVersion: Int,
  val latestMigrationVersion: Int,
  val changedMarkers: List<File>
)

internal class DebugMigrationCoordinator(
  private val configuration: DebugMigrationConfiguration,
  private val logger: KSPLogger
) {
  fun handle(
    database: GeneratedDatabaseElement,
    currentStructure: DatabaseStructure,
    completion: (DebugMigrationOutcome) -> Unit = {}
  ): DebugMigrationOutcome {
    if (!configuration.enabled) {
      val transaction = FileSnapshotTransaction()
      try {
        when {
          database.isSubmodule -> submoduleStructureDirectory()
            ?.let { structureDirectory ->
              persistSubmoduleState(
                structureDirectory = structureDirectory,
                submoduleName = checkNotNull(database.submoduleName),
                structure = currentStructure,
                migrationHappened = false,
                replaceExistingStructures = configuration.structureOutputDirectory != null,
                snapshots = transaction
              )
            }
          configuration.structureOutputDirectory != null -> transaction.writeTextIfChanged(
            file = File(configuration.structureOutputDirectory, "latest.struct"),
            text = DatabaseStructureJson.write(currentStructure)
          )
        }
        completion(NO_DATABASE_VERSION_OVERRIDE)
      } catch (exception: Exception) {
        transaction.restore(exception)
        throw exception
      }
      return NO_DATABASE_VERSION_OVERRIDE
    }

    val projectDir = configuration.projectDir
    val variantName = configuration.variantName
    if (projectDir == null || variantName == null) {
      logger.warn("Skipping automatic debug migrations: project directory and variant name are required")
      completion(NO_DATABASE_VERSION_OVERRIDE)
      return NO_DATABASE_VERSION_OVERRIDE
    }

    val transaction = FileSnapshotTransaction()
    val publication = try {
      val latestDatabaseVersion = readLatestDebugVersion(
        projectDir = projectDir,
        mainModulePath = configuration.mainModulePath,
        variantName = variantName,
        isSubmodule = database.isSubmodule,
        transaction = transaction
      )
      val nextDatabaseVersion = latestDatabaseVersion + 1
      val structureFile = File(projectDir, "db/latest.struct")
      val migrationFileName = when (val submoduleName = database.submoduleName) {
        null -> "$nextDatabaseVersion.sql"
        else -> "$submoduleName$nextDatabaseVersion.sql"
      }
      val migrationFile = File(projectDir, "src/$variantName/assets/$migrationFileName")
      val versionFile = File(projectDir, "db/latest_$variantName.version")
      val changedMarkers = when {
        database.isSubmodule -> emptyList()
        else -> submoduleChangeMarkers(projectDir)
      }
      val pendingMigrationStatements = when {
        database.isSubmodule -> transaction
          .snapshot(migrationFile)
          .readLines()
        else -> emptyList()
      }
      val migrationHappened = MigrationsHandler(
        currentStructure = currentStructure,
        previousStructure = transaction
          .snapshot(structureFile)
          .text
          ?.let {
            runCatching {
              DatabaseStructureJson.read(it)
            }.getOrNull()
          },
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile,
        pendingMigrationStatements = pendingMigrationStatements,
        externalTransaction = transaction
      ).migrate()
      val outcome = when (val submoduleName = database.submoduleName) {
        null -> when {
          migrationHappened || changedMarkers.isNotEmpty() -> {
            writeMainModuleDebugVersion(
              file = versionFile,
              version = nextDatabaseVersion,
              transaction = transaction
            )
            DebugMigrationOutcome(databaseVersionOverride = nextDatabaseVersion)
          }
          else -> DebugMigrationOutcome(databaseVersionOverride = latestDatabaseVersion)
        }
        else -> {
          submoduleStructureDirectory()?.let { structureDirectory ->
            persistSubmoduleState(
              structureDirectory = structureDirectory,
              submoduleName = submoduleName,
              structure = currentStructure,
              migrationHappened = migrationHappened && configuration.structureOutputDirectory == null,
              replaceExistingStructures = configuration.structureOutputDirectory != null,
              snapshots = transaction
            )
          }
          NO_DATABASE_VERSION_OVERRIDE
        }
      }
      DebugMigrationPublication(
        outcome = outcome,
        nextDatabaseVersion = nextDatabaseVersion,
        latestMigrationVersion = outcome.databaseVersionOverride ?: when {
          migrationHappened || pendingMigrationStatements.isNotEmpty() -> nextDatabaseVersion
          else -> latestDatabaseVersion
        },
        changedMarkers = changedMarkers
      )
    } catch (exception: Exception) {
      transaction.restore(exception)
      logger.warn("Failed to automatically migrate database: ${exception.message}")
      completion(NO_DATABASE_VERSION_OVERRIDE)
      return NO_DATABASE_VERSION_OVERRIDE
    }
    try {
      completion(publication.outcome)
      if (!database.isSubmodule && publication.outcome.databaseVersionOverride == publication.nextDatabaseVersion) {
        publication.changedMarkers.forEach { marker ->
          transaction.track(marker)
          check(marker.delete()) {
            "Failed to remove consumed SqliteMagic submodule change marker ${marker.absolutePath}"
          }
        }
      }
      pruneDebugMigrationHistory(
        assetsDirectory = File(projectDir, "src/$variantName/assets"),
        submoduleName = database.submoduleName,
        latestVersion = publication.latestMigrationVersion,
        transaction = transaction
      )
    } catch (exception: Exception) {
      transaction.restore(exception)
      throw exception
    }
    return publication.outcome
  }

  private fun submoduleStructureDirectory() = configuration
    .structureOutputDirectory
    ?.let(::File)
    ?: configuration.mainModulePath?.let { File(it, "db") }
}

private fun readLatestDebugVersion(
  projectDir: String,
  mainModulePath: String?,
  variantName: String,
  isSubmodule: Boolean,
  transaction: FileSnapshotTransaction
): Int {
  val readFile = File(mainModulePath ?: projectDir, "db/latest_$variantName.version")
  val writeFile = File(projectDir, "db/latest_$variantName.version")
  val versionLines = when {
    !isSubmodule && readFile == writeFile -> transaction
      .snapshot(readFile)
      .takeIf(FileSnapshotTransaction.Snapshot::isRegularFile)
      ?.readLines()
    else -> readFile
      .takeIf(File::isFile)
      ?.readLines()
  }
  return versionLines
    ?.last()
    ?.toInt()
    ?: INITIAL_DEBUG_DATABASE_VERSION
}

private fun pruneDebugMigrationHistory(
  assetsDirectory: File,
  submoduleName: String?,
  latestVersion: Int,
  transaction: FileSnapshotTransaction
) {
  val prefix = submoduleName.orEmpty()
  val lastExpiredVersion = latestVersion - DEBUG_MIGRATION_HISTORY_LIMIT
  assetsDirectory
    .listFiles()
    .orEmpty()
    .filter { file ->
      val version = file.nameWithoutExtension
        .removePrefix(prefix)
        .toIntOrNull()
      version != null &&
          version > INITIAL_DEBUG_DATABASE_VERSION &&
          version <= lastExpiredVersion &&
          file.isFile && file.name == "$prefix$version.sql"
    }
    .forEach { file ->
      transaction.track(file)
      check(file.delete()) {
        "Failed to remove old SqliteMagic debug migration ${file.absolutePath}"
      }
    }
}

private fun writeMainModuleDebugVersion(
  file: File,
  version: Int,
  transaction: FileSnapshotTransaction
) = transaction.writeTextIfChanged(
  file = file,
  text = version.toString()
)

private fun persistSubmoduleState(
  structureDirectory: File,
  submoduleName: String,
  structure: DatabaseStructure,
  migrationHappened: Boolean,
  replaceExistingStructures: Boolean,
  snapshots: FileSnapshotTransaction
) {
  val normalizedName = submoduleName.lowercase(Locale.ROOT)
  val structureFile = structureDirectory.resolve("latest_$normalizedName.struct")
  val changeMarker = structureDirectory.resolve("$normalizedName.changed")
  val staleFiles = when {
    replaceExistingStructures -> structureDirectory
      .listFiles()
      .orEmpty()
      .filter { file ->
        file.isFile &&
            file != structureFile &&
            (file.name.startsWith("latest_") && file.name.endsWith(".struct") || file.extension == "changed")
      }
    else -> emptyList()
  }
  if (replaceExistingStructures) {
    staleFiles.forEach { file ->
      snapshots.track(file)
      check(file.delete()) {
        "Failed to remove stale staged SqliteMagic state ${file.absolutePath}"
      }
    }
  }
  snapshots.writeTextIfChanged(
    file = structureFile,
    text = DatabaseStructureJson.write(structure)
  )
  if (migrationHappened) {
    snapshots.writeTextIfChanged(
      file = changeMarker,
      text = ""
    )
  }
}

private fun submoduleChangeMarkers(projectDir: String) = File(projectDir, "db")
  .listFiles { it.isFile && it.extension == "changed" }
  .orEmpty()
  .sortedBy(File::getName)
