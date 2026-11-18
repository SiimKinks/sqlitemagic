package com.siimkinks.sqlitemagic.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
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

internal fun quotedMigrationTable(
  name: String,
  columns: List<ColumnStructure>,
  options: String = ""
) = TableStructure(
  name = name,
  schema = "CREATE TABLE IF NOT EXISTS ${quoteSqlIdentifier(name)} " +
      "(${columns.joinToString(transform = ColumnStructure::schema)})$options",
  columns = columns
)

internal fun migrationColumn(
  name: String,
  schema: String,
  onDeleteCascade: Boolean = false,
  notNull: Boolean = false,
  notNullDefault: String? = null
) = ColumnStructure(
  name = name,
  onDeleteCascade = onDeleteCascade,
  schema = schema,
  notNull = notNull,
  notNullDefault = notNullDefault
)

internal fun databaseStructure(vararg tableNames: String) = DatabaseStructure(
  tables = tableNames.associateWith(::tableStructure)
)

private fun tableStructure(name: String) = TableStructure(
  name = name,
  schema = """CREATE TABLE IF NOT EXISTS "$name" ("id" INTEGER PRIMARY KEY)""",
  columns = listOf(
    ColumnStructure(
      id = true,
      name = "id",
      sqlType = "INTEGER",
      schema = """"id" INTEGER PRIMARY KEY"""
    )
  )
)

internal data class MigrationRun(
  val migrationHappened: Boolean,
  val statements: List<String>,
  val warnings: List<String> = emptyList()
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
  .also {
    assertThat(it.migrationHappened).isTrue()
    assertThat(it.warnings).isEmpty()
  }
  .statements

internal fun runMigrationResult(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  temporaryDirectory: Path
): MigrationRun {
  val migrationFile = temporaryDirectory
    .resolve("src/debug/assets/1001.sql")
    .toFile()
  val warnings = mutableListOf<String>()
  val migrationHappened = MigrationsHandler(
    currentStructure = current,
    previousStructure = previous,
    outputStructureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile(),
    migrationOutputFile = migrationFile,
    warn = warnings::add
  ).migrate()
  return MigrationRun(
    migrationHappened = migrationHappened,
    statements = migrationFile
      .takeIf(File::isFile)
      ?.readLines()
      .orEmpty(),
    warnings = warnings
  )
}

internal fun recreatedFullTextIndexWarning(
  index: String,
  table: String
) = "Migration rebuilds full-text index $index from all rows of $table and loses the settings of the index it " +
    "replaces, such as 'automerge' or FTS5 'rank' set through raw SQL. Re-apply them at the end of the migration SQL."

internal fun migratedDatabase(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  rows: List<String>
): SQLiteConnection {
  val connection = BundledSQLiteDriver().open(":memory:")
  previous.tables.values.forEach { connection.execSQL(it.schema) }
  previous.indices.values.forEach { connection.execSQL(it.indexSql) }
  rows.forEach(connection::execSQL)
  if (current.enforcesForeignKeys) {
    connection.execSQL("PRAGMA foreign_keys = ON")
  }
  connection.execSQL("BEGIN EXCLUSIVE")
  planSchemaMigration(
    previous = previous,
    current = current
  ).statements.forEach(connection::execSQL)
  connection.execSQL("COMMIT")
  return connection
}

internal fun SQLiteConnection.schemaRows() = strings(
  "SELECT type || ' ' || name || ' ' || tbl_name || ' ' || IFNULL(sql, '') FROM sqlite_master ORDER BY name"
)

internal fun SQLiteConnection.strings(sql: String) = prepare(sql).use { statement ->
  buildList {
    while (statement.step()) {
      add(statement.getText(0))
    }
  }
}

internal fun SQLiteConnection.longs(sql: String) = prepare(sql).use { statement ->
  buildList {
    while (statement.step()) {
      add(statement.getLong(0))
    }
  }
}
