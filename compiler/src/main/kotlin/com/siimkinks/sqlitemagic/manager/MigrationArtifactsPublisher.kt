package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationArtifactsPublisher(
  private val structureFile: File,
  private val migrationFile: File,
  private val externalTransaction: FileSnapshotTransaction? = null
) {
  fun publish(
    structure: DatabaseStructure,
    migrationStatements: List<String>
  ) {
    val transaction = externalTransaction ?: FileSnapshotTransaction()
    try {
      transaction.writeTextIfChanged(
        file = structureFile,
        text = DatabaseStructureJson.write(structure)
      )
      publishLines(
        file = migrationFile,
        lines = migrationStatements,
        transaction = transaction
      )
    } catch (exception: Exception) {
      if (externalTransaction == null) transaction.restore(exception)
      throw exception
    }
  }

  private fun publishLines(
    file: File,
    lines: List<String>,
    transaction: FileSnapshotTransaction
  ) {
    when {
      lines.isNotEmpty() -> transaction.writeTextIfChanged(
        file = file,
        text = migrationScript(lines)
      )
      file.isFile -> {
        transaction.track(file)
        check(file.delete()) {
          "Failed to remove stale migration artifact ${file.absolutePath}"
        }
      }
    }
  }
}

internal fun migrationScript(lines: List<String>) = lines.joinToString(
  separator = "\n",
  postfix = "\n"
)
