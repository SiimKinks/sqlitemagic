package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path

internal fun migrationTable(
  name: String,
  columns: List<ColumnStructure>
) = TableStructure(
  name = name,
  schema = "CREATE TABLE IF NOT EXISTS $name (${columns.joinToString(transform = ColumnStructure::schema)})",
  columns = columns
)

internal fun migrationColumn(
  name: String,
  schema: String,
  onDeleteCascade: Boolean = false
) = ColumnStructure(
  name = name,
  onDeleteCascade = onDeleteCascade,
  schema = schema
)

internal fun databaseStructure(vararg tableNames: String) = DatabaseStructure(
  tables = tableNames.associateWith(::tableStructure)
)

private fun tableStructure(name: String) = TableStructure(
  name = name,
  schema = "CREATE TABLE IF NOT EXISTS $name (id INTEGER PRIMARY KEY)",
  columns = listOf(
    ColumnStructure(
      id = true,
      name = "id",
      sqlType = "INTEGER",
      schema = "id INTEGER PRIMARY KEY"
    )
  )
)

internal data class MigrationRun(
  val migrationHappened: Boolean,
  val statements: List<String>
)

internal fun runMigration(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  temporaryDirectory: Path
) = runMigrationResult(
  previous = previous,
  current = current,
  temporaryDirectory = temporaryDirectory
)
  .also { assertThat(it.migrationHappened).isTrue() }
  .statements

internal fun runMigrationResult(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  temporaryDirectory: Path
): MigrationRun {
  val migrationFile = temporaryDirectory
    .resolve("src/debug/assets/1001.sql")
    .toFile()
  val migrationHappened = MigrationsHandler(
    currentStructure = current,
    previousStructure = previous,
    outputStructureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile(),
    migrationOutputFile = migrationFile
  ).migrate()
  return MigrationRun(
    migrationHappened = migrationHappened,
    statements = migrationFile
      .takeIf(File::isFile)
      ?.readLines()
      .orEmpty()
  )
}
