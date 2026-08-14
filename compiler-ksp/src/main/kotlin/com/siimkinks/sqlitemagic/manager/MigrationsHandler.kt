package com.siimkinks.sqlitemagic.manager

import java.io.File

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File
) {
  fun migrate(): Boolean {
    val previous = previousStructure
    val migrationHappened = previous != null && previous.tables != currentStructure.tables
    val migrationStatements = when {
      migrationHappened -> {
        val diff = SchemaDiffer.diff(
          from = previous,
          to = currentStructure
        )
        val plan = MigrationPlanner.plan(diff)
        MigrationSqlRenderer.render(
          diff = diff,
          plan = plan
        )
      }
      else -> emptyList()
    }
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile
    ).publish(
      structure = currentStructure,
      migrationStatements = migrationStatements
    )
    return migrationHappened
  }
}
