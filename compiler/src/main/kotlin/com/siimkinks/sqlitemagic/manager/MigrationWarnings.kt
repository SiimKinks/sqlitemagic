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
  val tableChangeWarnings = (plan.directRebuilds + plan.batchedRebuilds).flatMap { change ->
    change.dataLossWarnings(
      cascadeSuffix = cascades
        ?.cascadeSuffix(change.from.name)
        .orEmpty()
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

private fun TableChange.dataLossWarnings(cascadeSuffix: String) = when {
  copiedColumns.isEmpty() -> listOf(
    "Migration deletes all rows of ${from.name} because none of its column(s) remain: " +
        "${droppedColumns.columnNames()}$cascadeSuffix. Edit the migration SQL to keep them."
  )
  !copiesRows -> listOf(
    "Migration deletes all rows of ${from.name} because existing rows have no value for its new column(s): " +
        "${columnsWithoutValue.columnNames()}$cascadeSuffix. Edit the migration SQL to keep them."
  )
  else -> listOfNotNull(
    nullRowColumns
      .takeIf(List<ColumnStructure>::isNotEmpty)
      ?.let { columns ->
        "Migration deletes rows of ${from.name} whose NOT NULL column(s) without a default hold NULL: " +
            "${columns.columnNames()}$cascadeSuffix. Edit the migration SQL to keep them."
      },
    droppedColumns
      .takeIf(List<ColumnStructure>::isNotEmpty)
      ?.let { columns ->
        "Migration drops column(s) of ${from.name} with their data: ${columns.columnNames()}. " +
            "Edit the migration SQL to keep it."
      }
  )
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
