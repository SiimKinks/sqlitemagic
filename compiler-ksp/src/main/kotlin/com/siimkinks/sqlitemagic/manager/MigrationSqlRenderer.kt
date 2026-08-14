package com.siimkinks.sqlitemagic.manager

import java.util.Locale

internal object MigrationSqlRenderer {
  fun render(
    diff: SchemaDiff,
    plan: TableMigrationPlan
  ): List<String> {
    val migrationStatements = arrayListOf<String>()
    val usedTemporaryNames = diff.previousTableNames
      .toMutableSet()
      .apply {
        diff.currentTables.forEach { add(it.name) }
      }
    createNewTables(
      names = plan.prerequisiteNewTables,
      migrationStatements = migrationStatements
    )
    plan.simpleRenames.forEach { rename ->
      migrationStatements += "ALTER TABLE ${rename.previousName} RENAME TO ${rename.currentName}"
    }
    plan.directRebuilds.forEach { rebuild ->
      migrateTable(
        from = rebuild.from.structure,
        to = rebuild.to.structure,
        sourceTableName = rebuild.from.name,
        temporaryTableName = temporaryTableName(
          tableName = rebuild.to.structure.name,
          usedNames = usedTemporaryNames
        ),
        migrationStatements = migrationStatements
      )
    }
    migrateDependentTables(
      rebuilds = plan.batchedRebuilds,
      usedNames = usedTemporaryNames,
      migrationStatements = migrationStatements
    )
    plan.removedTables.forEach { table ->
      migrationStatements += "DROP TABLE IF EXISTS ${table.name}"
    }
    createNewTables(
      names = plan.remainingNewTables,
      migrationStatements = migrationStatements
    )
    return migrationStatements
  }

  private fun migrateTable(
    from: TableStructure,
    to: TableStructure,
    sourceTableName: String,
    temporaryTableName: String,
    migrationStatements: MutableList<String>
  ) {
    val unchangedPrefix = from.columns.size < to.columns.size &&
        from.columns.indices.all { index -> from.columns[index] == to.columns[index] } &&
        equivalentTableOptions(
          from = from,
          to = to
        ) &&
        to.columns
          .asSequence()
          .drop(from.columns.size)
          .all(ColumnStructure::canBeAddedWithAlterTable)
    if (unchangedPrefix) {
      to.columns
        .asSequence()
        .drop(from.columns.size)
        .forEach { column ->
          migrationStatements += "ALTER TABLE ${to.name} ADD COLUMN ${column.schema}"
        }
      return
    }

    validateRebuildColumns(
      from = from,
      to = to
    )
    migrationStatements += "ALTER TABLE $sourceTableName RENAME TO $temporaryTableName"
    migrationStatements += to.schema
    addMutualColumnCopy(
      from = from,
      to = to,
      temporaryTableName = temporaryTableName,
      migrationStatements = migrationStatements
    )
    migrationStatements += "DROP TABLE IF EXISTS $temporaryTableName"
  }

  private fun migrateDependentTables(
    rebuilds: List<TableChange>,
    usedNames: MutableSet<String>,
    migrationStatements: MutableList<String>
  ) {
    if (rebuilds.isEmpty()) return
    val tables = rebuilds.map { rebuild ->
      RebuildTable(
        from = rebuild.from,
        to = rebuild.to,
        temporaryName = temporaryTableName(
          tableName = rebuild.to.structure.name,
          usedNames = usedNames
        )
      )
    }

    val tableCreationStatements = arrayListOf<String>()
    tables.forEach { table ->
      validateRebuildColumns(
        from = table.from.structure,
        to = table.to.structure
      )
      migrationStatements += "ALTER TABLE ${table.from.name} RENAME TO ${table.temporaryName}"
      tableCreationStatements += table.to.structure.schema
      addMutualColumnCopy(
        from = table.from.structure,
        to = table.to.structure,
        temporaryTableName = table.temporaryName,
        migrationStatements = tableCreationStatements
      )
    }
    migrationStatements += tableCreationStatements
    tables
      .asReversed()
      .forEach { table ->
        migrationStatements += "DROP TABLE IF EXISTS ${table.temporaryName}"
      }
  }

  private fun addMutualColumnCopy(
    from: TableStructure,
    to: TableStructure,
    temporaryTableName: String,
    migrationStatements: MutableList<String>
  ) {
    val currentColumnsByName = to.columns.associateBy { column ->
      column.name.lowercase(Locale.ROOT)
    }
    val mutualColumns = from.columns.mapNotNull { previousColumn ->
      currentColumnsByName[previousColumn.name.lowercase(Locale.ROOT)]?.let { currentColumn ->
        previousColumn.name to currentColumn.name
      }
    }
    if (mutualColumns.isNotEmpty()) {
      val sourceColumnNames = mutualColumns.joinToString(
        separator = ",",
        transform = Pair<String, String>::first
      )
      val destinationColumnNames = mutualColumns.joinToString(
        separator = ",",
        transform = Pair<String, String>::second
      )
      migrationStatements += "INSERT INTO ${to.name} ($destinationColumnNames) SELECT $sourceColumnNames FROM $temporaryTableName"
    }
  }

  private fun equivalentTableOptions(
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

  private fun createNewTables(
    names: Iterable<TableSnapshot>,
    migrationStatements: MutableList<String>
  ) = names
    .asSequence()
    .map(TableSnapshot::structure)
    .map(TableStructure::schema)
    .forEach(migrationStatements::add)

  private fun validateRebuildColumns(
    from: TableStructure,
    to: TableStructure
  ) {
    val previousColumnNames = from.columns.mapTo(
      destination = hashSetOf(),
      transform = { it.name.lowercase(Locale.ROOT) }
    )
    to.columns
      .asSequence()
      .filterNot { it.name.lowercase(Locale.ROOT) in previousColumnNames }
      .firstOrNull { it.requiresValueDuringRebuild(table = to) }
      ?.let { column ->
        error(
          "Cannot automatically migrate ${from.name}: new column ${column.name} is required but has no usable default"
        )
      }
  }

  private fun temporaryTableName(
    tableName: String,
    usedNames: MutableSet<String>
  ): String {
    var temporaryName = "${tableName}_"
    while (usedNames.any { usedName -> usedName.equals(temporaryName, ignoreCase = true) }) {
      temporaryName += "_"
    }
    usedNames += temporaryName
    return temporaryName
  }
}

private data class RebuildTable(
  val from: TableSnapshot,
  val to: TableSnapshot,
  val temporaryName: String
)
