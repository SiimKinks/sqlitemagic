package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
import java.util.Locale

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
        ?.takeIf { it.changed || it.renamed }
        ?.previous
        ?.structure
        ?.normalizedReferencedTableNames()
        .orEmpty()
      migrationReferencesByTableName[table.name] = currentReferences + previousReferences
    }
    val dependencyGraph = ForeignKeyGraph(
      tableNames = diff.currentTables.map(TableSnapshot::name),
      referencesByTableName = migrationReferencesByTableName
    )
    val operationsByTableName = diff.changedTransitions.associate { transition ->
      transition.current.name to tableMigrationOperation(
        from = transition.previous.structure,
        to = transition.current.structure,
        renamed = transition.renamed
      )
    }
    val changedTableNames = operationsByTableName.keys
    val dependentRebuildTables = dependencyGraph.dependentRebuildClosure(
      previousNamesByCurrentName = diff.renamedTables,
      changedTables = operationsByTableName
        .filterValues(TableMigrationOperation.REBUILD::equals)
        .keys,
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
          operation = operationsByTableName.getValue(transition.current.name)
        )
      }
      .toList()
    val currentTablesByName = diff.currentTables.associateBy(TableSnapshot::name)
    val batchedRebuilds = dependencyGraph
      .orderReferencesFirst(
        tableNamesToOrder = diff.currentTables
          .map(TableSnapshot::name)
          .filter(dependentRebuildTables::contains)
      )
      .map { tableName ->
        TableChange(
          from = diff.transitionByCurrentName.getValue(tableName).previous,
          to = currentTablesByName.getValue(tableName),
          operation = TableMigrationOperation.REBUILD
        )
      }
    val batchedTableNames = batchedRebuilds
      .asSequence()
      .flatMap { sequenceOf(it.from.name, it.to.name) }
      .mapTo(
        destination = hashSetOf(),
        transform = String::normalizedSqlIdentifier
      )
    val (batchedPrerequisiteNewTables, earlyPrerequisiteNewTables) = prerequisiteNewTables.partition { table ->
      currentReferencesByTableName
        .getValue(table.name)
        .any(batchedTableNames::contains)
    }
    val removedReferencesByTableName = diff.removedTables.associate { table ->
      table.name to table.structure.normalizedReferencedTableNames()
    }
    val removedTableGraph = ForeignKeyGraph(
      tableNames = diff.removedTableNames,
      referencesByTableName = removedReferencesByTableName
    )
    val orderedRemovedTableNames = removedTableGraph
      .orderReferencesFirst(tableNamesToOrder = diff.removedTableNames)
      .asReversed()
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
      .mapTo(destination = linkedSetOf()) { it.from.name }
    val rebuiltCurrentTableNames = (directRebuilds + batchedRebuilds)
      .asSequence()
      .filter { it.operation == TableMigrationOperation.REBUILD }
      .mapTo(destination = linkedSetOf()) { it.to.name }
    val indexTransitionsByName = diff.indexTransitions.associateBy { it.previous.name }
    val indexTransitionsByCurrentName = diff.indexTransitions.associateBy { it.current.name }
    val indexDrops = diff.previousIndices.filterTo(arrayListOf()) { index ->
      val transition = indexTransitionsByName[index.name]
      index.structure.forTable !in rebuiltPreviousTableNames && (transition == null || transition.changed)
    }
    val indexCreates = diff.currentIndices.filterTo(arrayListOf()) { index ->
      val transition = indexTransitionsByCurrentName[index.name]
      transition == null ||
          transition.changed ||
          index.structure.forTable in rebuiltCurrentTableNames ||
          index.structure.forTable in diff.newTableNames
    }
    return TableMigrationPlan(
      prerequisiteNewTables = earlyPrerequisiteNewTables,
      batchedPrerequisiteNewTables = batchedPrerequisiteNewTables,
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
  !(from.columns zip to.columns)
    .all { (fromColumn, toColumn) -> fromColumn.sameSchemaAs(toColumn) } -> TableMigrationOperation.REBUILD
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
) {
  private val currentColumnsByName = to.structure.columns.associateBy(ColumnStructure::lookupName)
  private val previousColumnNames = from.structure.columns.mapTo(
    destination = hashSetOf(),
    transform = ColumnStructure::lookupName
  )
  val copiedColumns = from.structure.columns.mapNotNull { previous ->
    currentColumnsByName[previous.lookupName]?.let { current ->
      ColumnCopy(
        previous = previous,
        current = current,
        gainsNotNull = current.isNotNullIn(to.structure) && !previous.isNotNullIn(from.structure)
      )
    }
  }
  val droppedColumns = from.structure.columns.filterNot { it.lookupName in currentColumnsByName }
  val addedColumns = to.structure.columns.filterNot { it.lookupName in previousColumnNames }
  val columnsWithoutValue = addedColumns.filter { it.requiresValueDuringRebuild(table = to.structure) }
  val copiesRows get() = copiedColumns.isNotEmpty() && columnsWithoutValue.isEmpty()
  val missingReferenceConditions = copiedColumns.mapNotNull(ColumnCopy::missingReferenceCondition)
  val copiesMissingReferences get() = copiesRows && missingReferenceConditions.isNotEmpty()
  val nullRowColumns = copiedColumns
    .filter(ColumnCopy::deletesNullRows)
    .map(ColumnCopy::previous)
  val transformedImplicitDefaultColumns = addedColumns.filter(ColumnStructure::transformedImplicitDefault) +
      copiedColumns
        .filter(ColumnCopy::defaultsNullWithTransformedImplicitDefault)
        .map(ColumnCopy::current)
}

internal data class ColumnCopy(
  val previous: ColumnStructure,
  val current: ColumnStructure,
  private val gainsNotNull: Boolean
) {
  private val defaultsNull get() = gainsNotNull && current.notNullDefault != null
  val deletesNullRows get() = gainsNotNull && current.notNullDefault == null
  val defaultsNullWithTransformedImplicitDefault get() = defaultsNull && current.transformedImplicitDefault
  val defaultsNullReference get() = defaultsNull && current.cascadeReference() != null
  val missingReferenceCondition
    get() = current
      .cascadeReference()
      ?.let { reference ->
        val column = quoteSqlIdentifier(current.name)
        "$column IS NOT NULL AND $column NOT IN " +
            "(SELECT ${quoteSqlIdentifier(reference.column)} FROM ${quoteSqlIdentifier(reference.table)})"
      }
  val sourceValue
    get() = when {
      defaultsNull -> "IFNULL(${quoteSqlIdentifier(previous.name)}, ${current.notNullDefault})"
      else -> quoteSqlIdentifier(previous.name)
    }
}

private val ColumnStructure.lookupName get() = name.lowercase(Locale.ROOT)

internal data class TableMigrationPlan(
  val prerequisiteNewTables: List<TableSnapshot>,
  val batchedPrerequisiteNewTables: List<TableSnapshot>,
  val simpleRenames: List<TableRename>,
  val directRebuilds: List<TableChange>,
  val batchedRebuilds: List<TableChange>,
  val removedTables: List<TableSnapshot>,
  val remainingNewTables: List<TableSnapshot>,
  val indexDrops: List<IndexSnapshot>,
  val indexCreates: List<IndexSnapshot>
)
