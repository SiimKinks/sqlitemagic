package com.siimkinks.sqlitemagic.manager

internal data class TableSnapshot(
  val name: String,
  val structure: TableStructure
)

internal data class TableTransition(
  val previous: TableSnapshot,
  val current: TableSnapshot,
  val changed: Boolean
) {
  val renamed get() = previous.name != current.name
}

internal data class IndexSnapshot(
  val name: String,
  val structure: IndexStructure
)

internal data class IndexTransition(
  val previous: IndexSnapshot,
  val current: IndexSnapshot,
  val changed: Boolean
)

internal data class ViewSnapshot(
  val name: String,
  val structure: ViewStructure
) {
  val normalizedViewId = name.normalizedSqlIdentifier()
}

internal data class ViewTransition(
  val previous: ViewSnapshot,
  val current: ViewSnapshot,
  val changed: Boolean
)

internal data class SchemaDiff(
  val previousTableNames: List<String>,
  val currentTables: List<TableSnapshot>,
  val transitions: List<TableTransition>,
  val newTables: List<TableSnapshot>,
  val removedTables: List<TableSnapshot>,
  val previousIndices: List<IndexSnapshot> = emptyList(),
  val currentIndices: List<IndexSnapshot> = emptyList(),
  val indexTransitions: List<IndexTransition> = emptyList(),
  val newIndices: List<IndexSnapshot> = emptyList(),
  val removedIndices: List<IndexSnapshot> = emptyList(),
  val viewTransitions: List<ViewTransition> = emptyList(),
  val newViews: List<ViewSnapshot> = emptyList(),
  val removedViews: List<ViewSnapshot> = emptyList(),
  val renameCandidates: Map<String, List<String>> = emptyMap()
) {
  val renamedTables = transitions
    .asSequence()
    .filter(TableTransition::renamed)
    .associateTo(
      destination = linkedMapOf(),
      transform = { transition ->
        transition.current.name to transition.previous.name
      }
    )

  val changedTransitions = transitions.filter(TableTransition::changed)

  val newTableNames = newTables.mapTo(
    destination = linkedSetOf(),
    transform = TableSnapshot::name
  )

  val removedTableNames = removedTables.mapTo(
    destination = linkedSetOf(),
    transform = TableSnapshot::name
  )

  val transitionByCurrentName = transitions.associateBy { it.current.name }

  val hasPersistentTableOrIndexChanges = changedTransitions.isNotEmpty() ||
      renamedTables.isNotEmpty() ||
      newTables.isNotEmpty() ||
      removedTables.isNotEmpty() ||
      indexTransitions.any(IndexTransition::changed) ||
      newIndices.isNotEmpty() ||
      removedIndices.isNotEmpty()

  val hasPersistentChanges = hasPersistentTableOrIndexChanges ||
      viewTransitions.any(ViewTransition::changed) ||
      newViews.isNotEmpty() ||
      removedViews.isNotEmpty()
}

internal object SchemaDiffer {
  fun diff(
    from: DatabaseStructure,
    to: DatabaseStructure
  ): SchemaDiff {
    val renameDetection = RenameDetector(
      from = from,
      to = to
    ).detect()
    val transitions = arrayListOf<TableTransition>()
    val newTables = arrayListOf<TableSnapshot>()

    to.tables.forEach { (tableName, table) ->
      val previousName = when {
        tableName in from.tables -> tableName
        else -> renameDetection.renamedTables[tableName]
      }
      when (previousName) {
        null -> newTables += TableSnapshot(
          name = tableName,
          structure = table
        )
        else -> transitions += TableTransition(
          previous = TableSnapshot(
            name = previousName,
            structure = from.tables.getValue(previousName)
          ),
          current = TableSnapshot(
            name = tableName,
            structure = table
          ),
          changed = tableName in renameDetection.changedTableNames
        )
      }
    }

    val removedTables = from.tables
      .asSequence()
      .filter { (tableName, _) -> tableName in renameDetection.removedTableNames }
      .map { (tableName, table) ->
        TableSnapshot(
          name = tableName,
          structure = table
        )
      }
      .toList()

    val previousIndices = from.indices.entries.map(::indexSnapshot)
    val currentIndices = to.indices.entries.map(::indexSnapshot)
    val previousIndicesByName = previousIndices.associateBy(IndexSnapshot::name)
    val currentIndicesByName = currentIndices.associateBy(IndexSnapshot::name)
    val indexTransitions = currentIndices.mapNotNull { currentIndex ->
      previousIndicesByName[currentIndex.name]?.let { previousIndex ->
        IndexTransition(
          previous = previousIndex,
          current = currentIndex,
          changed = previousIndex.structure != currentIndex.structure
        )
      }
    }
    val newIndices = currentIndices.filter { it.name !in previousIndicesByName }
    val removedIndices = previousIndices.filter { it.name !in currentIndicesByName }
    val previousViews = from.views.entries.map(::viewSnapshot)
    val currentViews = to.views.entries.map(::viewSnapshot)
    val previousViewsByIdentity = previousViews.associateBy(ViewSnapshot::normalizedViewId)
    val currentViewsByIdentity = currentViews.associateBy(ViewSnapshot::normalizedViewId)
    val viewTransitions = currentViews.mapNotNull { currentView ->
      previousViewsByIdentity[currentView.normalizedViewId]?.let { previousView ->
        ViewTransition(
          previous = previousView,
          current = currentView,
          changed = previousView != currentView
        )
      }
    }
    val newViews = currentViews.filter { it.normalizedViewId !in previousViewsByIdentity }
    val removedViews = previousViews.filter { it.normalizedViewId !in currentViewsByIdentity }

    return SchemaDiff(
      previousTableNames = from.tables.keys.toList(),
      currentTables = to.tables.map { (tableName, table) ->
        TableSnapshot(
          name = tableName,
          structure = table
        )
      },
      transitions = transitions,
      newTables = newTables,
      removedTables = removedTables,
      previousIndices = previousIndices,
      currentIndices = currentIndices,
      indexTransitions = indexTransitions,
      newIndices = newIndices,
      removedIndices = removedIndices,
      viewTransitions = viewTransitions,
      newViews = newViews,
      removedViews = removedViews,
      renameCandidates = renameDetection.renameCandidates
    )
  }

  private fun indexSnapshot(
    entry: Map.Entry<String, IndexStructure>
  ) = IndexSnapshot(
    name = entry.value.name.ifEmpty { entry.key },
    structure = entry.value
  )

  private fun viewSnapshot(
    entry: Map.Entry<String, ViewStructure>
  ) = ViewSnapshot(
    name = entry.key,
    structure = entry.value
  )
}

private data class RenameDetection(
  val renamedTables: LinkedHashMap<String, String>,
  val changedTableNames: LinkedHashSet<String>,
  val newTableNames: LinkedHashSet<String>,
  val removedTableNames: LinkedHashSet<String>,
  val renameCandidates: Map<String, List<String>>
)

private class RenameDetector(
  private val from: DatabaseStructure,
  private val to: DatabaseStructure
) {
  fun detect(): RenameDetection {
    val removedTableNames = from.tables.keys
      .filterNotTo(
        destination = linkedSetOf(),
        predicate = to.tables::containsKey
      )
    val candidateNewTableNames = to.tables.keys
      .filterNotTo(
        destination = linkedSetOf(),
        predicate = from.tables::containsKey
      )
    val renamedTables = findRenamedTables(removedTableNames = removedTableNames)
    val renamedSourceNames = renamedTables.values.toSet()
    val newTableNames = candidateNewTableNames
      .filterNotTo(
        destination = linkedSetOf(),
        predicate = renamedTables::containsKey
      )
    val changedTableNames = to.tables
      .asSequence()
      .filter { (tableName, table) ->
        from.tables[tableName]?.let { it != table } == true
      }
      .map { (tableName, _) -> tableName }
      .toCollection(linkedSetOf())

    renamedTables
      .filter { (tableName, previousTableName) ->
        isCaseOnlyRename(
          previousTableName = previousTableName,
          tableName = tableName
        ) || !isEquivalentRename(
          from = from.tables.getValue(previousTableName),
          to = to.tables.getValue(tableName),
          renames = renamedTables
        )
      }
      .keys
      .forEach(changedTableNames::add)
    val remainingRemovedTableNames = removedTableNames.filterTo(linkedSetOf()) {
      it !in renamedSourceNames
    }
    val unrenamedChangedTableNames = changedTableNames - renamedTables.keys
    return RenameDetection(
      renamedTables = renamedTables,
      changedTableNames = changedTableNames,
      newTableNames = newTableNames,
      removedTableNames = remainingRemovedTableNames,
      renameCandidates = remainingRemovedTableNames.associateWith { removedTableName ->
        renameCandidates(
          previousTable = from.tables.getValue(removedTableName),
          newTableNames = newTableNames,
          changedTableNames = unrenamedChangedTableNames
        )
      }
    )
  }

  private fun findRenamedTables(
    removedTableNames: Set<String>
  ): LinkedHashMap<String, String> {
    val result = linkedMapOf<String, String>()
    val usedSourceNames = hashSetOf<String>()
    val candidateShapeMatches = linkedMapOf<String, List<String>>()
    to.tables
      .asSequence()
      .filter { (tableName, _) -> tableName !in from.tables }
      .forEach { (tableName, table) ->
        candidateShapeMatches[tableName] = removedTableNames
          .asSequence()
          .filter { previousTableName ->
            columnsHaveSameShape(
              from = from.tables.getValue(previousTableName),
              to = table
            )
          }
          .toList()
      }
    var changed: Boolean
    do {
      changed = false
      val candidateMatches = linkedMapOf<String, List<String>>()
      candidateShapeMatches.forEach { (tableName, matches) ->
        if (tableName !in result) {
          candidateMatches[tableName] = matches.filterNotTo(
            destination = arrayListOf(),
            predicate = usedSourceNames::contains
          )
        }
      }
      val sourceMatchCounts = candidateMatches.values
        .asSequence()
        .flatten()
        .groupingBy(String::toString)
        .eachCount()
      candidateMatches
        .asSequence()
        .filter { (_, matches) ->
          matches.size == 1 && sourceMatchCounts[matches.single()] == 1
        }
        .forEach { (tableName, matches) ->
          result[tableName] = matches.single()
          usedSourceNames += matches.single()
          changed = true
        }
    } while (changed)
    return result
  }

  private fun renameCandidates(
    previousTable: TableStructure,
    newTableNames: Set<String>,
    changedTableNames: Set<String>
  ) = to.tables
    .filter { (tableName, table) ->
      when (tableName) {
        in newTableNames -> columnsHaveSameShape(
          from = previousTable,
          to = table
        ) || couldBeChangedRename(
          from = previousTable,
          to = table
        )
        in changedTableNames -> columnsHaveSameShape(
          from = previousTable,
          to = table
        )
        else -> false
      }
    }
    .keys
    .toList()

  private fun couldBeChangedRename(
    from: TableStructure,
    to: TableStructure
  ): Boolean {
    val fromId = from.columns.firstOrNull(ColumnStructure::id)
    val toId = to.columns.firstOrNull(ColumnStructure::id)
    if (fromId != null || toId != null) {
      return fromId != null && toId != null &&
          fromId.name == toId.name &&
          fromId.sqlType == toId.sqlType &&
          fromId.autoIncrement == toId.autoIncrement
    }
    val toColumns = to.columns.associateBy(ColumnStructure::name)
    return from.columns.any { fromColumn ->
      fromColumn.sqlType.isNotEmpty() &&
          toColumns[fromColumn.name]?.sqlType == fromColumn.sqlType
    } || columnsHaveSameValueShape(
      from = from,
      to = to
    )
  }

  private fun columnsHaveSameShape(
    from: TableStructure,
    to: TableStructure
  ) = from.columns.size == to.columns.size &&
      (from.columns zip to.columns).all { (fromColumn, toColumn) ->
        fromColumn.hasSameStorageShapeAs(toColumn) &&
            fromColumn.name == toColumn.name
      }

  private fun columnsHaveSameValueShape(
    from: TableStructure,
    to: TableStructure
  ) = from.columns.isNotEmpty() &&
      from.columns.size == to.columns.size &&
      (from.columns zip to.columns).all { (fromColumn, toColumn) ->
        fromColumn.sqlType.isNotEmpty() &&
            toColumn.sqlType.isNotEmpty() &&
            fromColumn.hasSameStorageShapeAs(toColumn)
      }

  private fun ColumnStructure.hasSameStorageShapeAs(
    other: ColumnStructure
  ) = id == other.id &&
      autoIncrement == other.autoIncrement &&
      onDeleteCascade == other.onDeleteCascade &&
      sqlType == other.sqlType

  private fun isCaseOnlyRename(
    previousTableName: String,
    tableName: String
  ) = previousTableName != tableName && previousTableName.equals(tableName, ignoreCase = true)

  private fun isEquivalentRename(
    from: TableStructure,
    to: TableStructure,
    renames: Map<String, String>
  ) = columnsEquivalent(
    from = from,
    to = to,
    renames = renames
  ) && normalizeSql(
    schema = from.schema,
    ownTableName = from.name,
    renames = emptyMap()
  ) == normalizeSql(
    schema = to.schema,
    ownTableName = to.name,
    renames = renames
  )

  private fun columnsEquivalent(
    from: TableStructure,
    to: TableStructure,
    renames: Map<String, String>
  ) = from.columns.size == to.columns.size &&
      (from.columns zip to.columns).all { (fromColumn, toColumn) ->
        fromColumn.id == toColumn.id &&
            fromColumn.autoIncrement == toColumn.autoIncrement &&
            fromColumn.name == toColumn.name &&
            fromColumn.onDeleteCascade == toColumn.onDeleteCascade &&
            fromColumn.sqlType == toColumn.sqlType &&
            normalizeSql(
              schema = fromColumn.schema,
              ownTableName = null,
              renames = emptyMap()
            ) == normalizeSql(
          schema = toColumn.schema,
          ownTableName = null,
          renames = renames
        )
      }
}
