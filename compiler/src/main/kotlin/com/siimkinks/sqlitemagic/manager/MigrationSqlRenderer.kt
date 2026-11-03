package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.manager.TableMigrationOperation.APPEND_COLUMNS
import com.siimkinks.sqlitemagic.manager.TableMigrationOperation.REBUILD
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier

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
      migrationStatements += "ALTER TABLE ${quoteSqlIdentifier(rename.previousName)} " +
          "RENAME TO ${quoteSqlIdentifier(rename.currentName)}"
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
      migrationStatements += "DROP TABLE IF EXISTS ${quoteSqlIdentifier(table.name)}"
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
          migrationStatements += "ALTER TABLE ${quoteSqlIdentifier(to.structure.name)} ADD COLUMN ${column.schema}"
        }
      REBUILD -> {
        rebuild(
          temporaryTableName = temporaryTableName,
          renameStatements = migrationStatements,
          creationStatements = migrationStatements
        )
        migrationStatements += "DROP TABLE IF EXISTS ${quoteSqlIdentifier(temporaryTableName)}"
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
        migrationStatements += "DROP TABLE IF EXISTS ${quoteSqlIdentifier(table.temporaryName)}"
      }
  }

  private fun TableChange.rebuild(
    temporaryTableName: String,
    renameStatements: MutableList<String>,
    creationStatements: MutableList<String>
  ) {
    rowDeletion()?.let(renameStatements::add)
    renameStatements += "ALTER TABLE ${quoteSqlIdentifier(from.name)} " +
        "RENAME TO ${quoteSqlIdentifier(temporaryTableName)}"
    creationStatements += to.structure.schema
    if (to.structure.columns.any(ColumnStructure::autoIncrement)) {
      creationStatements += "UPDATE main.sqlite_sequence SET name = ${quoteSqlLiteral(to.structure.name)} " +
          "WHERE name = ${quoteSqlLiteral(temporaryTableName)}"
    }
    rowCopy(temporaryTableName)?.let(creationStatements::add)
  }

  private fun TableChange.rowDeletion() = when {
    !copiesRows -> "DELETE FROM ${quoteSqlIdentifier(from.name)}"
    nullRowColumns.isNotEmpty() -> "DELETE FROM ${quoteSqlIdentifier(from.name)} WHERE " +
        nullRowColumns.joinToString(separator = " OR ") { "${quoteSqlIdentifier(it.name)} IS NULL" }
    else -> null
  }

  private fun TableChange.rowCopy(temporaryTableName: String) = when {
    !copiesRows -> null
    else -> {
      val destinationColumnNames = copiedColumns.joinToString(separator = ",") { quoteSqlIdentifier(it.current.name) }
      val sourceValues = copiedColumns.joinToString(
        separator = ",",
        transform = ColumnCopy::sourceValue
      )
      val referenceConditions = copiedColumns.mapNotNull(ColumnCopy::existingReferenceCondition)
      val referenceFilter = when (referenceConditions.size) {
        0 -> ""
        1 -> " WHERE ${referenceConditions.single()}"
        else -> referenceConditions.joinToString(
          separator = " AND ",
          prefix = " WHERE "
        ) { "($it)" }
      }
      "INSERT INTO ${quoteSqlIdentifier(to.structure.name)} ($destinationColumnNames) SELECT $sourceValues " +
          "FROM ${quoteSqlIdentifier(temporaryTableName)}$referenceFilter"
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

private fun quoteSqlLiteral(value: String) = "'${value.replace(oldValue = "'", newValue = "''")}'"

private data class RebuildTable(
  val change: TableChange,
  val temporaryName: String
)
