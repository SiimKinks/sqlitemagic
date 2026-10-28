package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

internal class MigrationsHandlerTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `reads legacy structures and writes compatible table-only JSON`() {
    @Language("JSON")
    val legacyJson = """
      {
        "tables": {
          "books": {
            "name": "books",
            "schema": "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
            "columns": [{
              "id": true,
              "autoIncrement": false,
              "name": "id",
              "onDeleteCascade": false,
              "sqlType": "INTEGER",
              "schema": "id INTEGER PRIMARY KEY"
            }]
          }
        },
        "indices": {},
        "futureProperty": "ignored"
      }
    """
    val structure = DatabaseStructure(
      tables = mapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
          columns = listOf(
            ColumnStructure(
              id = true,
              autoIncrement = false,
              name = "id",
              onDeleteCascade = false,
              sqlType = "INTEGER",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      ),
      indices = emptyMap()
    )

    @Language("JSON")
    val expectedJson = """
      {"tables":{
        "books":{
          "name":"books",
          "schema":"CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
          "columns":[{
            "id":true,
            "autoIncrement":false,
            "name":"id",
            "onDeleteCascade":false,
            "sqlType":"INTEGER",
            "schema":"id INTEGER PRIMARY KEY",
            "notNull":false,
            "notNullDefault":null
          }]
        }
      },"indices":{},"temporaryTables":{},"temporaryIndices":{},"views":{},"temporaryViews":{}}
    """
      .trimIndent()
      .lines()
      .joinToString(
        separator = "",
        transform = String::trim
      )

    assertThat(DatabaseStructureJson.read(legacyJson)).isEqualTo(structure)
    assertThat(DatabaseStructureJson.write(structure)).isEqualTo(expectedJson)
  }

  @Test
  fun `rebuilds when an append also changes the table-level schema`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT '') WITHOUT ROWID",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT '') WITHOUT ROWID",
      "INSERT INTO books (id) SELECT id FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `creates a new table instead of renaming an unchanged table with the same columns`() {
    val columns = listOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = columns
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = columns
        ),
        "admins" to migrationTable(
          name = "admins",
          columns = columns
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "CREATE TABLE IF NOT EXISTS admins (id INTEGER PRIMARY KEY)"
    )
  }

  @Test
  fun `does not rename from an ambiguous duplicate column layout`() {
    val columns = listOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy_users" to migrationTable(
          name = "legacy_users",
          columns = columns
        ),
        "archived_users" to migrationTable(
          name = "archived_users",
          columns = columns
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = columns
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DROP TABLE IF EXISTS legacy_users",
          "DROP TABLE IF EXISTS archived_users",
          "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)"
        ),
        warnings = listOf(
          "Migration drops table legacy_users with its rows; if it was renamed to users, edit the migration SQL to " +
              "keep them.",
          "Migration drops table archived_users with its rows; if it was renamed to users, edit the migration SQL " +
              "to keep them."
        )
      )
    )
  }

  @Test
  fun `does not infer one old table as the rename source for multiple new tables`() {
    val columns = listOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy" to migrationTable(
          name = "legacy",
          columns = columns
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = columns
        ),
        "admins" to migrationTable(
          name = "admins",
          columns = columns
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DROP TABLE IF EXISTS legacy",
          "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
          "CREATE TABLE IF NOT EXISTS admins (id INTEGER PRIMARY KEY)"
        ),
        warnings = listOf(
          "Migration drops table legacy with its rows; if it was renamed to users or admins, edit the migration SQL " +
              "to keep them."
        )
      )
    )
  }

  @Test
  fun `rebuilds instead of appending a primary key column`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            ),
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (title TEXT DEFAULT '', id INTEGER PRIMARY KEY)",
      "INSERT INTO books (title) SELECT title FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `deletes all rows instead of copying when a rebuild has no mutual columns`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "legacy_title",
              schema = "legacy_title TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM books",
          "ALTER TABLE books RENAME TO books_",
          "CREATE TABLE IF NOT EXISTS books (title TEXT DEFAULT '')",
          "DROP TABLE IF EXISTS books_"
        ),
        warnings = listOf(
          "Migration deletes all rows of books because none of its column(s) remain: legacy_title. Edit the " +
              "migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `copies columns whose names only change case during a rebuild`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (DisplayName TEXT DEFAULT '')",
          columns = listOf(
            migrationColumn(
              name = "DisplayName",
              schema = "DisplayName TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (displayName TEXT DEFAULT '')",
          columns = listOf(
            migrationColumn(
              name = "displayName",
              schema = "displayName TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (displayName TEXT DEFAULT '')",
      "INSERT INTO books (displayName) SELECT DisplayName FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `chooses a free temporary name during a rebuild`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        ),
        "books_" to migrationTable(
          name = "books_",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        ),
        "books_" to migrationTable(
          name = "books_",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE books RENAME TO books__",
          "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
          "INSERT INTO books (id) SELECT id FROM books__",
          "DROP TABLE IF EXISTS books__"
        ),
        warnings = listOf(
          "Migration drops column(s) of books with their data: title. Edit the migration SQL to keep it."
        )
      )
    )
  }

  @Test
  fun `chooses a temporary name using SQLite case-insensitive table matching`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "Users" to migrationTable(
          name = "Users",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "Users_" to migrationTable(
          name = "Users_",
          columns = listOf(
            migrationColumn(
              name = "legacy",
              schema = "legacy TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE Users RENAME TO users__",
          "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
          "INSERT INTO users (id) SELECT id FROM users__",
          "DROP TABLE IF EXISTS users__",
          "DROP TABLE IF EXISTS Users_"
        ),
        warnings = listOf("Migration drops table Users_ with its rows. Edit the migration SQL to keep them.")
      )
    )
  }

  @Test
  fun `creates a new foreign-key parent before rebuilding its existing child`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE"
            ),
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        ),
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY)",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS children " +
          "(id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE, " +
          "label TEXT DEFAULT '')",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_"
    ).inOrder()
  }

  @Test
  fun `batches changed parents and cascading children before dropping either old table`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE"
            ),
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE"
            ),
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
          "INSERT INTO parents (id) SELECT id FROM parents_",
          "CREATE TABLE IF NOT EXISTS children " +
              "(id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE, " +
              "label TEXT DEFAULT '')",
          "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration drops column(s) of parents with their data: value. Edit the migration SQL to keep it.",
          "Migration drops column(s) of children with their data: value. Edit the migration SQL to keep it."
        )
      )
    )
  }

  @Test
  fun `renames related tables together without recreating the child`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy_parents" to migrationTable(
          name = "legacy_parents",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "legacy_children" to migrationTable(
          name = "legacy_children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES legacy_parents(id) ON DELETE CASCADE"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE legacy_parents RENAME TO parents",
      "ALTER TABLE legacy_children RENAME TO children"
    ).inOrder()
  }

  @Test
  fun `rebuilds a renamed table when its table-level schema changes`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (id INTEGER PRIMARY KEY)",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (id INTEGER PRIMARY KEY) WITHOUT ROWID",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE legacy RENAME TO renamed_",
      "CREATE TABLE IF NOT EXISTS renamed (id INTEGER PRIMARY KEY) WITHOUT ROWID",
      "INSERT INTO renamed (id) SELECT id FROM renamed_",
      "DROP TABLE IF EXISTS renamed_"
    ).inOrder()
  }

  @Test
  fun `recreates a table without its rows when it gains a without-rowid primary key`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "items" to migrationTable(
          name = "items",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "items" to TableStructure(
          name = "items",
          schema = "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, value TEXT DEFAULT '') WITHOUT ROWID",
          columns = listOf(
            migrationColumn(
              name = "id",
              schema = "id TEXT PRIMARY KEY"
            ).copy(
              id = true,
              sqlType = "TEXT"
            ),
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM items",
          "ALTER TABLE items RENAME TO items_",
          "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, value TEXT DEFAULT '') WITHOUT ROWID",
          "DROP TABLE IF EXISTS items_"
        ),
        warnings = listOf(
          "Migration deletes all rows of items because existing rows have no value for its new column(s): id. Edit " +
              "the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `drops a removed table and creates an unrelated new table with the same kind of key`() {
    val idColumn = ColumnStructure(
      id = true,
      autoIncrement = true,
      name = "id",
      sqlType = "INTEGER",
      schema = "id INTEGER PRIMARY KEY AUTOINCREMENT"
    )

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "notes" to migrationTable(
              name = "notes",
              columns = listOf(
                idColumn,
                migrationColumn(
                  name = "text",
                  schema = "text TEXT NOT NULL DEFAULT ''"
                )
              )
            )
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "tags" to migrationTable(
              name = "tags",
              columns = listOf(
                idColumn,
                migrationColumn(
                  name = "name",
                  schema = "name TEXT NOT NULL DEFAULT ''"
                ),
                migrationColumn(
                  name = "color",
                  schema = "color INTEGER NOT NULL DEFAULT 0"
                )
              )
            )
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DROP TABLE IF EXISTS notes",
          "CREATE TABLE IF NOT EXISTS tags " +
              "(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '', color INTEGER NOT NULL DEFAULT 0)"
        ),
        warnings = listOf(
          "Migration drops table notes with its rows; if it was renamed to tags, edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `drops and creates instead of inferring a rename when the columns also change`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    ).copy(
      id = true,
      sqlType = "INTEGER"
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy_users" to migrationTable(
          name = "legacy_users",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "name",
              schema = "name TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "users" to migrationTable(
          name = "users",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "name",
              schema = "name TEXT DEFAULT ''"
            ),
            migrationColumn(
              name = "email",
              schema = "email TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DROP TABLE IF EXISTS legacy_users",
          "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT DEFAULT '', email TEXT DEFAULT '')"
        ),
        warnings = listOf(
          "Migration drops table legacy_users with its rows; if it was renamed to users, edit the migration SQL to " +
              "keep them."
        )
      )
    )
  }

  @Test
  fun `drops and creates instead of inferring a rename when an idless column is renamed`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy_values" to migrationTable(
          name = "legacy_values",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            ).copy(sqlType = "TEXT")
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "values" to migrationTable(
          name = "values",
          columns = listOf(
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            ).copy(sqlType = "TEXT")
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DROP TABLE IF EXISTS legacy_values",
          "CREATE TABLE IF NOT EXISTS values (label TEXT DEFAULT '')"
        ),
        warnings = listOf(
          "Migration drops table legacy_values with its rows; if it was renamed to values, edit the migration SQL " +
              "to keep them."
        )
      )
    )
  }

  @Test
  fun `rebuilds instead of treating changed quoted whitespace as an equivalent rename`() {
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (value TEXT DEFAULT 'a  b')",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT 'a  b'"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (value TEXT DEFAULT 'a b')",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT 'a b'"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE legacy RENAME TO renamed_",
      "CREATE TABLE IF NOT EXISTS renamed (value TEXT DEFAULT 'a b')",
      "INSERT INTO renamed (value) SELECT value FROM renamed_",
      "DROP TABLE IF EXISTS renamed_"
    ).inOrder()
  }

  @ParameterizedTest(name = "quoted identifier {0}")
  @ValueSource(strings = ["\"", "`", "["])
  fun `does not replace a table name within a different quoted identifier`(opening: String) {
    val closing = when (opening) {
      "[" -> "]"
      else -> opening
    }
    val previousCollation = "$opening legacy value$closing"
    val currentCollation = "$opening renamed value$closing"
    val previous = DatabaseStructure(
      tables = mapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (value TEXT COLLATE $previousCollation)",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT COLLATE $previousCollation"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (value TEXT COLLATE $currentCollation)",
          columns = listOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT COLLATE $currentCollation"
            )
          )
        )
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE legacy RENAME TO renamed_",
      "CREATE TABLE IF NOT EXISTS renamed (value TEXT COLLATE $currentCollation)",
      "INSERT INTO renamed (value) SELECT value FROM renamed_",
      "DROP TABLE IF EXISTS renamed_"
    ).inOrder()
  }

  @Test
  fun `does not treat references text in a quoted default as a foreign key`() {
    val previousParents = migrationTable(
      name = "parents",
      columns = listOf(
        migrationColumn(
          name = "id",
          schema = "id INTEGER PRIMARY KEY"
        )
      )
    )
    val currentParents = previousParents.copy(
      schema = "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY) WITHOUT ROWID"
    )
    val notes = migrationTable(
      name = "notes",
      columns = listOf(
        migrationColumn(
          name = "value",
          schema = "value TEXT DEFAULT 'REFERENCES parents(id)'"
        )
      )
    )

    assertThat(
      runMigration(
        previous = DatabaseStructure(
          tables = mapOf(
            "parents" to previousParents,
            "notes" to notes
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "parents" to currentParents,
            "notes" to notes
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE parents RENAME TO parents_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY) WITHOUT ROWID",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "DROP TABLE IF EXISTS parents_"
    ).inOrder()
  }

  @ParameterizedTest(name = "quoted parent identifier {0}")
  @ValueSource(
    strings = [
      """"parents"""",
      "`parents`",
      "[parents]"
    ]
  )
  fun `rebuilds dependencies that use quoted foreign key identifiers`(referencedTableName: String) {
    val previousParent = migrationTable(
      name = "parents",
      columns = listOf(
        migrationColumn(
          name = "id",
          schema = "id INTEGER PRIMARY KEY"
        ),
        migrationColumn(
          name = "value",
          schema = "value TEXT DEFAULT ''"
        )
      )
    )
    val currentParent = migrationTable(
      name = "parents",
      columns = listOf(
        migrationColumn(
          name = "id",
          schema = "id INTEGER PRIMARY KEY"
        ),
        migrationColumn(
          name = "label",
          schema = "label TEXT DEFAULT ''"
        )
      )
    )
    val child = migrationTable(
      name = "children",
      columns = listOf(
        migrationColumn(
          name = "id",
          schema = "id INTEGER PRIMARY KEY"
        ),
        migrationColumn(
          name = "parent_id",
          onDeleteCascade = true,
          schema = "parent_id INTEGER DEFAULT 0 REFERENCES $referencedTableName(id) ON DELETE CASCADE"
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "parents" to previousParent,
            "children" to child
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "parents" to currentParent,
            "children" to child
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
          "INSERT INTO parents (id) SELECT id FROM parents_",
          "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, " +
              "parent_id INTEGER DEFAULT 0 REFERENCES $referencedTableName(id) ON DELETE CASCADE)",
          "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration drops column(s) of parents with their data: value. Edit the migration SQL to keep it."
        )
      )
    )
  }

  @Test
  fun `matches foreign key dependencies using SQLite case-insensitive table names`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val child = migrationTable(
      name = "children",
      columns = listOf(
        idColumn,
        migrationColumn(
          name = "parent_id",
          schema = "parent_id INTEGER DEFAULT 0 REFERENCES USERS(id) ON DELETE CASCADE",
          onDeleteCascade = true
        )
      )
    )

    assertThat(
      runMigration(
        previous = DatabaseStructure(
          tables = mapOf(
            "Users" to migrationTable(
              name = "Users",
              columns = listOf(idColumn)
            ),
            "children" to child
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "users" to migrationTable(
              name = "users",
              columns = listOf(idColumn)
            ),
            "children" to child
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE Users RENAME TO users_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
      "INSERT INTO users (id) SELECT id FROM users_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, " +
          "parent_id INTEGER DEFAULT 0 REFERENCES USERS(id) ON DELETE CASCADE)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS users_"
    ).inOrder()
  }

  @Test
  fun `batches an old foreign key dependency that is removed by the migration`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE",
              onDeleteCascade = true
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            idColumn,
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0"
            )
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
          "INSERT INTO parents (id) SELECT id FROM parents_",
          "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0)",
          "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration drops column(s) of parents with their data: value. Edit the migration SQL to keep it."
        )
      )
    )
  }

  @Test
  fun `orders a deep removed foreign-key chain without recursive traversal`() {
    val tableCount = 2_000
    val tables = (0 until tableCount)
      .map { index ->
        migrationTable(
          name = "table$index",
          columns = listOfNotNull(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "parent_id",
              onDeleteCascade = true,
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES table${index - 1}(id) ON DELETE CASCADE"
            ).takeIf { index > 0 }
          )
        )
      }
      .associateBy(TableStructure::name)

    val droppedTableNames = (tableCount - 1 downTo 0).map { "table$it" }

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(tables = tables),
        current = DatabaseStructure(),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = droppedTableNames.map { "DROP TABLE IF EXISTS $it" },
        warnings = droppedTableNames.map { tableName ->
          "Migration drops table $tableName with its rows. Edit the migration SQL to keep them."
        }
      )
    )
  }

  @Test
  fun `does not infer a rename chain through an occupied table name`() {
    val alpha = migrationTable(
      name = "alpha",
      columns = listOf(
        migrationColumn(
          name = "alpha_id",
          schema = "alpha_id INTEGER PRIMARY KEY"
        )
      )
    )
    val beta = migrationTable(
      name = "beta",
      columns = listOf(
        migrationColumn(
          name = "beta_id",
          schema = "beta_id TEXT PRIMARY KEY"
        )
      )
    )
    val previous = DatabaseStructure(
      tables = mapOf(
        "alpha" to alpha,
        "beta" to beta
      )
    )
    val current = DatabaseStructure(
      tables = mapOf(
        "beta" to migrationTable(
          name = "beta",
          columns = alpha.columns
        ),
        "gamma" to migrationTable(
          name = "gamma",
          columns = beta.columns
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM beta",
          "ALTER TABLE beta RENAME TO beta_",
          "CREATE TABLE IF NOT EXISTS beta (alpha_id INTEGER PRIMARY KEY)",
          "DROP TABLE IF EXISTS beta_",
          "DROP TABLE IF EXISTS alpha",
          "CREATE TABLE IF NOT EXISTS gamma (beta_id TEXT PRIMARY KEY)"
        ),
        warnings = listOf(
          "Migration deletes all rows of beta because none of its column(s) remain: beta_id. Edit the migration " +
              "SQL to keep them.",
          "Migration drops table alpha with its rows; if it was renamed to beta, edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `rebuilds both tables in place instead of inferring a swap between their names`() {
    val alpha = migrationTable(
      name = "alpha",
      columns = listOf(
        migrationColumn(
          name = "alpha_id",
          schema = "alpha_id INTEGER PRIMARY KEY"
        )
      )
    )
    val beta = migrationTable(
      name = "beta",
      columns = listOf(
        migrationColumn(
          name = "beta_id",
          schema = "beta_id TEXT PRIMARY KEY"
        )
      )
    )

    assertThat(
      runMigrationResult(
        current = DatabaseStructure(
          tables = mapOf(
            "alpha" to migrationTable(
              name = "alpha",
              columns = beta.columns
            ),
            "beta" to migrationTable(
              name = "beta",
              columns = alpha.columns
            )
          )
        ),
        previous = DatabaseStructure(
          tables = mapOf(
            "alpha" to alpha,
            "beta" to beta
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM alpha",
          "ALTER TABLE alpha RENAME TO alpha_",
          "CREATE TABLE IF NOT EXISTS alpha (beta_id TEXT PRIMARY KEY)",
          "DROP TABLE IF EXISTS alpha_",
          "DELETE FROM beta",
          "ALTER TABLE beta RENAME TO beta_",
          "CREATE TABLE IF NOT EXISTS beta (alpha_id INTEGER PRIMARY KEY)",
          "DROP TABLE IF EXISTS beta_"
        ),
        warnings = listOf(
          "Migration deletes all rows of alpha because none of its column(s) remain: alpha_id. Edit the migration " +
              "SQL to keep them.",
          "Migration deletes all rows of beta because none of its column(s) remain: beta_id. Edit the migration " +
              "SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `batches transitive foreign key dependents`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previousParents = migrationTable(
      name = "parents",
      columns = listOf(
        idColumn,
        migrationColumn(
          name = "value",
          schema = "value TEXT DEFAULT ''"
        )
      )
    )
    val currentParents = migrationTable(
      name = "parents",
      columns = listOf(
        idColumn,
        migrationColumn(
          name = "label",
          schema = "label TEXT DEFAULT ''"
        )
      )
    )
    val children = migrationTable(
      name = "children",
      columns = listOf(
        idColumn,
        migrationColumn(
          name = "parent_id",
          onDeleteCascade = true,
          schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE"
        )
      )
    )
    val grandchildren = migrationTable(
      name = "grandchildren",
      columns = listOf(
        idColumn,
        migrationColumn(
          name = "child_id",
          onDeleteCascade = true,
          schema = "child_id INTEGER DEFAULT 0 REFERENCES children(id) ON DELETE CASCADE"
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "parents" to previousParents,
            "children" to children,
            "grandchildren" to grandchildren
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "parents" to currentParents,
            "children" to children,
            "grandchildren" to grandchildren
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "ALTER TABLE grandchildren RENAME TO grandchildren_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
          "INSERT INTO parents (id) SELECT id FROM parents_",
          "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, " +
              "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE)",
          "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
          "CREATE TABLE IF NOT EXISTS grandchildren (id INTEGER PRIMARY KEY, " +
              "child_id INTEGER DEFAULT 0 REFERENCES children(id) ON DELETE CASCADE)",
          "INSERT INTO grandchildren (id,child_id) SELECT id,child_id FROM grandchildren_",
          "DROP TABLE IF EXISTS grandchildren_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration drops column(s) of parents with their data: value. Edit the migration SQL to keep it."
        )
      )
    )
  }
}
