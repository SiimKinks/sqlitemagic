package com.siimkinks.sqlitemagic.manager

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
          to = transition.current
        )
      }
      .toList()
    val batchedRebuilds = diff.currentTables
      .asSequence()
      .filter { it.name in dependentRebuildTables }
      .map { table ->
        TableChange(
          from = diff.transitionByCurrentName.getValue(table.name).previous,
          to = table
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
    return TableMigrationPlan(
      prerequisiteNewTables = prerequisiteNewTables,
      simpleRenames = diff.transitions
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
        .toList(),
      directRebuilds = directRebuilds,
      batchedRebuilds = batchedRebuilds,
      removedTables = orderedRemovedTableNames.map(removedTablesByName::getValue),
      remainingNewTables = remainingNewTables
    )
  }
}

internal data class TableRename(
  val previousName: String,
  val currentName: String
)

internal data class TableChange(
  val from: TableSnapshot,
  val to: TableSnapshot
)

internal data class TableMigrationPlan(
  val prerequisiteNewTables: List<TableSnapshot>,
  val simpleRenames: List<TableRename>,
  val directRebuilds: List<TableChange>,
  val batchedRebuilds: List<TableChange>,
  val removedTables: List<TableSnapshot>,
  val remainingNewTables: List<TableSnapshot>
)
