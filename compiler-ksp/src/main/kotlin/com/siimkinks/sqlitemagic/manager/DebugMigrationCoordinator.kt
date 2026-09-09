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

internal class DebugMigrationCoordinator(
  private val configuration: DebugMigrationConfiguration,
  private val logger: KSPLogger
) {
  fun handle(
    database: GeneratedDatabaseElement,
    orderedTables: CreationOrderedTables
  ): DebugMigrationOutcome {
    val currentStructure = DatabaseStructure.from(
      orderedTables = orderedTables,
      indexes = database.indices
    )
    if (!configuration.enabled) {
      if (database.isSubmodule) {
        submoduleStructureDirectory()?.let { structureDirectory ->
          persistSubmoduleState(
            structureDirectory = structureDirectory,
            submoduleName = checkNotNull(database.submoduleName),
            structure = currentStructure,
            migrationHappened = false,
            replaceExistingStructures = configuration.structureOutputDirectory != null
          )
        }
      }
      return NO_DATABASE_VERSION_OVERRIDE
    }

    val projectDir = configuration.projectDir
    val variantName = configuration.variantName
    if (projectDir == null || variantName == null) {
      logger.warn("Skipping automatic debug migrations: project directory and variant name are required")
      return NO_DATABASE_VERSION_OVERRIDE
    }

    val latestDatabaseVersion = readLatestDebugVersion(
      projectDir = projectDir,
      mainModulePath = configuration.mainModulePath,
      variantName = variantName
    )
    val nextDatabaseVersion = latestDatabaseVersion + 1
    val structureFile = File(projectDir, "db/latest.struct")
    val migrationFileName = when (val submoduleName = database.submoduleName) {
      null -> "$nextDatabaseVersion.sql"
      else -> "$submoduleName$nextDatabaseVersion.sql"
    }
    val migrationHappened = try {
      MigrationsHandler(
        currentStructure = currentStructure,
        previousStructure = DatabaseStructureJson.read(structureFile),
        outputStructureFile = structureFile,
        migrationOutputFile = File(projectDir, "src/$variantName/assets/$migrationFileName")
      ).migrate()
    } catch (exception: Exception) {
      logger.warn("Failed to automatically migrate database: ${exception.message}")
      return NO_DATABASE_VERSION_OVERRIDE
    }

    return when (val submoduleName = database.submoduleName) {
      null -> when {
        migrationHappened || determineSubmoduleChange(projectDir) -> {
          writeMainModuleDebugVersion(
            projectDir = projectDir,
            variantName = variantName,
            version = nextDatabaseVersion
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
            replaceExistingStructures = configuration.structureOutputDirectory != null
          )
        }
        NO_DATABASE_VERSION_OVERRIDE
      }
    }
  }

  private fun submoduleStructureDirectory() = configuration
    .structureOutputDirectory
    ?.let(::File)
    ?: configuration.mainModulePath?.let { File(it, "db") }
}

private fun readLatestDebugVersion(
  projectDir: String,
  mainModulePath: String?,
  variantName: String
): Int {
  val versionFile = File(mainModulePath ?: projectDir, "db/latest_$variantName.version")
  return when {
    versionFile.exists() -> versionFile
      .readLines()
      .last()
      .toInt()
    else -> 1000
  }
}

private fun writeMainModuleDebugVersion(
  projectDir: String,
  variantName: String,
  version: Int
) {
  File(projectDir, "db/latest_$variantName.version").apply {
    parentFile?.mkdirs()
    writeText(version.toString())
  }
}

private fun persistSubmoduleState(
  structureDirectory: File,
  submoduleName: String,
  structure: DatabaseStructure,
  migrationHappened: Boolean,
  replaceExistingStructures: Boolean
) {
  val normalizedName = submoduleName.lowercase(Locale.ROOT)
  if (replaceExistingStructures) {
    structureDirectory
      .listFiles()
      .orEmpty()
      .filter { file ->
        file.isFile && file.name.startsWith("latest_") && file.name.endsWith(".struct")
      }
      .forEach { file ->
        check(file.delete()) {
          "Failed to remove stale staged SqliteMagic structure ${file.absolutePath}"
        }
      }
    structureDirectory
      .listFiles { file -> file.isFile && file.extension == "changed" }
      .orEmpty()
      .forEach { file ->
        check(file.delete()) {
          "Failed to remove stale staged SqliteMagic change marker ${file.absolutePath}"
        }
      }
  }
  DatabaseStructureJson.write(
    file = structureDirectory.resolve("latest_$normalizedName.struct"),
    structure = structure
  )
  if (migrationHappened) {
    structureDirectory
      .resolve("$normalizedName.changed")
      .createNewFile()
  }
}

private fun determineSubmoduleChange(projectDir: String): Boolean = File(projectDir, "db")
  .listFiles { it.extension == "changed" }
  .orEmpty()
  .onEach(File::delete)
  .isNotEmpty()
