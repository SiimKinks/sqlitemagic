package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.KSPLogger
import com.siimkinks.sqlitemagic.CompilerOptions
import com.siimkinks.sqlitemagic.manager.DebugMigrationOutcome.Companion.NO_DATABASE_VERSION_OVERRIDE
import java.io.File
import java.util.Locale

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
                pendingViewRemovalNames = emptyList(),
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
      val viewRemovalFile = migrationFile.resolveSibling("${migrationFile.nameWithoutExtension}.views")
      val versionFile = File(projectDir, "db/latest_$variantName.version")
      val changedMarkers = when {
        database.isSubmodule -> emptyList()
        else -> submoduleChangeMarkers(projectDir)
      }
      val markerRemovalNames = changedMarkers
        .flatMap { transaction.snapshot(it).readLines() }
        .filter(String::isNotEmpty)
      val pendingMigrationStatements = when {
        database.isSubmodule -> transaction
          .snapshot(migrationFile)
          .readLines()
        else -> emptyList()
      }
      val pendingViewRemovalNames = when {
        database.isSubmodule -> transaction
          .snapshot(viewRemovalFile)
          .readLines()
        else -> emptyList()
      }
      val migrationResult = MigrationsHandler(
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
        pendingViewRemovalNames = pendingViewRemovalNames + markerRemovalNames,
        includePreviousOwnedViews = changedMarkers.isNotEmpty(),
        externalTransaction = transaction
      ).migrate()
      val outcome = when (val submoduleName = database.submoduleName) {
        null -> when {
          migrationResult.migrationHappened || changedMarkers.isNotEmpty() -> {
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
              migrationHappened = migrationResult.migrationHappened && configuration.structureOutputDirectory == null,
              pendingViewRemovalNames = migrationResult.viewRemovalNames,
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
    ?: 1000
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
  pendingViewRemovalNames: List<String>,
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
  val existingMarkerNames = when {
    migrationHappened && !replaceExistingStructures -> snapshots
      .snapshot(changeMarker)
      .readLines()
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
    val names = (existingMarkerNames + pendingViewRemovalNames)
      .filter(String::isNotEmpty)
      .distinct()
    snapshots.writeTextIfChanged(
      file = changeMarker,
      text = names.joinToString(
        separator = "\n",
        postfix = when {
          names.isEmpty() -> ""
          else -> "\n"
        }
      )
    )
  }
}

private fun submoduleChangeMarkers(projectDir: String) = File(projectDir, "db")
  .listFiles { it.isFile && it.extension == "changed" }
  .orEmpty()
  .sortedBy(File::getName)
