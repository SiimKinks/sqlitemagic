package com.siimkinks.sqlitemagic.manager

internal enum class TableMigrationOperation {
  APPEND_COLUMNS,
  REBUILD
}

internal object MigrationPlanner {
  fun plan(diff: SchemaDiff): TableMigrationPlan {
    val currentReferencesByTableName = linkedMapOf<String, Set<String>>()
    val migrationReferencesByTableName = linkedMapOf<String, Set<String>>()
    diff.currentTables.forEach { table ->
      val currentReferences = table.structure.normalizedReferencedTableNames()
      currentReferencesByTableName[table.name] = currentReferences
      val previousReferences = diff.transitionByCurrentName[table.name]
        ?.previous
        ?.structure
        ?.takeUnless { it == table.structure }
        ?.normalizedReferencedTableNames()
        .orEmpty()
      migrationReferencesByTableName[table.name] = currentReferences + previousReferences
    }
    val dependencyGraph = ForeignKeyGraph(
      tableNames = diff.currentTables.map(TableSnapshot::name),
      referencesByTableName = migrationReferencesByTableName
    )
    val changedTableNames = diff.changedTransitions
      .mapTo(
        destination = linkedSetOf(),
        transform = { it.current.name }
      )
    val dependentRebuildTables = dependencyGraph.dependentRebuildClosure(
      previousNamesByCurrentName = diff.renamedTables,
      changedTables = changedTableNames,
      excludedTables = diff.newTableNames
    )
    val rebuildTables = dependentRebuildTables.ifEmpty { changedTableNames }
    val rebuildReferencedTableNames = rebuildTables
      .asSequence()
      .flatMap { tableName ->
        currentReferencesByTableName
          .getValue(tableName)
          .asSequence()
      }
      .toHashSet()
    val prerequisiteNewTables = diff.currentTables.filter { table ->
      table.name in diff.newTableNames &&
          table.name.normalizedSqlIdentifier() in rebuildReferencedTableNames
    }
    val prerequisiteNewTableNames = prerequisiteNewTables.mapTo(
      destination = linkedSetOf(),
      transform = TableSnapshot::name
    )
    val directRebuilds = diff.transitions
      .asSequence()
      .filter { transition ->
        transition.changed && transition.current.name !in dependentRebuildTables
      }
      .map { transition ->
        TableChange(
          from = transition.previous,
          to = transition.current,
          operation = tableMigrationOperation(
            from = transition.previous.structure,
            to = transition.current.structure,
            renamed = transition.renamed
          )
        )
      }
      .toList()
    val batchedRebuilds = diff.currentTables
      .asSequence()
      .filter { it.name in dependentRebuildTables }
      .map { table ->
        TableChange(
          from = diff.transitionByCurrentName.getValue(table.name).previous,
          to = table,
          operation = TableMigrationOperation.REBUILD
        )
      }
      .toList()
    val removedReferencesByTableName = diff.removedTables.associate { table ->
      table.name to table.structure.normalizedReferencedTableNames()
    }
    val removedTableGraph = ForeignKeyGraph(
      tableNames = diff.removedTableNames,
      referencesByTableName = removedReferencesByTableName
    )
    val orderedRemovedTableNames = removedTableGraph.orderDependentsFirst(
      tableNamesToOrder = diff.removedTableNames
    )
    val removedTablesByName = diff.removedTables.associateBy(TableSnapshot::name)
    val remainingNewTables = diff.currentTables.filter { table ->
      table.name in diff.newTableNames && table.name !in prerequisiteNewTableNames
    }
    val simpleRenames = diff.transitions
      .asSequence()
      .filter { transition ->
        transition.renamed &&
            !transition.changed &&
            transition.current.name !in dependentRebuildTables
      }
      .map { transition ->
        TableRename(
          previousName = transition.previous.name,
          currentName = transition.current.name
        )
      }
      .toList()
    val rebuiltPreviousTableNames = (directRebuilds + batchedRebuilds)
      .asSequence()
      .filter { it.operation == TableMigrationOperation.REBUILD }
      .mapTo(linkedSetOf(), transform = { it.from.name })
    val rebuiltCurrentTableNames = (directRebuilds + batchedRebuilds)
      .asSequence()
      .filter { it.operation == TableMigrationOperation.REBUILD }
      .mapTo(linkedSetOf(), transform = { it.to.name })
    val indexTransitionsByName = diff.indexTransitions.associateBy { it.previous.name }
    val indexTransitionsByCurrentName = diff.indexTransitions.associateBy { it.current.name }
    val indexDrops = diff.previousIndices.filterTo(arrayListOf()) { index ->
      val transition = indexTransitionsByName[index.name]
      transition == null ||
          transition.changed ||
          index.structure.forTable in rebuiltPreviousTableNames
    }
    val indexCreates = diff.currentIndices.filterTo(arrayListOf()) { index ->
      val transition = indexTransitionsByCurrentName[index.name]
      transition == null ||
          transition.changed ||
          index.structure.forTable in rebuiltCurrentTableNames ||
          index.structure.forTable in diff.newTableNames
    }
    return TableMigrationPlan(
      prerequisiteNewTables = prerequisiteNewTables,
      simpleRenames = simpleRenames,
      directRebuilds = directRebuilds,
      batchedRebuilds = batchedRebuilds,
      removedTables = orderedRemovedTableNames.map(removedTablesByName::getValue),
      remainingNewTables = remainingNewTables,
      indexDrops = indexDrops,
      indexCreates = indexCreates
    )
  }
}

private fun tableMigrationOperation(
  from: TableStructure,
  to: TableStructure,
  renamed: Boolean
) = when {
  renamed || from.name != to.name -> TableMigrationOperation.REBUILD
  from.columns.size >= to.columns.size -> TableMigrationOperation.REBUILD
  !from.columns.indices.all { index -> from.columns[index] == to.columns[index] } -> TableMigrationOperation.REBUILD
  !equivalentTableOptionsForMigration(from = from, to = to) -> TableMigrationOperation.REBUILD
  !to.columns
    .asSequence()
    .drop(from.columns.size)
    .all(ColumnStructure::canBeAddedWithAlterTable) -> TableMigrationOperation.REBUILD
  else -> TableMigrationOperation.APPEND_COLUMNS
}

private fun equivalentTableOptionsForMigration(
  from: TableStructure,
  to: TableStructure
) = normalizeSql(
  schema = from.schema.withoutTableColumns(),
  ownTableName = from.name,
  renames = emptyMap()
) == normalizeSql(
  schema = to.schema.withoutTableColumns(),
  ownTableName = to.name,
  renames = emptyMap()
)

internal data class TableRename(
  val previousName: String,
  val currentName: String
)

internal data class TableChange(
  val from: TableSnapshot,
  val to: TableSnapshot,
  val operation: TableMigrationOperation
)

internal data class TableMigrationPlan(
  val prerequisiteNewTables: List<TableSnapshot>,
  val simpleRenames: List<TableRename>,
  val directRebuilds: List<TableChange>,
  val batchedRebuilds: List<TableChange>,
  val removedTables: List<TableSnapshot>,
  val remainingNewTables: List<TableSnapshot>,
  val indexDrops: List<IndexSnapshot>,
  val indexCreates: List<IndexSnapshot>
)
