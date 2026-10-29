package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File,
  private val pendingMigrationStatements: List<String> = emptyList(),
  private val externalTransaction: FileSnapshotTransaction? = null,
  private val warn: (String) -> Unit = {}
) {
  fun migrate(): Boolean {
    val migration = previousStructure?.let { previous ->
      planSchemaMigration(
        previous = previous,
        current = currentStructure
      )
    }
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile,
      externalTransaction = externalTransaction
    ).publish(
      structure = currentStructure,
      migrationStatements = pendingMigrationStatements + migration?.statements.orEmpty()
    )
    migration?.warnings?.forEach(warn)
    return migration?.diff?.hasPersistentChanges == true
  }
}
