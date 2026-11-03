package com.siimkinks.sqlitemagic.manager

internal fun migrationWarnings(
  diff: SchemaDiff,
  plan: TableMigrationPlan,
  enforcesForeignKeys: Boolean
): List<String> {
  val keptTables = diff.transitions.map(TableTransition::previous)
  val cascades = when {
    enforcesForeignKeys -> ForeignKeyGraph(
      tableNames = keptTables.map(TableSnapshot::name),
      referencesByTableName = keptTables.associate { table ->
        table.name to table.structure.normalizedReferencedTableNames()
      }
    )
    else -> null
  }
  val previousTableNames = diff.renamedTables.entries.associate { (currentName, previousName) ->
    currentName.normalizedSqlIdentifier() to previousName.normalizedSqlIdentifier()
  }
  val tableChangeWarnings = (plan.directRebuilds + plan.batchedRebuilds).flatMap { change ->
    change.dataLossWarnings(
      cascadeSuffix = cascades
        ?.cascadeSuffix(change.from.name)
        .orEmpty(),
      previousTableNames = previousTableNames
    )
  }
  val droppedTableWarnings = plan.removedTables.map { table ->
    droppedTableWarning(
      tableName = table.name,
      renameCandidates = diff.renameCandidates[table.name].orEmpty()
    )
  }
  return tableChangeWarnings + droppedTableWarnings
}

private fun ForeignKeyGraph.cascadeSuffix(tableName: String) = transitiveDependents(tableName)
  .takeIf(Set<String>::isNotEmpty)
  ?.let { "; ON DELETE CASCADE also deletes their dependent rows in ${it.joinToString()}" }

private fun TableChange.dataLossWarnings(
  cascadeSuffix: String,
  previousTableNames: Map<String, String>
) = when {
  copiedColumns.isEmpty() -> listOf(
    "Migration deletes all rows of ${from.name} because none of its column(s) remain: " +
        "${droppedColumns.columnNames()}$cascadeSuffix. Edit the migration SQL to keep them."
  )
  !copiesRows -> listOf(
    "Migration deletes all rows of ${from.name} because existing rows have no value for its new column(s): " +
        "${columnsWithoutValue.columnNames()}$cascadeSuffix. Edit the migration SQL to keep them."
  )
  else -> listOfNotNull(
    rowDeletionWarning(
      columns = nullRowColumns,
      condition = "NOT NULL column(s) without a default hold NULL",
      cascadeSuffix = cascadeSuffix
    ),
    rowDeletionWarning(
      columns = copiedColumns
        .filter { it.gainsReference(previousTableNames) }
        .map(ColumnCopy::previous),
      condition = "ON DELETE CASCADE column(s) reference missing rows",
      cascadeSuffix = cascadeSuffix
    ),
    rowDeletionWarning(
      columns = copiedColumns
        .filter(ColumnCopy::defaultsNullReference)
        .map(ColumnCopy::previous),
      condition = "NULL in NOT NULL ON DELETE CASCADE column(s) becomes a default that references a missing row",
      cascadeSuffix = cascadeSuffix
    ),
    droppedColumns
      .takeIf(List<ColumnStructure>::isNotEmpty)
      ?.let { columns ->
        "Migration drops column(s) of ${from.name} with their data: ${columns.columnNames()}. " +
            "Edit the migration SQL to keep it."
      }
  )
}

private fun TableChange.rowDeletionWarning(
  columns: List<ColumnStructure>,
  condition: String,
  cascadeSuffix: String
) = columns
  .takeIf(List<ColumnStructure>::isNotEmpty)
  ?.let {
    "Migration deletes rows of ${from.name} whose $condition: ${it.columnNames()}$cascadeSuffix. " +
        "Edit the migration SQL to keep them."
  }

private fun ColumnCopy.gainsReference(previousTableNames: Map<String, String>): Boolean {
  val reference = current
    .cascadeReference()
    ?.normalized()
    ?: return false
  return previous
    .cascadeReference()
    ?.normalized() != reference.copy(table = previousTableNames[reference.table] ?: reference.table)
}

private fun droppedTableWarning(
  tableName: String,
  renameCandidates: List<String>
) = when {
  renameCandidates.isEmpty() -> "Migration drops table $tableName with its rows. Edit the migration SQL to keep them."
  else -> "Migration drops table $tableName with its rows; if it was renamed to " +
      "${renameCandidates.joinToString(separator = " or ")}, edit the migration SQL to keep them."
}

private fun List<ColumnStructure>.columnNames() = joinToString(transform = ColumnStructure::name)
