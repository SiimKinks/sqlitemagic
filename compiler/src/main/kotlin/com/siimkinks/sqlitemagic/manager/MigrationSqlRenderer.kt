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
    if ((plan.directRebuilds + plan.batchedRebuilds).any { it.operation == REBUILD && it.copiesMissingReferences }) {
      migrationStatements += "PRAGMA defer_foreign_keys = ON"
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
      prerequisiteNewTables = plan.batchedPrerequisiteNewTables,
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
      REBUILD -> rebuild(
        tables = listOf(
          RebuildTable(
            change = this,
            temporaryName = temporaryTableName
          )
        ),
        prerequisiteNewTables = emptyList(),
        migrationStatements = migrationStatements
      )
    }
  }

  private fun migrateDependentTables(
    rebuilds: List<TableChange>,
    prerequisiteNewTables: List<TableSnapshot>,
    usedNames: MutableSet<String>,
    migrationStatements: MutableList<String>
  ) {
    if (rebuilds.isEmpty()) return
    rebuild(
      tables = rebuilds.map { change ->
        RebuildTable(
          change = change,
          temporaryName = temporaryTableName(
            tableName = change.to.structure.name,
            usedNames = usedNames
          )
        )
      },
      prerequisiteNewTables = prerequisiteNewTables,
      migrationStatements = migrationStatements
    )
  }

  private fun rebuild(
    tables: List<RebuildTable>,
    prerequisiteNewTables: List<TableSnapshot>,
    migrationStatements: MutableList<String>
  ) {
    tables.forEach { (change, temporaryName) ->
      change
        .rowDeletion()
        ?.let(migrationStatements::add)
      migrationStatements += "ALTER TABLE ${quoteSqlIdentifier(change.from.name)} " +
          "RENAME TO ${quoteSqlIdentifier(temporaryName)}"
    }
    createNewTables(
      names = prerequisiteNewTables,
      migrationStatements = migrationStatements
    )
    tables.forEach { (change, temporaryName) ->
      val table = change.to.structure
      migrationStatements += table.schema
      if (table.columns.any(ColumnStructure::autoIncrement)) {
        migrationStatements += "UPDATE main.sqlite_sequence SET name = ${quoteSqlLiteral(table.name)} " +
            "WHERE name = ${quoteSqlLiteral(temporaryName)}"
      }
    }
    tables.forEach { (change, temporaryName) ->
      change
        .rowCopy(temporaryName)
        ?.let(migrationStatements::add)
    }
    tables.forEach { (change) ->
      change
        .missingReferenceDeletion()
        ?.let(migrationStatements::add)
    }
    tables
      .asReversed()
      .forEach { table ->
        migrationStatements += "DROP TABLE IF EXISTS ${quoteSqlIdentifier(table.temporaryName)}"
      }
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
      "INSERT INTO ${quoteSqlIdentifier(to.structure.name)} ($destinationColumnNames) SELECT $sourceValues " +
          "FROM ${quoteSqlIdentifier(temporaryTableName)}"
    }
  }

  private fun TableChange.missingReferenceDeletion() = when {
    !copiesMissingReferences -> null
    missingReferenceConditions.size == 1 ->
      "DELETE FROM ${quoteSqlIdentifier(to.structure.name)} WHERE ${missingReferenceConditions.single()}"
    else -> missingReferenceConditions.joinToString(
      separator = " OR ",
      prefix = "DELETE FROM ${quoteSqlIdentifier(to.structure.name)} WHERE "
    ) { "($it)" }
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
