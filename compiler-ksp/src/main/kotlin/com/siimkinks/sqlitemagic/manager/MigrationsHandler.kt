package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File,
  private val persistentStructureOnly: Boolean = false
) {
  fun migrate(): Boolean {
    val previous = previousStructure
    val diff = previous?.let {
      SchemaDiffer.diff(
        from = it,
        to = currentStructure
      )
    }
    val migrationHappened = diff?.hasPersistentChanges == true
    val migrationStatements = when {
      diff == null || !migrationHappened -> emptyList()
      else -> MigrationSqlRenderer.render(
        diff = diff,
        plan = MigrationPlanner.plan(diff)
      )
    }
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile,
      persistentStructureOnly = persistentStructureOnly
    ).publish(
      structure = currentStructure,
      migrationStatements = migrationStatements
    )
    return migrationHappened
  }
}
