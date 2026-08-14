package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.transformer.DefaultTransformerCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionStep
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

internal class MigrationsHandlerTest : ProcessingStepsTest {
  override val processingSteps = ::migrationCollectionSteps

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
      tables = linkedMapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
          columns = arrayListOf(
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
      indices = linkedMapOf()
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
            "schema":"id INTEGER PRIMARY KEY"
          }]
        }
      },"indices":{}}
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
      tables = linkedMapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY)",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT '') WITHOUT ROWID",
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT '') WITHOUT ROWID",
      "INSERT INTO books (id) SELECT id FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `ignores index-only changes until index migration is implemented`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val table = bookStructure(
      columns = arrayListOf(
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
    val previous = DatabaseStructure(
      tables = linkedMapOf("books" to table),
      indices = linkedMapOf(
        "books_id" to IndexStructure(
          name = "books_id",
          indexSql = "CREATE INDEX IF NOT EXISTS books_id ON books (id)",
          forTable = "books"
        )
      )
    )
    val current = DatabaseStructure(tables = linkedMapOf("books" to table))

    assertThat(
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    ).isFalse()
    assertThat(migrationFile.exists()).isFalse()
    assertThat(DatabaseStructureJson.read(structureFile)).isEqualTo(current)
  }

  @Test
  fun `creates a new table instead of renaming an unchanged table with the same columns`() {
    val columns = arrayListOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = columns
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = ArrayList(columns)
        ),
        "admins" to migrationTable(
          name = "admins",
          columns = ArrayList(columns)
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "CREATE TABLE IF NOT EXISTS admins (id INTEGER PRIMARY KEY)"
    )
  }

  @Test
  fun `does not rename from an ambiguous duplicate column layout`() {
    val columns = arrayListOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy_users" to migrationTable(
          name = "legacy_users",
          columns = ArrayList(columns)
        ),
        "archived_users" to migrationTable(
          name = "archived_users",
          columns = ArrayList(columns)
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = ArrayList(columns)
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "DROP TABLE IF EXISTS legacy_users",
      "DROP TABLE IF EXISTS archived_users",
      "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)"
    ).inOrder()
  }

  @Test
  fun `does not infer one old table as the rename source for multiple new tables`() {
    val columns = arrayListOf(
      migrationColumn(
        name = "id",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy" to migrationTable(
          name = "legacy",
          columns = columns
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = ArrayList(columns)
        ),
        "admins" to migrationTable(
          name = "admins",
          columns = ArrayList(columns)
        )
      )
    )
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()

    assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists()).isFalse()
    assertThat(structureFile.exists()).isFalse()
  }

  @Test
  fun `rebuilds instead of appending a primary key column`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (title TEXT DEFAULT '', id INTEGER PRIMARY KEY)",
      "INSERT INTO books (title) SELECT title FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `rebuilds instead of appending a unique column`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
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
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "isbn",
              schema = "isbn TEXT UNIQUE"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, isbn TEXT UNIQUE)",
      "INSERT INTO books (id) SELECT id FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `rebuilds instead of appending a column with a non-constant default`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "created_at",
              schema = "created_at TEXT DEFAULT CURRENT_TIMESTAMP"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, created_at TEXT DEFAULT CURRENT_TIMESTAMP)",
      "INSERT INTO books (id) SELECT id FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `rebuilds instead of appending a column with a parenthesized default`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to bookStructure(
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to bookStructure(
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            ),
            migrationColumn(
              name = "priority",
              schema = "priority INTEGER DEFAULT (1)"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, priority INTEGER DEFAULT (1))",
      "INSERT INTO books (id) SELECT id FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `omits the copy statement when a rebuild has no mutual columns`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "legacy_title",
              schema = "legacy_title TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (title TEXT DEFAULT '')",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `copies columns whose names only change case during a rebuild`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (DisplayName TEXT DEFAULT '')",
          columns = arrayListOf(
            migrationColumn(
              name = "DisplayName",
              schema = "DisplayName TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to TableStructure(
          name = "books",
          schema = "CREATE TABLE IF NOT EXISTS books (displayName TEXT DEFAULT '')",
          columns = arrayListOf(
            migrationColumn(
              name = "displayName",
              schema = "displayName TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (displayName TEXT DEFAULT '')",
      "INSERT INTO books (displayName) SELECT DisplayName FROM books_",
      "DROP TABLE IF EXISTS books_"
    ).inOrder()
  }

  @Test
  fun `chooses a free temporary name during a rebuild`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
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
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
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
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE books RENAME TO books__",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
      "INSERT INTO books (id) SELECT id FROM books__",
      "DROP TABLE IF EXISTS books__"
    ).inOrder()
  }

  @Test
  fun `chooses a temporary name using SQLite case-insensitive table matching`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "Users" to migrationTable(
          name = "Users",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "Users_" to migrationTable(
          name = "Users_",
          columns = arrayListOf(
            migrationColumn(
              name = "legacy",
              schema = "legacy TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE Users RENAME TO users__",
      "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
      "INSERT INTO users (id) SELECT id FROM users__",
      "DROP TABLE IF EXISTS users__",
      "DROP TABLE IF EXISTS Users_"
    ).inOrder()
  }

  @Test
  fun `creates a new foreign-key parent before rebuilding its existing child`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
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
      tables = linkedMapOf(
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
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
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
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
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
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
          columns = arrayListOf(
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
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
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
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
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
    ).inOrder()
  }

  @Test
  fun `renames related tables together without recreating the child`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy_parents" to migrationTable(
          name = "legacy_parents",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "legacy_children" to migrationTable(
          name = "legacy_children",
          columns = arrayListOf(
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
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE legacy_parents RENAME TO parents",
      "ALTER TABLE legacy_children RENAME TO children"
    ).inOrder()
  }

  @Test
  fun `rebuilds a renamed table when its table-level schema changes`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (id INTEGER PRIMARY KEY)",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (id INTEGER PRIMARY KEY) WITHOUT ROWID",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE legacy RENAME TO renamed_",
      "CREATE TABLE IF NOT EXISTS renamed (id INTEGER PRIMARY KEY) WITHOUT ROWID",
      "INSERT INTO renamed (id) SELECT id FROM renamed_",
      "DROP TABLE IF EXISTS renamed_"
    ).inOrder()
  }

  @Test
  fun `does not emit a migration that adds an implicit without-rowid primary key`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "items" to migrationTable(
          name = "items",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "items" to TableStructure(
          name = "items",
          schema = "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, value TEXT DEFAULT '') WITHOUT ROWID",
          columns = arrayListOf(
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

    assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists()).isFalse()
    assertThat(structureFile.exists()).isFalse()
  }

  @Test
  fun `refuses to infer a table rename when the columns also change`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    ).copy(
      id = true,
      sqlType = "INTEGER"
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy_users" to migrationTable(
          name = "legacy_users",
          columns = arrayListOf(
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
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = arrayListOf(
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

    assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists()).isFalse()
    assertThat(structureFile.exists()).isFalse()
  }

  @Test
  fun `refuses to infer a table rename when an idless column is renamed`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy_values" to migrationTable(
          name = "legacy_values",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            ).copy(sqlType = "TEXT")
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "values" to migrationTable(
          name = "values",
          columns = arrayListOf(
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            ).copy(sqlType = "TEXT")
          )
        )
      )
    )

    assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists()).isFalse()
    assertThat(structureFile.exists()).isFalse()
  }

  @Test
  fun `rebuilds instead of treating changed quoted whitespace as an equivalent rename`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (value TEXT DEFAULT 'a  b')",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT 'a  b'"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (value TEXT DEFAULT 'a b')",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT 'a b'"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
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
      tables = linkedMapOf(
        "legacy" to TableStructure(
          name = "legacy",
          schema = "CREATE TABLE IF NOT EXISTS legacy (value TEXT COLLATE $previousCollation)",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT COLLATE $previousCollation"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "renamed" to TableStructure(
          name = "renamed",
          schema = "CREATE TABLE IF NOT EXISTS renamed (value TEXT COLLATE $currentCollation)",
          columns = arrayListOf(
            migrationColumn(
              name = "value",
              schema = "value TEXT COLLATE $currentCollation"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
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
      columns = arrayListOf(
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
      columns = arrayListOf(
        migrationColumn(
          name = "value",
          schema = "value TEXT DEFAULT 'REFERENCES parents(id)'"
        )
      )
    )

    assertThat(
      runMigration(
        previous = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to previousParents,
            "notes" to notes
          )
        ),
        current = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to currentParents,
            "notes" to notes
          )
        )
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
      "\"parents\"",
      "`parents`",
      "[parents]"
    ]
  )
  fun `rebuilds dependencies that use quoted foreign key identifiers`(referencedTableName: String) {
    val previousParent = migrationTable(
      name = "parents",
      columns = arrayListOf(
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
      columns = arrayListOf(
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
      columns = arrayListOf(
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
      runMigration(
        previous = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to previousParent,
            "children" to child
          )
        ),
        current = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to currentParent,
            "children" to child
          )
        )
      )
    ).containsExactly(
      "ALTER TABLE parents RENAME TO parents_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES $referencedTableName(id) ON DELETE CASCADE)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS parents_"
    ).inOrder()
  }

  @Test
  fun `rebuilds a case-only table rename through a distinct temporary name`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "Users" to migrationTable(
          name = "Users",
          columns = arrayListOf(idColumn)
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "users" to migrationTable(
          name = "users",
          columns = arrayListOf(idColumn)
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE Users RENAME TO users_",
      "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
      "INSERT INTO users (id) SELECT id FROM users_",
      "DROP TABLE IF EXISTS users_"
    ).inOrder()
  }

  @Test
  fun `matches foreign key dependencies using SQLite case-insensitive table names`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val child = migrationTable(
      name = "children",
      columns = arrayListOf(
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
          tables = linkedMapOf(
            "Users" to migrationTable(
              name = "Users",
              columns = arrayListOf(idColumn)
            ),
            "children" to child
          )
        ),
        current = DatabaseStructure(
          tables = linkedMapOf(
            "users" to migrationTable(
              name = "users",
              columns = arrayListOf(idColumn)
            ),
            "children" to child
          )
        )
      )
    ).containsExactly(
      "ALTER TABLE Users RENAME TO users_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)",
      "INSERT INTO users (id) SELECT id FROM users_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES USERS(id) ON DELETE CASCADE)",
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
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
            idColumn,
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
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
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
            idColumn,
            migrationColumn(
              name = "label",
              schema = "label TEXT DEFAULT ''"
            )
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
            idColumn,
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0"
            )
          )
        )
      )
    )

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE parents RENAME TO parents_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS parents_"
    ).inOrder()
  }

  @Test
  fun `drops removed foreign key children before their parents`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(idColumn)
        ),
        "children" to migrationTable(
          name = "children",
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = DatabaseStructure())).containsExactly(
      "DROP TABLE IF EXISTS children",
      "DROP TABLE IF EXISTS parents"
    ).inOrder()
  }

  @Test
  fun `orders a deep removed foreign-key chain without recursive traversal`() {
    val tableCount = 2_000
    val tables = linkedMapOf<String, TableStructure>()
    repeat(tableCount) { index ->
      val tableName = "table$index"
      val columns = arrayListOf(
        migrationColumn(
          name = "id",
          schema = "id INTEGER PRIMARY KEY"
        )
      )
      if (index > 0) {
        columns += migrationColumn(
          name = "parent_id",
          onDeleteCascade = true,
          schema = "parent_id INTEGER DEFAULT 0 REFERENCES table${index - 1}(id) ON DELETE CASCADE"
        )
      }
      tables[tableName] = migrationTable(
        name = tableName,
        columns = columns
      )
    }

    val statements = runMigration(
      previous = DatabaseStructure(tables = tables),
      current = DatabaseStructure()
    )

    assertThat(statements).hasSize(tableCount)
    assertThat(statements.first()).isEqualTo("DROP TABLE IF EXISTS table${tableCount - 1}")
    assertThat(statements.last()).isEqualTo("DROP TABLE IF EXISTS table0")
  }

  @Test
  fun `rejects a possible rename chain through an occupied table name`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val alpha = migrationTable(
      name = "alpha",
      columns = arrayListOf(
        migrationColumn(
          name = "alpha_id",
          schema = "alpha_id INTEGER PRIMARY KEY"
        )
      )
    )
    val beta = migrationTable(
      name = "beta",
      columns = arrayListOf(
        migrationColumn(
          name = "beta_id",
          schema = "beta_id TEXT PRIMARY KEY"
        )
      )
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "alpha" to alpha,
        "beta" to beta
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "beta" to alpha.copy(name = "beta", schema = alpha.schema.replace("alpha", "beta")),
        "gamma" to beta.copy(name = "gamma", schema = beta.schema.replace("beta", "gamma"))
      )
    )

    val exception = assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }

    assertThat(exception).hasMessageThat().contains("occupied table name")
    assertThat(structureFile.exists()).isFalse()
    assertThat(migrationFile.exists()).isFalse()
  }

  @Test
  fun `rejects a possible swap between occupied table names`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val alpha = migrationTable(
      name = "alpha",
      columns = arrayListOf(
        migrationColumn(
          name = "alpha_id",
          schema = "alpha_id INTEGER PRIMARY KEY"
        )
      )
    )
    val beta = migrationTable(
      name = "beta",
      columns = arrayListOf(
        migrationColumn(
          name = "beta_id",
          schema = "beta_id TEXT PRIMARY KEY"
        )
      )
    )

    val exception = assertThrows<IllegalStateException> {
      MigrationsHandler(
        currentStructure = DatabaseStructure(
          tables = linkedMapOf(
            "alpha" to beta.copy(name = "alpha", schema = beta.schema.replace("beta", "alpha")),
            "beta" to alpha.copy(name = "beta", schema = alpha.schema.replace("alpha", "beta"))
          )
        ),
        previousStructure = DatabaseStructure(
          tables = linkedMapOf(
            "alpha" to alpha,
            "beta" to beta
          )
        ),
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }

    assertThat(exception).hasMessageThat().contains("occupied table name")
    assertThat(structureFile.exists()).isFalse()
    assertThat(migrationFile.exists()).isFalse()
  }

  @Test
  fun `does not leave a migration asset when publishing the structure snapshot fails`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    check(structureFile.parentFile?.mkdirs() == true)
    check(structureFile.mkdir())
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(idColumn)
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            idColumn,
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      )
    )

    assertThrows<Exception> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(migrationFile.exists()).isFalse()
  }

  @Test
  fun `removes a stale migration asset when no migration is needed`() {
    val structure = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile().apply {
      parentFile.mkdirs()
      writeText("stale migration")
    }

    assertThat(
      MigrationsHandler(
        currentStructure = structure,
        previousStructure = structure,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    ).isFalse()
    assertThat(migrationFile.exists()).isFalse()
  }

  @Test
  fun `restores the structure snapshot when publishing the migration fails`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
            migrationColumn(
              name = "id",
              schema = "id INTEGER PRIMARY KEY"
            )
          )
        )
      )
    )
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "books" to migrationTable(
          name = "books",
          columns = arrayListOf(
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
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = previous
    )
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    check(migrationFile.mkdirs())

    assertThrows<Exception> {
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    }
    assertThat(DatabaseStructureJson.read(structureFile)).isEqualTo(previous)
  }

  @Test
  fun `rebuilds inbound foreign key tables before dropping the old parent`() {
    val previous = DatabaseStructure(
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
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
          columns = arrayListOf(
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
    val current = DatabaseStructure(
      tables = linkedMapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = arrayListOf(
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
          columns = arrayListOf(
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

    assertThat(runMigration(previous = previous, current = current)).containsExactly(
      "ALTER TABLE parents RENAME TO parents_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS parents_"
    ).inOrder()
  }

  @Test
  fun `batches transitive foreign key dependents`() {
    val idColumn = migrationColumn(
      name = "id",
      schema = "id INTEGER PRIMARY KEY"
    )
    val previousParents = migrationTable(
      name = "parents",
      columns = arrayListOf(
        idColumn,
        migrationColumn(
          name = "value",
          schema = "value TEXT DEFAULT ''"
        )
      )
    )
    val currentParents = migrationTable(
      name = "parents",
      columns = arrayListOf(
        idColumn,
        migrationColumn(
          name = "label",
          schema = "label TEXT DEFAULT ''"
        )
      )
    )
    val children = migrationTable(
      name = "children",
      columns = arrayListOf(
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
      columns = arrayListOf(
        idColumn,
        migrationColumn(
          name = "child_id",
          onDeleteCascade = true,
          schema = "child_id INTEGER DEFAULT 0 REFERENCES children(id) ON DELETE CASCADE"
        )
      )
    )

    assertThat(
      runMigration(
        previous = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to previousParents,
            "children" to children,
            "grandchildren" to grandchildren
          )
        ),
        current = DatabaseStructure(
          tables = linkedMapOf(
            "parents" to currentParents,
            "children" to children,
            "grandchildren" to grandchildren
          )
        )
      )
    ).containsExactly(
      "ALTER TABLE parents RENAME TO parents_",
      "ALTER TABLE children RENAME TO children_",
      "ALTER TABLE grandchildren RENAME TO grandchildren_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, label TEXT DEFAULT '')",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "CREATE TABLE IF NOT EXISTS grandchildren (id INTEGER PRIMARY KEY, child_id INTEGER DEFAULT 0 REFERENCES children(id) ON DELETE CASCADE)",
      "INSERT INTO grandchildren (id,child_id) SELECT id,child_id FROM grandchildren_",
      "DROP TABLE IF EXISTS grandchildren_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS parents_"
    ).inOrder()
  }

  @Test
  fun `does not publish a submodule snapshot when migration generation fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val compilation = SqliteMagicCompilation
      .compile(
        submoduleWithoutRowIdDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    val currentStructure = DatabaseStructure.from(orderedTables)
    val currentTable = currentStructure.tables.getValue("feature_items")
    val previousTable = currentTable.copy(
      schema = "CREATE TABLE IF NOT EXISTS feature_items (name TEXT DEFAULT '')",
      columns = arrayListOf(currentTable.columns.single { column -> column.name == "name" })
    )
    val previousStructure = DatabaseStructure(
      tables = linkedMapOf("feature_items" to previousTable)
    )
    DatabaseStructureJson.write(
      file = submoduleDirectory.resolve("db/latest.struct").toFile(),
      structure = previousStructure
    )

    val outcome = DebugMigrationCoordinator(
      configuration = DebugMigrationConfiguration.from(compilation.environment.options),
      logger = compilation.environment.logger
    ).handle(
      database = database,
      orderedTables = orderedTables
    )

    assertThat(outcome.databaseVersionOverride).isNull()
    assertThat(DatabaseStructureJson.read(submoduleDirectory.resolve("db/latest.struct").toFile()))
      .isEqualTo(previousStructure)
    assertThat(Files.exists(mainDirectory.resolve("db/latest_feature.struct"))).isFalse()
    assertThat(Files.exists(mainDirectory.resolve("db/feature.changed"))).isFalse()
    assertThat(Files.exists(submoduleDirectory.resolve("src/debug/assets/Feature1001.sql"))).isFalse()
  }

  private fun debugMigrationOptions(
    projectDirectory: Path,
    mainModuleDirectory: Path? = null
  ) = buildMap {
    put(key = "sqlitemagic.migrate.debug", value = "true")
    put(key = "sqlitemagic.project.dir", value = projectDirectory.toString())
    put(key = "sqlitemagic.variant.name", value = "debug")
    put(key = "sqlitemagic.variant.debug", value = "true")
    mainModuleDirectory?.let { directory ->
      put(key = "sqlitemagic.main.module.path", value = directory.toString())
    }
  }

  private fun submoduleWithoutRowIdDatabase() = SourceFile.kotlin(
    name = "FeatureWithoutRowIdDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID

      @SubmoduleDatabase("feature")
      class FeatureDatabase

      @Table(value = "feature_items", options = [WITHOUT_ROWID])
      data class FeatureItem(
        @Id val id: String,
        val name: String
      )
    """
  )

  private fun runMigration(
    previous: DatabaseStructure,
    current: DatabaseStructure
  ): List<String> {
    val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
    assertThat(
      MigrationsHandler(
        currentStructure = current,
        previousStructure = previous,
        outputStructureFile = structureFile,
        migrationOutputFile = migrationFile
      ).migrate()
    ).isTrue()
    return migrationFile.readLines()
  }
}

private fun migrationCollectionSteps(environment: Environment): List<ProcessingStep> = listOf(
  DefaultTransformerCollectionStep(environment),
  DatabaseConfigurationCollectionStep(environment),
  TransformerCollectionStep(environment),
  ModelCollectionStep(environment)
)

private fun bookStructure(columns: ArrayList<ColumnStructure>) = migrationTable(
  name = "books",
  columns = columns
)

private fun migrationTable(
  name: String,
  columns: ArrayList<ColumnStructure>
) = TableStructure(
  name = name,
  schema = "CREATE TABLE IF NOT EXISTS $name (${columns.joinToString(transform = ColumnStructure::schema)})",
  columns = columns
)

private fun migrationColumn(
  name: String,
  schema: String,
  onDeleteCascade: Boolean = false
) = ColumnStructure(
  name = name,
  onDeleteCascade = onDeleteCascade,
  schema = schema
)
