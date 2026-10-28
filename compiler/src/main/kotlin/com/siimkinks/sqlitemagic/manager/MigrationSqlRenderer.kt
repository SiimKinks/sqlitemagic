package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.manager.TableMigrationOperation.APPEND_COLUMNS
import com.siimkinks.sqlitemagic.manager.TableMigrationOperation.REBUILD

internal object MigrationSqlRenderer {
  fun render(
    diff: SchemaDiff,
    plan: TableMigrationPlan
  ): List<String> {
    val migrationStatements = arrayListOf<String>()
    val usedTemporaryNames = mutableSetOf<String>().apply {
      addAll(diff.previousTableNames)
      diff.currentTables.forEach { add(it.name) }
      diff.previousIndices.forEach { add(it.name) }
      diff.currentIndices.forEach { add(it.name) }
    }
    plan.indexDrops.forEach { index ->
      migrationStatements += dropMainSchemaIndexSql(index.name)
    }
    createNewTables(
      names = plan.prerequisiteNewTables,
      migrationStatements = migrationStatements
    )
    plan.simpleRenames.forEach { rename ->
      migrationStatements += "ALTER TABLE ${rename.previousName} RENAME TO ${rename.currentName}"
    }
    plan.directRebuilds.forEach { change ->
      change.migrate(
        temporaryTableName = temporaryTableName(
          tableName = change.to.structure.name,
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
    plan.indexCreates.forEach { index ->
      migrationStatements += index.structure.indexSql
    }
    return migrationStatements
  }

  private fun TableChange.migrate(
    temporaryTableName: String,
    migrationStatements: MutableList<String>
  ) {
    when (operation) {
      APPEND_COLUMNS -> to.structure.columns
        .asSequence()
        .drop(from.structure.columns.size)
        .forEach { column ->
          migrationStatements += "ALTER TABLE ${to.structure.name} ADD COLUMN ${column.schema}"
        }
      REBUILD -> {
        rebuild(
          temporaryTableName = temporaryTableName,
          renameStatements = migrationStatements,
          creationStatements = migrationStatements
        )
        migrationStatements += "DROP TABLE IF EXISTS $temporaryTableName"
      }
    }
  }

  private fun migrateDependentTables(
    rebuilds: List<TableChange>,
    usedNames: MutableSet<String>,
    migrationStatements: MutableList<String>
  ) {
    if (rebuilds.isEmpty()) return
    val tables = rebuilds.map { change ->
      RebuildTable(
        change = change,
        temporaryName = temporaryTableName(
          tableName = change.to.structure.name,
          usedNames = usedNames
        )
      )
    }

    val tableCreationStatements = arrayListOf<String>()
    tables.forEach { (change, temporaryName) ->
      change.rebuild(
        temporaryTableName = temporaryName,
        renameStatements = migrationStatements,
        creationStatements = tableCreationStatements
      )
    }
    migrationStatements += tableCreationStatements
    tables
      .asReversed()
      .forEach { table ->
        migrationStatements += "DROP TABLE IF EXISTS ${table.temporaryName}"
      }
  }

  private fun TableChange.rebuild(
    temporaryTableName: String,
    renameStatements: MutableList<String>,
    creationStatements: MutableList<String>
  ) {
    rowDeletion()?.let(renameStatements::add)
    renameStatements += "ALTER TABLE ${from.name} RENAME TO $temporaryTableName"
    creationStatements += to.structure.schema
    rowCopy(temporaryTableName)?.let(creationStatements::add)
  }

  private fun TableChange.rowDeletion() = when {
    !copiesRows -> "DELETE FROM ${from.name}"
    nullRowColumns.isNotEmpty() -> "DELETE FROM ${from.name} WHERE " +
        nullRowColumns.joinToString(separator = " OR ") { "${it.name} IS NULL" }
    else -> null
  }

  private fun TableChange.rowCopy(temporaryTableName: String) = when {
    !copiesRows -> null
    else -> {
      val destinationColumnNames = copiedColumns.joinToString(separator = ",") { it.current.name }
      val sourceValues = copiedColumns.joinToString(
        separator = ",",
        transform = ColumnCopy::sourceValue
      )
      "INSERT INTO ${to.structure.name} ($destinationColumnNames) SELECT $sourceValues FROM $temporaryTableName"
    }
  }

  private fun createNewTables(
    names: Iterable<TableSnapshot>,
    migrationStatements: MutableList<String>
  ) = names
    .asSequence()
    .map(TableSnapshot::structure)
    .map(TableStructure::schema)
    .forEach(migrationStatements::add)

  private fun temporaryTableName(
    tableName: String,
    usedNames: MutableSet<String>
  ): String {
    var temporaryName = "${tableName}_"
    while (usedNames.any { it.normalizedSqlIdentifier() == temporaryName.normalizedSqlIdentifier() }) {
      temporaryName += "_"
    }
    usedNames += temporaryName
    return temporaryName
  }
}

private data class RebuildTable(
  val change: TableChange,
  val temporaryName: String
)
