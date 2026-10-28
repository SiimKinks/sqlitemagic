package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

internal class NotNullMigrationTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `rebuilds same-shaped tables that gain NOT NULL in place`() {
    fun namedTable(
      name: String,
      nameColumn: ColumnStructure
    ) = migrationTable(
      name = name,
      columns = listOf(
        ColumnStructure(
          id = true,
          name = "id",
          sqlType = "INTEGER",
          schema = "id INTEGER PRIMARY KEY"
        ),
        nameColumn.copy(sqlType = "TEXT")
      )
    )

    assertThat(
      runMigration(
        previous = DatabaseStructure(
          tables = mapOf(
            "authors" to namedTable(
              name = "authors",
              nameColumn = plainColumn("name TEXT DEFAULT ''")
            ),
            "genres" to namedTable(
              name = "genres",
              nameColumn = plainColumn("name TEXT DEFAULT ''")
            )
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "authors" to namedTable(
              name = "authors",
              nameColumn = notNullColumn(
                schema = "name TEXT NOT NULL DEFAULT ''",
                default = "''"
              )
            ),
            "genres" to namedTable(
              name = "genres",
              nameColumn = notNullColumn(
                schema = "name TEXT NOT NULL DEFAULT ''",
                default = "''"
              )
            )
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE authors RENAME TO authors_",
      "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
      "INSERT INTO authors (id,name) SELECT id,IFNULL(name, '') FROM authors_",
      "DROP TABLE IF EXISTS authors_",
      "ALTER TABLE genres RENAME TO genres_",
      "CREATE TABLE IF NOT EXISTS genres (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
      "INSERT INTO genres (id,name) SELECT id,IFNULL(name, '') FROM genres_",
      "DROP TABLE IF EXISTS genres_"
    ).inOrder()
  }

  @Test
  fun `fills columns that gain NOT NULL with their default and deletes rows that hold NULL in the others`() {
    val currentColumns = arrayOf(
      plainColumn("id INTEGER PRIMARY KEY"),
      notNullColumn(
        schema = "name TEXT NOT NULL DEFAULT ''",
        default = "''"
      ),
      notNullColumn(
        schema = "quote TEXT NOT NULL DEFAULT 'it''s'",
        default = "'it''s'"
      ),
      notNullColumn(
        schema = "amount REAL NOT NULL DEFAULT - 1.5",
        default = "- 1.5"
      ),
      notNullColumn(
        schema = "computed INTEGER NOT NULL DEFAULT (1 + 2)",
        default = "(1 + 2)"
      ),
      notNullColumn(
        schema = "code TEXT NOT NULL DEFAULT 'x'",
        default = "'x'"
      ),
      plainColumn("note TEXT DEFAULT NULL"),
      notNullColumn("author INTEGER NOT NULL"),
      notNullColumn("slug TEXT NOT NULL UNIQUE")
    )

    assertThat(
      runMigrationResult(
        previous = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          plainColumn("name TEXT DEFAULT ''"),
          plainColumn("quote TEXT DEFAULT 'it''s'"),
          plainColumn("amount REAL DEFAULT 0.0"),
          plainColumn("computed INTEGER DEFAULT 0"),
          notNullColumn(
            schema = "code TEXT NOT NULL DEFAULT 'x'",
            default = "'x'"
          ),
          plainColumn("note TEXT DEFAULT NULL"),
          plainColumn("author INTEGER DEFAULT NULL"),
          plainColumn("slug TEXT UNIQUE")
        ),
        current = recordsStructure(*currentColumns),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM records WHERE author IS NULL OR slug IS NULL",
          "ALTER TABLE records RENAME TO records_",
          "CREATE TABLE IF NOT EXISTS records (${currentColumns.joinToString(transform = ColumnStructure::schema)})",
          "INSERT INTO records (id,name,quote,amount,computed,code,note,author,slug) " +
              "SELECT id,IFNULL(name, ''),IFNULL(quote, 'it''s'),IFNULL(amount, - 1.5),IFNULL(computed, (1 + 2))," +
              "code,note,author,slug FROM records_",
          "DROP TABLE IF EXISTS records_"
        ),
        warnings = listOf(
          "Migration deletes rows of records whose NOT NULL column(s) without a default hold NULL: author, slug. " +
              "Edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `appends a NOT NULL column with a default`() {
    assertThat(
      runMigration(
        previous = recordsStructure(plainColumn("id INTEGER PRIMARY KEY")),
        current = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          notNullColumn(
            schema = "name TEXT NOT NULL DEFAULT ''",
            default = "''"
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly("ALTER TABLE records ADD COLUMN name TEXT NOT NULL DEFAULT ''")
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
    strings = [
      "author INTEGER NOT NULL",
      "slug TEXT NOT NULL UNIQUE"
    ]
  )
  fun `recreates the table without its rows for a new NOT NULL column without a default`(column: String) {
    assertThat(
      runMigrationResult(
        previous = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          plainColumn("name TEXT DEFAULT ''")
        ),
        current = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          plainColumn("name TEXT DEFAULT ''"),
          notNullColumn(column)
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM records",
          "ALTER TABLE records RENAME TO records_",
          "CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY, name TEXT DEFAULT '', $column)",
          "DROP TABLE IF EXISTS records_"
        ),
        warnings = listOf(
          "Migration deletes all rows of records because existing rows have no value for its new column(s): " +
              "${column.substringBefore(' ')}. Edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `deletes recreated parent rows before its cascading children are copied`() {
    fun structure(vararg parentColumns: ColumnStructure) = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = parentColumns.asList()
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            plainColumn("id INTEGER PRIMARY KEY"),
            cascadingColumn("parent INTEGER REFERENCES parents(id) ON DELETE CASCADE")
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = structure(plainColumn("id INTEGER PRIMARY KEY")),
        current = structure(
          plainColumn("id INTEGER PRIMARY KEY"),
          notNullColumn("owner INTEGER NOT NULL")
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM parents",
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, owner INTEGER NOT NULL)",
          "CREATE TABLE IF NOT EXISTS children " +
              "(id INTEGER PRIMARY KEY, parent INTEGER REFERENCES parents(id) ON DELETE CASCADE)",
          "INSERT INTO children (id,parent) SELECT id,parent FROM children_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration deletes all rows of parents because existing rows have no value for its new column(s): " +
              "owner; ON DELETE CASCADE also deletes their dependent rows in children. Edit the migration SQL to " +
              "keep them."
        )
      )
    )
  }

  @Test
  fun `names transitive cascade dependents of rows deleted for holding NULL`() {
    fun structure(parentName: ColumnStructure) = DatabaseStructure(
      tables = mapOf(
        "parents" to migrationTable(
          name = "parents",
          columns = listOf(
            plainColumn("id INTEGER PRIMARY KEY"),
            parentName
          )
        ),
        "children" to migrationTable(
          name = "children",
          columns = listOf(
            plainColumn("id INTEGER PRIMARY KEY"),
            cascadingColumn("parent INTEGER REFERENCES parents(id) ON DELETE CASCADE")
          )
        ),
        "grandchildren" to migrationTable(
          name = "grandchildren",
          columns = listOf(
            plainColumn("id INTEGER PRIMARY KEY"),
            cascadingColumn("child INTEGER REFERENCES children(id) ON DELETE CASCADE")
          )
        )
      )
    )

    assertThat(
      runMigrationResult(
        previous = structure(plainColumn("name TEXT UNIQUE")),
        current = structure(notNullColumn("name TEXT NOT NULL UNIQUE")),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM parents WHERE name IS NULL",
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "ALTER TABLE grandchildren RENAME TO grandchildren_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)",
          "INSERT INTO parents (id,name) SELECT id,name FROM parents_",
          "CREATE TABLE IF NOT EXISTS children " +
              "(id INTEGER PRIMARY KEY, parent INTEGER REFERENCES parents(id) ON DELETE CASCADE)",
          "INSERT INTO children (id,parent) SELECT id,parent FROM children_",
          "CREATE TABLE IF NOT EXISTS grandchildren " +
              "(id INTEGER PRIMARY KEY, child INTEGER REFERENCES children(id) ON DELETE CASCADE)",
          "INSERT INTO grandchildren (id,child) SELECT id,child FROM grandchildren_",
          "DROP TABLE IF EXISTS grandchildren_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration deletes rows of parents whose NOT NULL column(s) without a default hold NULL: name; ON DELETE " +
              "CASCADE also deletes their dependent rows in children, grandchildren. Edit the migration SQL to keep " +
              "them."
        )
      )
    )
  }

  @Test
  fun `does not name cascades when the current schema leaves foreign keys disabled`() {
    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "parents" to migrationTable(
              name = "parents",
              columns = listOf(plainColumn("id INTEGER PRIMARY KEY"))
            ),
            "children" to migrationTable(
              name = "children",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                cascadingColumn("parent INTEGER REFERENCES parents(id) ON DELETE CASCADE")
              )
            )
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "parents" to migrationTable(
              name = "parents",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                notNullColumn("owner INTEGER NOT NULL")
              )
            ),
            "children" to migrationTable(
              name = "children",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                plainColumn("parent INTEGER DEFAULT NULL")
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
          "DELETE FROM parents",
          "ALTER TABLE parents RENAME TO parents_",
          "ALTER TABLE children RENAME TO children_",
          "CREATE TABLE IF NOT EXISTS parents (id INTEGER PRIMARY KEY, owner INTEGER NOT NULL)",
          "CREATE TABLE IF NOT EXISTS children (id INTEGER PRIMARY KEY, parent INTEGER DEFAULT NULL)",
          "INSERT INTO children (id,parent) SELECT id,parent FROM children_",
          "DROP TABLE IF EXISTS children_",
          "DROP TABLE IF EXISTS parents_"
        ),
        warnings = listOf(
          "Migration deletes all rows of parents because existing rows have no value for its new column(s): " +
              "owner. Edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `infers a rename while another same-shaped table gains NOT NULL`() {
    val nullableName = plainColumn("name TEXT DEFAULT ''")
    val notNullName = notNullColumn(
      schema = "name TEXT NOT NULL DEFAULT ''",
      default = "''"
    )
    fun table(
      name: String,
      nameColumn: ColumnStructure
    ) = migrationTable(
      name = name,
      columns = listOf(
        plainColumn("id INTEGER PRIMARY KEY"),
        nameColumn
      )
    )

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "authors" to table(
              name = "authors",
              nameColumn = nullableName
            ),
            "genres" to table(
              name = "genres",
              nameColumn = nullableName
            )
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "writers" to table(
              name = "writers",
              nameColumn = notNullName
            ),
            "genres" to table(
              name = "genres",
              nameColumn = notNullName
            )
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE authors RENAME TO writers_",
          "CREATE TABLE IF NOT EXISTS writers (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
          "INSERT INTO writers (id,name) SELECT id,IFNULL(name, '') FROM writers_",
          "DROP TABLE IF EXISTS writers_",
          "ALTER TABLE genres RENAME TO genres_",
          "CREATE TABLE IF NOT EXISTS genres (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
          "INSERT INTO genres (id,name) SELECT id,IFNULL(name, '') FROM genres_",
          "DROP TABLE IF EXISTS genres_"
        )
      )
    )
  }

  @Test
  fun `hints a rename into a same-shaped table that gains NOT NULL when dropping a table`() {
    val nullableName = plainColumn("name TEXT DEFAULT ''")

    assertThat(
      runMigrationResult(
        previous = DatabaseStructure(
          tables = mapOf(
            "authors" to migrationTable(
              name = "authors",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                nullableName
              )
            ),
            "writers" to migrationTable(
              name = "writers",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                nullableName
              )
            )
          )
        ),
        current = DatabaseStructure(
          tables = mapOf(
            "authors" to migrationTable(
              name = "authors",
              columns = listOf(
                plainColumn("id INTEGER PRIMARY KEY"),
                notNullColumn(
                  schema = "name TEXT NOT NULL DEFAULT ''",
                  default = "''"
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
          "ALTER TABLE authors RENAME TO authors_",
          "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
          "INSERT INTO authors (id,name) SELECT id,IFNULL(name, '') FROM authors_",
          "DROP TABLE IF EXISTS authors_",
          "DROP TABLE IF EXISTS writers"
        ),
        warnings = listOf(
          "Migration drops table writers with its rows; if it was renamed to authors, edit the migration SQL to " +
              "keep them."
        )
      )
    )
  }

  @Test
  fun `warns about columns a rebuild drops with their data`() {
    assertThat(
      runMigrationResult(
        previous = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          notNullColumn(
            schema = "title TEXT NOT NULL DEFAULT ''",
            default = "''"
          )
        ),
        current = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          notNullColumn(
            schema = "name TEXT NOT NULL DEFAULT ''",
            default = "''"
          )
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "ALTER TABLE records RENAME TO records_",
          "CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
          "INSERT INTO records (id) SELECT id FROM records_",
          "DROP TABLE IF EXISTS records_"
        ),
        warnings = listOf(
          "Migration drops column(s) of records with their data: title. Edit the migration SQL to keep it."
        )
      )
    )
  }

  @Test
  fun `reports only the row deletion when a table recreated without its rows also drops columns`() {
    assertThat(
      runMigrationResult(
        previous = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          plainColumn("note TEXT DEFAULT NULL")
        ),
        current = recordsStructure(
          plainColumn("id INTEGER PRIMARY KEY"),
          notNullColumn("slug TEXT NOT NULL UNIQUE")
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM records",
          "ALTER TABLE records RENAME TO records_",
          "CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY, slug TEXT NOT NULL UNIQUE)",
          "DROP TABLE IF EXISTS records_"
        ),
        warnings = listOf(
          "Migration deletes all rows of records because existing rows have no value for its new column(s): " +
              "slug. Edit the migration SQL to keep them."
        )
      )
    )
  }

  @Test
  fun `deletes rows with a NULL key when a table becomes WITHOUT ROWID`() {
    assertThat(
      runMigrationResult(
        previous = itemsStructure(
          key = keyColumn(sqlType = "TEXT"),
          withoutRowId = false
        ),
        current = itemsStructure(
          key = keyColumn(sqlType = "TEXT"),
          withoutRowId = true
        ),
        temporaryDirectory = temporaryDirectory
      )
    ).isEqualTo(
      MigrationRun(
        migrationHappened = true,
        statements = listOf(
          "DELETE FROM items WHERE id IS NULL",
          "ALTER TABLE items RENAME TO items_",
          "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY) WITHOUT ROWID",
          "INSERT INTO items (id) SELECT id FROM items_",
          "DROP TABLE IF EXISTS items_"
        ),
        warnings = listOf(
          "Migration deletes rows of items whose NOT NULL column(s) without a default hold NULL: id. Edit the " +
              "migration SQL to keep them."
        )
      )
    )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("keysWithoutNull")
  fun `keeps every row when a key that already excludes NULL is rebuilt`(
    label: String,
    previous: DatabaseStructure,
    current: DatabaseStructure
  ) {
    assertThat(
      runMigration(
        previous = previous,
        current = current,
        temporaryDirectory = temporaryDirectory
      )
    ).containsExactly(
      "ALTER TABLE items RENAME TO items_",
      current.tables
        .getValue("items")
        .schema,
      "INSERT INTO items (id) SELECT id FROM items_",
      "DROP TABLE IF EXISTS items_"
    ).inOrder()
  }

  private fun recordsStructure(vararg columns: ColumnStructure) = DatabaseStructure(
    tables = mapOf(
      "records" to migrationTable(
        name = "records",
        columns = columns.asList()
      )
    )
  )

  private fun plainColumn(schema: String) = migrationColumn(
    name = schema.substringBefore(' '),
    schema = schema
  )

  private fun cascadingColumn(schema: String) = migrationColumn(
    name = schema.substringBefore(' '),
    schema = schema,
    onDeleteCascade = true
  )

  private fun notNullColumn(
    schema: String,
    default: String? = null
  ) = migrationColumn(
    name = schema.substringBefore(' '),
    schema = schema,
    notNull = true,
    notNullDefault = default
  )

  companion object {
    @JvmStatic
    fun keysWithoutNull() = listOf(
      Arguments.of(
        "INTEGER key of a rowid table becomes WITHOUT ROWID",
        itemsStructure(
          key = keyColumn(sqlType = "INTEGER"),
          withoutRowId = false
        ),
        itemsStructure(
          key = keyColumn(sqlType = "INTEGER"),
          withoutRowId = true
        )
      ),
      Arguments.of(
        "WITHOUT ROWID key becomes a NOT NULL rowid table key",
        itemsStructure(
          key = keyColumn(sqlType = "TEXT"),
          withoutRowId = true
        ),
        itemsStructure(
          key = keyColumn(sqlType = "TEXT").copy(
            schema = "id TEXT NOT NULL PRIMARY KEY",
            notNull = true
          ),
          withoutRowId = false
        )
      )
    )
  }
}

private fun keyColumn(sqlType: String) = ColumnStructure(
  id = true,
  name = "id",
  sqlType = sqlType,
  schema = "id $sqlType PRIMARY KEY"
)

private fun itemsStructure(
  key: ColumnStructure,
  withoutRowId: Boolean
): DatabaseStructure {
  val table = migrationTable(
    name = "items",
    columns = listOf(key)
  )
  return DatabaseStructure(
    tables = mapOf(
      "items" to when {
        withoutRowId -> table.copy(schema = "${table.schema} WITHOUT ROWID")
        else -> table
      }
    )
  )
}
