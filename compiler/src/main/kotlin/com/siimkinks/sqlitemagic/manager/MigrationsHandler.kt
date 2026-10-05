package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File,
  private val persistentStructureOnly: Boolean = false,
  private val pendingMigrationStatements: List<String> = emptyList(),
  private val externalTransaction: FileSnapshotTransaction? = null
) {
  fun migrate(): Boolean {
    val diff = previousStructure?.let {
      SchemaDiffer.diff(
        from = it,
        to = currentStructure
      )
    }
    val migrationHappened = diff?.hasPersistentChanges == true
    val plannedMigrationStatements = when {
      diff?.hasPersistentTableOrIndexChanges == true -> MigrationSqlRenderer.render(
        diff = diff,
        plan = MigrationPlanner.plan(diff)
      )
      else -> emptyList()
    }
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile,
      persistentStructureOnly = persistentStructureOnly,
      externalTransaction = externalTransaction
    ).publish(
      structure = currentStructure,
      migrationStatements = pendingMigrationStatements + plannedMigrationStatements
    )
    return migrationHappened
  }
}
