package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class IndexMigrationContractTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `creates an index added without table changes`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable())
    )
    val current = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      """CREATE INDEX IF NOT EXISTS main."books_id" ON "books" ("id")"""
    )
  }

  @Test
  fun `drops an index removed without table changes`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf("books" to persistentTable())
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      """DROP INDEX IF EXISTS main."books_id""""
    )
  }

  @Test
  fun `replaces a same-name index when its SQL changes around table work`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          columns = listOf("id")
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          columns = listOf("title")
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
      """DROP INDEX IF EXISTS main."books_lookup"""",
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''",
      """CREATE INDEX IF NOT EXISTS main."books_lookup" ON "books" ("title")"""
    ).inOrder()
  }

  @Test
  fun `replaces a same-name index when uniqueness changes around table work`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_id" to index(
          name = "books_id",
          unique = true
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
      """DROP INDEX IF EXISTS main."books_id"""",
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''",
      """CREATE UNIQUE INDEX IF NOT EXISTS main."books_id" ON "books" ("id")"""
    ).inOrder()
  }

  @Test
  fun `replaces a same-name index when column order changes around table work`() {
    val previous = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          columns = listOf("id", "title")
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            ),
            migrationColumn(
              name = "status",
              schema = "status TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          columns = listOf("title", "id")
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
      """DROP INDEX IF EXISTS main."books_lookup"""",
      "ALTER TABLE books ADD COLUMN status TEXT DEFAULT ''",
      """CREATE INDEX IF NOT EXISTS main."books_lookup" ON "books" ("title", "id")"""
    ).inOrder()
  }

  @Test
  fun `replaces a same-name index when its owner changes around table work`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "shared_lookup" to index(
          name = "shared_lookup",
          table = "books"
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        ),
        "authors" to persistentTable(name = "authors")
      ),
      indices = linkedMapOf(
        "shared_lookup" to index(
          name = "shared_lookup",
          table = "authors"
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
      """DROP INDEX IF EXISTS main."shared_lookup"""",
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''",
      "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)",
      """CREATE INDEX IF NOT EXISTS main."shared_lookup" ON "authors" ("id")"""
    ).inOrder()
  }

  @Test
  fun `treats a case-only index rename as drop and create`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "Books_ID" to index(name = "Books_ID")
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      """DROP INDEX IF EXISTS main."Books_ID"""",
      """CREATE INDEX IF NOT EXISTS main."books_id" ON "books" ("id")"""
    ).inOrder()
  }

  @Test
  fun `ignores index map iteration order when definitions are unchanged`() {
    val firstIndex = index(name = "books_id")
    val secondIndex = index(
      name = "books_title",
      columns = listOf("title")
    )
    val previous = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        firstIndex.name to firstIndex,
        secondIndex.name to secondIndex
      )
    )
    val current = databaseStructure(
      tables = LinkedHashMap(previous.tables),
      indices = linkedMapOf(
        secondIndex.name to secondIndex,
        firstIndex.name to firstIndex
      )
    )
    val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()

    assertThat(
      runMigrationResult(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      ).migrationHappened
    ).isFalse()
    assertThat(migrationFile.exists()).isFalse()
  }

  @Test
  fun `keeps an unchanged index during a safe append-column migration`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id")
      )
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''"
    )
  }

  @Test
  fun `recreates every desired index after a direct table rebuild`() {
    val previous = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id"),
        "books_title" to index(
          name = "books_title",
          columns = listOf("title")
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT 'untitled'"
            )
          )
        )
      ),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id"),
        "books_title" to index(
          name = "books_title",
          columns = listOf("title")
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
      """DROP INDEX IF EXISTS main."books_id"""",
      """DROP INDEX IF EXISTS main."books_title"""",
      "ALTER TABLE books RENAME TO books_",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT 'untitled')",
      "INSERT INTO books (id,title) SELECT id,title FROM books_",
      "DROP TABLE IF EXISTS books_",
      """CREATE INDEX IF NOT EXISTS main."books_id" ON "books" ("id")""",
      """CREATE INDEX IF NOT EXISTS main."books_title" ON "books" ("title")"""
    ).inOrder()
  }

  @Test
  fun `recreates every desired index after a cascading dependency rebuild`() {
    val previous = databaseStructure(
      tables = linkedMapOf(
        "parents" to persistentTable(name = "parents"),
        "children" to persistentTable(
          name = "children",
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE",
              onDeleteCascade = true
            )
          )
        )
      ),
      indices = linkedMapOf(
        "parents_id" to index(
          name = "parents_id",
          table = "parents"
        ),
        "children_parent" to index(
          name = "children_parent",
          table = "children",
          columns = listOf("parent_id")
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "parents" to persistentTable(
          name = "parents",
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "value",
              schema = "value TEXT DEFAULT ''"
            )
          )
        ),
        "children" to persistentTable(
          name = "children",
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "parent_id",
              schema = "parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE",
              onDeleteCascade = true
            )
          )
        )
      ),
      indices = linkedMapOf(
        "parents_id" to index(
          name = "parents_id",
          table = "parents"
        ),
        "children_parent" to index(
          name = "children_parent",
          table = "children",
          columns = listOf("parent_id")
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
      """DROP INDEX IF EXISTS main."parents_id"""",
      """DROP INDEX IF EXISTS main."children_parent"""",
      "ALTER TABLE parents RENAME TO parents_",
      "ALTER TABLE children RENAME TO children_",
      "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, value TEXT DEFAULT '')",
      "INSERT INTO parents (id) SELECT id FROM parents_",
      "CREATE TABLE IF NOT EXISTS children " +
          "(id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0 REFERENCES parents(id) ON DELETE CASCADE)",
      "INSERT INTO children (id,parent_id) SELECT id,parent_id FROM children_",
      "DROP TABLE IF EXISTS children_",
      "DROP TABLE IF EXISTS parents_",
      """CREATE INDEX IF NOT EXISTS main."parents_id" ON "parents" ("id")""",
      """CREATE INDEX IF NOT EXISTS main."children_parent" ON "children" ("parent_id")"""
    ).inOrder()
  }

  @Test
  fun `creates a unique index added without table changes`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable())
    )
    val current = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_unique_id" to index(
          name = "books_unique_id",
          unique = true
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
      """CREATE UNIQUE INDEX IF NOT EXISTS main."books_unique_id" ON "books" ("id")"""
    )
  }

  @Test
  fun `reconciles index ownership through a simple table rename`() {
    val previous = databaseStructure(
      tables = linkedMapOf("legacy_books" to persistentTable(name = "legacy_books")),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          table = "legacy_books"
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(
        "books_lookup" to index(
          name = "books_lookup",
          table = "books"
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
      """DROP INDEX IF EXISTS main."books_lookup"""",
      "ALTER TABLE legacy_books RENAME TO books",
      """CREATE INDEX IF NOT EXISTS main."books_lookup" ON "books" ("id")"""
    ).inOrder()
  }

  @Test
  fun `drops indexes owned by a removed table before dropping the table`() {
    val previous = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(),
        "authors" to persistentTable(name = "authors")
      ),
      indices = linkedMapOf(
        "books_id" to index(name = "books_id"),
        "authors_id" to index(
          name = "authors_id",
          table = "authors"
        )
      )
    )
    val current = databaseStructure(
      tables = linkedMapOf("authors" to persistentTable(name = "authors")),
      indices = linkedMapOf(
        "authors_id" to index(
          name = "authors_id",
          table = "authors"
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
      """DROP INDEX IF EXISTS main."books_id"""",
      "DROP TABLE IF EXISTS books"
    ).inOrder()
  }

  @Test
  fun `creates indexes for a new table after creating the table`() {
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable())
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(),
        "authors" to persistentTable(name = "authors")
      ),
      indices = linkedMapOf(
        "authors_id" to index(
          name = "authors_id",
          table = "authors"
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
      "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)",
      """CREATE INDEX IF NOT EXISTS main."authors_id" ON "authors" ("id")"""
    ).inOrder()
  }

  @Test
  fun `quotes special index names in schema-qualified create SQL`() {
    val specialIndex = index(name = "book.idx\"quoted")
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable())
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(specialIndex.name to specialIndex)
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''",
      """CREATE INDEX IF NOT EXISTS main."book.idx""quoted" ON "books" ("id")"""
    ).inOrder()
  }

  @Test
  fun `quotes special index names in schema-qualified drop SQL`() {
    val specialIndex = index(name = "book.idx\"quoted")
    val previous = databaseStructure(
      tables = linkedMapOf("books" to persistentTable()),
      indices = linkedMapOf(specialIndex.name to specialIndex)
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
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
      """DROP INDEX IF EXISTS main."book.idx""quoted"""",
      "ALTER TABLE books ADD COLUMN title TEXT DEFAULT ''"
    ).inOrder()
  }

  @Test
  fun `reserves previous and current index identities for rebuild scratch names`() {
    val previousIndex = index(name = "books_")
    val previous = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT ''"
            )
          )
        )
      ),
      indices = linkedMapOf(previousIndex.name to previousIndex)
    )
    val current = databaseStructure(
      tables = linkedMapOf(
        "books" to persistentTable(
          columns = arrayListOf(
            idColumn(),
            migrationColumn(
              name = "title",
              schema = "title TEXT DEFAULT 'untitled'"
            )
          )
        )
      ),
      indices = linkedMapOf(previousIndex.name to previousIndex)
    )

    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      """DROP INDEX IF EXISTS main."books_"""",
      "ALTER TABLE books RENAME TO books__",
      "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT DEFAULT 'untitled')",
      "INSERT INTO books (id,title) SELECT id,title FROM books__",
      "DROP TABLE IF EXISTS books__",
      """CREATE INDEX IF NOT EXISTS main."books_" ON "books" ("id")"""
    ).inOrder()
  }
}

private data class MigrationRun(
  val migrationHappened: Boolean,
  val statements: List<String>
)

internal fun runMigration(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  temporaryDirectory: Path
): List<String> = runMigrationResult(
  previous = previous,
  current = current,
  temporaryDirectory = temporaryDirectory
).also { result ->
  assertThat(result.migrationHappened).isTrue()
}.statements

private fun runMigrationResult(
  previous: DatabaseStructure,
  current: DatabaseStructure,
  temporaryDirectory: Path
): MigrationRun {
  val structureFile = temporaryDirectory.resolve("db/latest.struct").toFile()
  val migrationFile = temporaryDirectory.resolve("src/debug/assets/1001.sql").toFile()
  val migrationHappened = MigrationsHandler(
    currentStructure = current,
    previousStructure = previous,
    outputStructureFile = structureFile,
    migrationOutputFile = migrationFile
  ).migrate()
  return MigrationRun(
    migrationHappened = migrationHappened,
    statements = migrationFile.takeIf(java.io.File::isFile)?.readLines().orEmpty()
  )
}

private fun databaseStructure(
  tables: LinkedHashMap<String, TableStructure> = linkedMapOf(),
  indices: LinkedHashMap<String, IndexStructure> = linkedMapOf()
) = DatabaseStructure(
  tables = tables,
  indices = indices
)

private fun persistentTable(
  name: String = "books",
  columns: ArrayList<ColumnStructure> = arrayListOf(idColumn())
) = migrationTable(
  name = name,
  columns = columns
)

private fun idColumn() = migrationColumn(
  name = "id",
  schema = "id INTEGER PRIMARY KEY"
).copy(
  id = true,
  sqlType = "INTEGER"
)

private fun index(
  name: String,
  table: String = "books",
  columns: List<String> = listOf("id"),
  unique: Boolean = false,
  schema: String = "main"
) = IndexStructure(
  name = name,
  indexSql = buildString {
    if (unique) append("CREATE UNIQUE") else append("CREATE")
    append(" INDEX IF NOT EXISTS ")
    append(schema)
    append('.')
    append(quoteIdentifier(name))
    append(" ON ")
    append(quoteIdentifier(table))
    append(" (")
    append(columns.joinToString(separator = ", ", transform = ::quoteIdentifier))
    append(')')
  },
  forTable = table
)

private fun quoteIdentifier(identifier: String) =
  "\"${identifier.replace(oldValue = "\"", newValue = "\"\"")}\""
