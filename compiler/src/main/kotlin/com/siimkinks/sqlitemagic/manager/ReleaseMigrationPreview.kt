package com.siimkinks.sqlitemagic.manager

import java.io.File

class ReleaseMigrationPreview internal constructor(
  val version: Int,
  val report: String,
  val sql: String?
)

internal fun PreparedRelease.previewReport(
  projectDir: File,
  databaseVersion: Int
) = listOf(
  header(databaseVersion),
  schemaChanges(),
  warningsReport(),
  sqlReport(projectDir)
).joinToString(separator = "\n\n")

private fun PreparedRelease.header(databaseVersion: Int) = listOfNotNull(
  when (previousVersion) {
    null -> "Release $version preview (first release)"
    else -> "Release $version preview (latest release $previousVersion)"
  },
  when (databaseVersion) {
    version -> null
    else -> "Database version $databaseVersion is not above the latest release; " +
        "set @Database(version = $version) before publishing this release."
  }
).joinToString(separator = "\n")

private fun PreparedRelease.schemaChanges(): String {
  val migration = migration ?: return "First release: generated code creates the schema."
  val groups = listOfNotNull(
    changeGroup(
      title = "Tables",
      changes = migration.tableChanges()
    ),
    changeGroup(
      title = "Indices",
      changes = migration.diff.indexChanges()
    ),
    changeGroup(
      title = "Views",
      changes = migration.diff.viewChanges()
    )
  )
  return when {
    groups.isEmpty() -> "No schema changes since release $previousVersion."
    else -> groups.joinToString(
      separator = "\n",
      prefix = "Schema changes since release $previousVersion:\n"
    )
  }
}

private fun PreparedRelease.warningsReport() = when {
  warnings.isEmpty() -> "No warnings."
  else -> warnings.joinToString(
    separator = "\n",
    prefix = "Warnings:\n"
  ) { "  $it" }
}

private fun PreparedRelease.sqlReport(projectDir: File) = when (sql) {
  null -> "No migration SQL."
  else -> sql
    .reader()
    .readLines()
    .joinToString(
      separator = "\n",
      prefix = "Migration SQL for ${sqlFile.relativeToOrSelf(projectDir).invariantSeparatorsPath}:\n"
    ) { "  $it" }
}

private fun SchemaMigration.tableChanges(): List<String> {
  val changesByCurrentName = plan
    ?.let { it.directRebuilds + it.batchedRebuilds }
    .orEmpty()
    .associateBy { it.to.name }
  val keptTableChanges = diff.transitions.mapNotNull { transition ->
    val name = when {
      transition.renamed -> "${transition.previous.name} -> ${transition.current.name}"
      else -> transition.current.name
    }
    when (val change = changesByCurrentName[transition.current.name]) {
      null -> "~ $name: renamed".takeIf { transition.renamed }
      else -> "~ $name: ${change.description(changed = transition.changed)}"
    }
  }
  return diff.newTables.map { "+ ${it.name}" } + keptTableChanges + diff.removedTables.map { "- ${it.name}" }
}

private fun TableChange.description(changed: Boolean): String {
  if (!changed) return "rebuilt because it references a rebuilt table"
  val columnChanges = listOfNotNull(
    columnChange(
      verb = "adds",
      columns = addedColumns
    ),
    columnChange(
      verb = "drops",
      columns = droppedColumns
    ),
    columnChange(
      verb = "changes",
      columns = copiedColumns
        .filter { it.previous != it.current }
        .map(ColumnCopy::current)
    )
  ).ifEmpty { listOf("changes table definition") }
  val changes = when (operation) {
    TableMigrationOperation.APPEND_COLUMNS -> columnChanges
    TableMigrationOperation.REBUILD -> columnChanges + "rebuilt"
  }
  return changes.joinToString(separator = "; ")
}

private fun columnChange(
  verb: String,
  columns: List<ColumnStructure>
) = when {
  columns.isEmpty() -> null
  else -> "$verb ${columns.joinToString(transform = ColumnStructure::name)}"
}

private fun changeGroup(
  title: String,
  changes: List<String>
) = when {
  changes.isEmpty() -> null
  else -> changes.joinToString(
    separator = "\n",
    prefix = "  $title:\n"
  ) { "    $it" }
}

private fun SchemaDiff.indexChanges() = newIndices.map { "+ ${it.name}" } +
    indexTransitions
      .filter(IndexTransition::changed)
      .map { "~ ${it.current.name}" } +
    removedIndices.map { "- ${it.name}" }

private fun SchemaDiff.viewChanges() = newViews.map { "+ ${it.name}" } +
    viewTransitions
      .filter(ViewTransition::changed)
      .map { "~ ${it.current.name}" } +
    removedViews.map { "- ${it.name}" }
