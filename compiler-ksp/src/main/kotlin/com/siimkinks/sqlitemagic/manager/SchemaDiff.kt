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

internal data class SchemaDiff(
  val previousTableNames: List<String>,
  val currentTables: List<TableSnapshot>,
  val transitions: List<TableTransition>,
  val newTables: List<TableSnapshot>,
  val removedTables: List<TableSnapshot>
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
      removedTables = removedTables
    )
  }
}

private data class RenameDetection(
  val renamedTables: LinkedHashMap<String, String>,
  val changedTableNames: LinkedHashSet<String>,
  val newTableNames: LinkedHashSet<String>,
  val removedTableNames: LinkedHashSet<String>
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
    validateOccupiedRenameCandidates(
      removedTableNames = remainingRemovedTableNames,
      changedTableNames = changedTableNames
    )
    validateChangedRenameCandidates(
      removedTableNames = remainingRemovedTableNames,
      newTableNames = newTableNames
    )
    return RenameDetection(
      renamedTables = renamedTables,
      changedTableNames = changedTableNames,
      newTableNames = newTableNames,
      removedTableNames = remainingRemovedTableNames
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

  private fun validateOccupiedRenameCandidates(
    removedTableNames: Set<String>,
    changedTableNames: Set<String>
  ) {
    val candidateSourceNames = from.tables.keys
      .asSequence()
      .filter { sourceTableName ->
        sourceTableName in removedTableNames || sourceTableName in changedTableNames
      }
    val candidates = changedTableNames.flatMap { changedTableName ->
      val currentTable = to.tables.getValue(changedTableName)
      candidateSourceNames.mapNotNull { sourceTableName ->
        when {
          sourceTableName == changedTableName -> null
          columnsHaveSameShape(
            from = from.tables.getValue(sourceTableName),
            to = currentTable
          ) -> sourceTableName to changedTableName
          else -> null
        }
      }
    }
    if (candidates.isEmpty()) return

    val candidateNames = candidates.joinToString(
      transform = { (sourceTableName, changedTableName) ->
        "$sourceTableName -> $changedTableName"
      }
    )
    error(
      "Cannot automatically infer table rename(s) through an occupied table name: $candidateNames; " +
          "keep each destination name unoccupied for one migration or provide an explicit migration"
    )
  }

  private fun validateChangedRenameCandidates(
    removedTableNames: Set<String>,
    newTableNames: Set<String>
  ) {
    val previousTables = removedTableNames.asSequence().map { tableName ->
      tableName to from.tables.getValue(tableName)
    }
    val currentTables = newTableNames.asSequence().map { tableName ->
      tableName to to.tables.getValue(tableName)
    }
    val equivalentCandidates = previousTables
      .flatMap { (removedTableName, previousTable) ->
        currentTables.mapNotNull { (newTableName, currentTable) ->
          when {
            columnsHaveSameShape(
              from = previousTable,
              to = currentTable
            ) -> removedTableName to newTableName
            else -> null
          }
        }
      }
    val ambiguousSources = equivalentCandidates
      .groupBy { (removedTableName, _) -> removedTableName }
      .values
      .asSequence()
      .filter { it.size > 1 }
      .flatten()
      .toList()
    if (ambiguousSources.isNotEmpty()) {
      val candidateNames = ambiguousSources.joinToString(
        transform = { (removedTableName, newTableName) -> "$removedTableName -> $newTableName" }
      )
      error(
        "Cannot automatically infer changed table rename(s): $candidateNames; " +
            "keep the table name stable for one migration or provide an explicit migration"
      )
    }
    val candidates = currentTables
      .flatMap { (newTableName, currentTable) ->
        val toId = currentTable.columns.firstOrNull(ColumnStructure::id)
        val toColumns = when (toId) {
          null -> currentTable.columns.associateBy(ColumnStructure::name)
          else -> emptyMap()
        }
        previousTables.mapNotNull { (removedTableName, previousTable) ->
          when {
            couldBeChangedRename(
              from = previousTable,
              to = currentTable,
              toId = toId,
              toColumns = toColumns
            ) -> removedTableName to newTableName
            else -> null
          }
        }
      }
      .toList()
    val changedCandidates = candidates.filterNot { (removedTableName, newTableName) ->
      columnsEquivalent(
        from = from.tables.getValue(removedTableName),
        to = to.tables.getValue(newTableName),
        renames = emptyMap()
      )
    }
    if (candidates.size == 1 || changedCandidates.isNotEmpty()) {
      val candidateNames = (changedCandidates.ifEmpty { candidates }).joinToString(
        transform = { (removedTableName, newTableName) -> "$removedTableName -> $newTableName" }
      )
      error(
        "Cannot automatically infer changed table rename(s): $candidateNames; " +
            "keep the table name stable for one migration or provide an explicit migration"
      )
    }
  }

  private fun couldBeChangedRename(
    from: TableStructure,
    to: TableStructure,
    toId: ColumnStructure?,
    toColumns: Map<String, ColumnStructure>
  ): Boolean {
    val fromId = from.columns.firstOrNull(ColumnStructure::id)
    if (fromId != null || toId != null) {
      return fromId != null && toId != null &&
          fromId.name == toId.name &&
          fromId.sqlType == toId.sqlType &&
          fromId.autoIncrement == toId.autoIncrement
    }
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
      from.columns.indices.all { index ->
        val fromColumn = from.columns[index]
        val toColumn = to.columns[index]
        fromColumn.hasSameStorageShapeAs(toColumn) &&
            fromColumn.name == toColumn.name
      }

  private fun columnsHaveSameValueShape(
    from: TableStructure,
    to: TableStructure
  ) = from.columns.isNotEmpty() &&
      from.columns.size == to.columns.size &&
      from.columns.indices.all { index ->
        val fromColumn = from.columns[index]
        val toColumn = to.columns[index]
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
      from.columns.indices.all { index ->
        val fromColumn = from.columns[index]
        val toColumn = to.columns[index]
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
