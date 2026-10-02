package com.siimkinks.sqlitemagic.manager

import java.io.File

internal data class MigrationResult(
  val migrationHappened: Boolean,
  val viewRemovalNames: List<String>
)

internal class MigrationsHandler(
  private val currentStructure: DatabaseStructure,
  private val previousStructure: DatabaseStructure?,
  private val outputStructureFile: File,
  private val migrationOutputFile: File,
  private val persistentStructureOnly: Boolean = false,
  private val pendingMigrationStatements: List<String> = emptyList(),
  private val pendingViewRemovalNames: List<String> = emptyList(),
  private val includePreviousOwnedViews: Boolean = false,
  private val externalTransaction: FileSnapshotTransaction? = null
) {
  fun migrate(): MigrationResult {
    val diff = previousStructure?.let {
      SchemaDiffer.diff(
        from = it,
        to = currentStructure
      )
    }
    val migrationHappened = diff?.hasPersistentChanges == true
    val plan = when {
      diff?.hasPersistentTableOrIndexChanges == true -> MigrationPlanner.plan(diff)
      else -> null
    }
    val plannedMigrationStatements = when {
      plan != null -> MigrationSqlRenderer.render(
        diff = checkNotNull(diff),
        plan = plan.tables
      )
      else -> emptyList()
    }
    val previousOwnedViewNames = when {
      !migrationHappened && !includePreviousOwnedViews -> emptyList()
      plan != null -> plan.previousOwnedViewNames
      else -> diff
        ?.previousViews
        ?.map(ViewSnapshot::name)
        .orEmpty()
    }
    val viewRemovalNames = (pendingViewRemovalNames + previousOwnedViewNames).distinct()
    MigrationArtifactsPublisher(
      structureFile = outputStructureFile,
      migrationFile = migrationOutputFile,
      viewRemovalFile = migrationOutputFile.resolveSibling(
        "${migrationOutputFile.nameWithoutExtension}.views"
      ),
      persistentStructureOnly = persistentStructureOnly,
      externalTransaction = externalTransaction
    ).publish(
      structure = currentStructure,
      migrationStatements = pendingMigrationStatements + plannedMigrationStatements,
      previousOwnedViewNames = viewRemovalNames
    )
    return MigrationResult(
      migrationHappened = migrationHappened,
      viewRemovalNames = viewRemovalNames
    )
  }
}
