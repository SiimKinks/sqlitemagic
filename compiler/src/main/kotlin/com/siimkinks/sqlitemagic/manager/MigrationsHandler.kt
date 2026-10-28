package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File,
  private val persistentStructureOnly: Boolean = false,
  private val pendingMigrationStatements: List<String> = emptyList(),
  private val externalTransaction: FileSnapshotTransaction? = null,
  private val warn: (String) -> Unit = {}
) {
  fun migrate(): Boolean {
    val diff = previousStructure?.let {
      SchemaDiffer.diff(
        from = it,
        to = currentStructure
      )
    }
    val migrationHappened = diff?.hasPersistentChanges == true
    val plannedMigration = diff
      ?.takeIf(SchemaDiff::hasPersistentTableOrIndexChanges)
      ?.let(::planMigration)
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile,
      persistentStructureOnly = persistentStructureOnly,
      externalTransaction = externalTransaction
    ).publish(
      structure = currentStructure,
      migrationStatements = pendingMigrationStatements + plannedMigration?.statements.orEmpty()
    )
    plannedMigration?.warnings?.forEach(warn)
    return migrationHappened
  }

  private fun planMigration(diff: SchemaDiff): PlannedMigration {
    val plan = MigrationPlanner.plan(diff)
    return PlannedMigration(
      statements = MigrationSqlRenderer.render(
        diff = diff,
        plan = plan
      ),
      warnings = migrationWarnings(
        diff = diff,
        plan = plan,
        enforcesForeignKeys = currentStructure.enforcesForeignKeys
      )
    )
  }
}

private class PlannedMigration(
  val statements: List<String>,
  val warnings: List<String>
)
