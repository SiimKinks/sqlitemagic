package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.KSPLogger
import com.siimkinks.sqlitemagic.CompilerOptions
import com.siimkinks.sqlitemagic.manager.DebugMigrationOutcome.Companion.NO_DATABASE_VERSION_OVERRIDE
import java.io.File

internal data class DebugMigrationConfiguration(
  val enabled: Boolean,
  val projectDir: String?,
  val variantName: String?,
  val mainModulePath: String?
) {
  companion object {
    fun from(options: CompilerOptions) = DebugMigrationConfiguration(
      enabled = options.isDebugVariant && options.migrateDebug,
      projectDir = options.projectDir,
      variantName = options.variantName,
      mainModulePath = options.mainModulePath
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
    if (!configuration.enabled) return NO_DATABASE_VERSION_OVERRIDE

    val projectDir = configuration.projectDir
    val variantName = configuration.variantName
    if (projectDir == null || variantName == null) {
      logger.warn("Skipping automatic debug migrations: project directory and variant name are required")
      return NO_DATABASE_VERSION_OVERRIDE
    }

    val nextDatabaseVersion = readLatestDebugVersion(
      projectDir = projectDir,
      mainModulePath = configuration.mainModulePath,
      variantName = variantName
    ) + 1
    val structureFile = File(projectDir, "db/latest.struct")
    val migrationFileName = when (val submoduleName = database.submoduleName) {
      null -> "$nextDatabaseVersion.sql"
      else -> "$submoduleName$nextDatabaseVersion.sql"
    }
    val currentStructure = DatabaseStructure.from(orderedTables)
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
      null -> {
        val submoduleChangeHappened = determineSubmoduleChange(projectDir)
        if (migrationHappened || submoduleChangeHappened) {
          writeMainModuleDebugVersion(
            projectDir = projectDir,
            variantName = variantName,
            version = nextDatabaseVersion
          )
          DebugMigrationOutcome(databaseVersionOverride = nextDatabaseVersion)
        } else {
          NO_DATABASE_VERSION_OVERRIDE
        }
      }
      else -> {
        configuration.mainModulePath?.let { mainModulePath ->
          persistSubmoduleState(
            mainModulePath = mainModulePath,
            submoduleName = submoduleName,
            structure = currentStructure,
            migrationHappened = migrationHappened
          )
        }
        NO_DATABASE_VERSION_OVERRIDE
      }
    }
  }
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
  mainModulePath: String,
  submoduleName: String,
  structure: DatabaseStructure,
  migrationHappened: Boolean
) {
  val normalizedName = submoduleName.lowercase()
  val databaseDirectory = File(mainModulePath, "db")
  DatabaseStructureJson.write(
    file = databaseDirectory.resolve("latest_$normalizedName.struct"),
    structure = structure
  )
  if (migrationHappened) {
    databaseDirectory
      .resolve("$normalizedName.changed")
      .createNewFile()
  }
}

private fun determineSubmoduleChange(projectDir: String): Boolean = File(projectDir, "db")
  .listFiles { it.extension == "changed" }
  .orEmpty()
  .onEach(File::delete)
  .isNotEmpty()
