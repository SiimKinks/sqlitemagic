package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationArtifactsPublisher(
  private val structureFile: File,
  private val migrationFile: File
) {
  fun publish(
    structure: DatabaseStructure,
    migrationStatements: List<String>
  ) {
    val previousStructure = structureFile.takeIf(File::isFile)?.readBytes()
    val previousMigration = migrationFile.takeIf(File::isFile)?.readBytes()
    try {
      DatabaseStructureJson.write(
        file = structureFile,
        structure = structure
      )
      publishMigration(migrationStatements)
    } catch (exception: Exception) {
      restoreArtifact(
        file = structureFile,
        previousContents = previousStructure,
        originalFailure = exception
      )
      restoreArtifact(
        file = migrationFile,
        previousContents = previousMigration,
        originalFailure = exception
      )
      throw exception
    }
  }

  private fun publishMigration(migrationStatements: List<String>) {
    when {
      migrationStatements.isNotEmpty() -> {
        migrationFile.parentFile?.mkdirs()
        migrationFile.writeText(
          text = migrationStatements.joinToString(
            separator = System.lineSeparator(),
            postfix = System.lineSeparator()
          )
        )
      }
      migrationFile.isFile -> check(migrationFile.delete()) {
        "Failed to remove stale migration artifact ${migrationFile.absolutePath}"
      }
    }
  }

  private fun restoreArtifact(
    file: File,
    previousContents: ByteArray?,
    originalFailure: Exception
  ) {
    runCatching {
      when {
        previousContents != null -> {
          file.parentFile?.mkdirs()
          file.writeBytes(previousContents)
        }
        file.isFile -> check(file.delete()) {
          "Failed to remove partially written migration artifact ${file.absolutePath}"
        }
      }
    }.exceptionOrNull()
      ?.let(originalFailure::addSuppressed)
  }
}
