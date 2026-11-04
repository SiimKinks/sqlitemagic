package com.siimkinks.sqlitemagic.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
import org.junit.jupiter.api.Test

internal class MigrationExecutionTest {
  @Test
  fun `rebuilt tables keep their AUTOINCREMENT counter`() {
    val authors = listOf(
      "id INTEGER PRIMARY KEY AUTOINCREMENT",
      "name TEXT",
      "age INTEGER"
    )
    val books = listOf(
      "id INTEGER PRIMARY KEY AUTOINCREMENT",
      "author INTEGER REFERENCES authors(id) ON DELETE CASCADE"
    )
    val authorRows = listOf(
      "INSERT INTO authors (name) VALUES ('a'), ('b'), ('c')",
      "DELETE FROM authors WHERE id = 3"
    )
    mapOf(
      "rebuilt table" to CounterCase(
        previous = structure("authors" to authors),
        current = structure("authors" to authors.dropLast(1)),
        rows = authorRows,
        nextId = 3 + 1
      ),
      "rebuilt table beside a temporary AUTOINCREMENT table" to CounterCase(
        previous = structure("authors" to authors),
        current = structure("authors" to authors.dropLast(1)),
        rows = authorRows + listOf(
          "CREATE TEMP TABLE scratch (id INTEGER PRIMARY KEY AUTOINCREMENT)",
          "INSERT INTO scratch DEFAULT VALUES"
        ),
        nextId = 3 + 1
      ),
      "rebuilt table without copied rows" to CounterCase(
        previous = structure("authors" to authors),
        current = structure("authors" to authors + "rank INTEGER NOT NULL"),
        rows = authorRows,
        nextId = 3 + 1,
        insert = "INSERT INTO authors (rank) VALUES (1)"
      ),
      "renamed rebuilt table" to CounterCase(
        previous = structure("authors" to authors),
        current = structure("writers" to authors.dropLast(1) + "age INTEGER DEFAULT 0"),
        rows = authorRows,
        nextId = 3 + 1,
        table = "writers"
      ),
      "linked rebuild" to CounterCase(
        previous = structure(
          "authors" to authors,
          "books" to books
        ),
        current = structure(
          "authors" to authors.dropLast(1),
          "books" to books
        ),
        rows = authorRows + listOf(
          "INSERT INTO books (author) VALUES (1), (2), (2)",
          "DELETE FROM books WHERE id = 3"
        ),
        nextId = 3 + 1,
        table = "books"
      ),
      "table that becomes AUTOINCREMENT" to CounterCase(
        previous = structure("authors" to listOf("id INTEGER PRIMARY KEY") + authors.drop(1)),
        current = structure("authors" to authors.dropLast(1)),
        rows = authorRows,
        nextId = 2 + 1
      ),
      "table that stops being AUTOINCREMENT" to CounterCase(
        previous = structure("authors" to authors),
        current = structure("authors" to listOf("id INTEGER PRIMARY KEY", "name TEXT")),
        rows = authorRows,
        nextId = 2 + 1
      ),
      "rebuilt table named with a keyword" to CounterCase(
        previous = structure("order" to authors),
        current = structure("order" to authors.dropLast(1)),
        rows = listOf(
          """INSERT INTO "order" (name) VALUES ('a'), ('b'), ('c')""",
          """DELETE FROM "order" WHERE id = 3"""
        ),
        nextId = 3 + 1,
        table = "order"
      ),
      "table without AUTOINCREMENT" to CounterCase(
        previous = structure("authors" to listOf("id INTEGER PRIMARY KEY") + authors.drop(1)),
        current = structure("authors" to listOf("id INTEGER PRIMARY KEY", "name TEXT")),
        rows = authorRows,
        nextId = 2 + 1
      )
    ).forEach { (label, case) ->
      migratedDatabase(
        previous = case.previous,
        current = case.current,
        rows = case.rows
      ).use { connection ->
        connection.execSQL(case.insert ?: "INSERT INTO ${quoteSqlIdentifier(case.table)} DEFAULT VALUES")

        assertWithMessage(label)
          .that(connection.longs("SELECT MAX(id) FROM ${quoteSqlIdentifier(case.table)}"))
          .containsExactly(case.nextId.toLong())
      }
    }
  }

  @Test
  fun `a new table between rebuilt tables keeps its reference to the rebuilt parent`() {
    val current = structure(
      "sections" to listOf("id INTEGER PRIMARY KEY", "title TEXT NOT NULL DEFAULT ''"),
      "categories" to listOf(
        "id INTEGER PRIMARY KEY",
        "section INTEGER REFERENCES sections(id) ON DELETE CASCADE"
      ),
      "books" to listOf(
        "id INTEGER PRIMARY KEY",
        "category INTEGER REFERENCES categories(id) ON DELETE CASCADE"
      )
    )

    migratedDatabase(
      previous = structure(
        "sections" to listOf("id INTEGER PRIMARY KEY", "title TEXT"),
        "books" to listOf("id INTEGER PRIMARY KEY")
      ),
      current = current,
      rows = listOf(
        "INSERT INTO sections (id) VALUES (1)",
        "INSERT INTO books (id) VALUES (1)"
      )
    ).use { connection ->
      connection.execSQL("INSERT INTO categories (id, section) VALUES (1, 1)")
      connection.execSQL("UPDATE books SET category = 1")
      connection.execSQL("DELETE FROM sections")
      assertThat(
        listOf(
          connection.longs("SELECT COUNT(*) FROM categories"),
          connection.longs("SELECT COUNT(*) FROM books")
        )
      ).isEqualTo(listOf(listOf(0L), listOf(0L)))
    }
  }

  @Test
  fun `deletes rows whose new cascading reference points to a missing row`() {
    val previous = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY", "name TEXT"),
      "books" to listOf("id INTEGER PRIMARY KEY", "author INTEGER", "title TEXT")
    )
    val current = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY", "name TEXT"),
      "books" to listOf(
        "id INTEGER PRIMARY KEY",
        "author INTEGER REFERENCES authors(id) ON DELETE CASCADE",
        "title TEXT"
      )
    )

    migratedDatabase(
      previous = previous,
      current = current,
      rows = listOf(
        "INSERT INTO authors (id) VALUES (1)",
        "INSERT INTO books (id, author) VALUES (1, 1), (2, 2), (3, NULL)"
      )
    ).use { connection ->
      assertThat(connection.longs("SELECT id FROM books ORDER BY id"))
        .containsExactly(1L, 3L)
        .inOrder()
    }
    assertThat(
      planSchemaMigration(
        previous = previous,
        current = current
      ).warnings
    ).containsExactly(
      "Migration deletes rows of books whose ON DELETE CASCADE column(s) reference missing rows: author. " +
          "Edit the migration SQL to keep them."
    )
  }

  @Test
  fun `deletes rows whose new cascading reference to a table named with a keyword points to a missing row`() {
    migratedDatabase(
      previous = structure(
        "order" to listOf("id INTEGER PRIMARY KEY"),
        "select" to listOf("id INTEGER PRIMARY KEY", """"order" INTEGER""", "title TEXT")
      ),
      current = structure(
        "order" to listOf("id INTEGER PRIMARY KEY"),
        "select" to listOf(
          "id INTEGER PRIMARY KEY",
          """"order" INTEGER REFERENCES "order"("id") ON DELETE CASCADE""",
          "title TEXT"
        )
      ),
      rows = listOf(
        """INSERT INTO "order" (id) VALUES (1)""",
        """INSERT INTO "select" (id, "order") VALUES (1, 1), (2, 2), (3, NULL)"""
      )
    ).use { connection ->
      assertThat(connection.longs("""SELECT id FROM "select" ORDER BY id"""))
        .containsExactly(1L, 3L)
        .inOrder()
    }
  }

  @Test
  fun `deletes rows whose NULL cascading reference becomes a default that points to a missing row`() {
    val previous = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY"),
      "books" to listOf("id INTEGER PRIMARY KEY", "author INTEGER REFERENCES authors(id) ON DELETE CASCADE")
    )
    val current = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY"),
      "books" to listOf(
        "id INTEGER PRIMARY KEY",
        "author INTEGER NOT NULL DEFAULT 9 REFERENCES authors(id) ON DELETE CASCADE"
      )
    )

    migratedDatabase(
      previous = previous,
      current = current,
      rows = listOf(
        "INSERT INTO authors (id) VALUES (1)",
        "INSERT INTO books (id, author) VALUES (1, 1), (2, NULL)"
      )
    ).use { connection ->
      assertThat(connection.longs("SELECT id FROM books ORDER BY id")).containsExactly(1L)
    }
    assertThat(
      planSchemaMigration(
        previous = previous,
        current = current
      ).warnings
    ).containsExactly(
      "Migration deletes rows of books whose NULL in NOT NULL ON DELETE CASCADE column(s) becomes a default that " +
          "references a missing row: author. Edit the migration SQL to keep them."
    )
  }

  @Test
  fun `keeps only rows whose every cascading reference points to an existing row`() {
    migratedDatabase(
      previous = structure(
        "authors" to listOf("id INTEGER PRIMARY KEY"),
        "books" to listOf("id INTEGER PRIMARY KEY", "author INTEGER", "editor INTEGER")
      ),
      current = structure(
        "authors" to listOf("id INTEGER PRIMARY KEY"),
        "books" to listOf(
          "id INTEGER PRIMARY KEY",
          "author INTEGER REFERENCES authors(id) ON DELETE CASCADE",
          "editor INTEGER REFERENCES authors(id) ON DELETE CASCADE"
        )
      ),
      rows = listOf(
        "INSERT INTO authors (id) VALUES (1)",
        "INSERT INTO books (id, author, editor) VALUES (1, 1, 1), (2, 1, 9), (3, 9, 1), (4, NULL, NULL)"
      )
    ).use { connection ->
      assertThat(connection.longs("SELECT id FROM books ORDER BY id"))
        .containsExactly(1L, 4L)
        .inOrder()
    }
  }

  @Test
  fun `deletes cascade dependents of rows deleted for a new cascading reference`() {
    val books = listOf("id INTEGER PRIMARY KEY", "author INTEGER")
    val reviews = listOf(
      "id INTEGER PRIMARY KEY",
      "book INTEGER REFERENCES books(id) ON DELETE CASCADE",
      "stars INTEGER"
    )
    val previous = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY"),
      "books" to books,
      "reviews" to reviews
    )
    val current = structure(
      "authors" to listOf("id INTEGER PRIMARY KEY"),
      "books" to listOf("id INTEGER PRIMARY KEY", "author INTEGER REFERENCES authors(id) ON DELETE CASCADE"),
      "reviews" to reviews.dropLast(1)
    )

    migratedDatabase(
      previous = previous,
      current = current,
      rows = listOf(
        "INSERT INTO authors (id) VALUES (1)",
        "INSERT INTO books (id, author) VALUES (1, 1), (2, 2)",
        "INSERT INTO reviews (id, book) VALUES (1, 1), (2, 2), (3, NULL)"
      )
    ).use { connection ->
      assertThat(connection.longs("SELECT id FROM books ORDER BY id")).containsExactly(1L)
      assertThat(connection.longs("SELECT id FROM reviews ORDER BY id"))
        .containsExactly(1L, 3L)
        .inOrder()
    }
    assertThat(
      planSchemaMigration(
        previous = previous,
        current = current
      ).warnings
    ).containsExactly(
      "Migration deletes rows of books whose ON DELETE CASCADE column(s) reference missing rows: author; " +
          "ON DELETE CASCADE also deletes their dependent rows in reviews. Edit the migration SQL to keep them.",
      "Migration drops column(s) of reviews with their data: stars. Edit the migration SQL to keep it."
    )
  }

  @Test
  fun `evidence with unquoted identifiers migrates only what changed`() {
    val evidence = DatabaseStructure(
      tables = mapOf(
        "books" to migrationTable(
          name = "books",
          columns = listOf(
            column("id INTEGER PRIMARY KEY"),
            column("title TEXT")
          )
        )
      )
    )
    val bookColumns = listOf(""""id" INTEGER PRIMARY KEY""", """"title" TEXT""")
    val cases = mapOf(
      "unchanged table" to structure("books" to bookColumns),
      "added column" to structure("books" to bookColumns + """"note" TEXT"""),
      "renamed table" to structure("writers" to bookColumns)
    )

    assertThat(
      cases.mapValues { (_, current) ->
        planSchemaMigration(
          previous = evidence,
          current = current
        ).statements
      }
    ).isEqualTo(
      mapOf(
        "unchanged table" to emptyList(),
        "added column" to listOf("""ALTER TABLE "books" ADD COLUMN "note" TEXT"""),
        "renamed table" to listOf("""ALTER TABLE "books" RENAME TO "writers"""")
      )
    )
  }

  private fun migratedDatabase(
    previous: DatabaseStructure,
    current: DatabaseStructure,
    rows: List<String>
  ): SQLiteConnection {
    val connection = BundledSQLiteDriver().open(":memory:")
    previous.tables.values.forEach { connection.execSQL(it.schema) }
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

  private fun SQLiteConnection.longs(sql: String) = prepare(sql).use { statement ->
    buildList {
      while (statement.step()) {
        add(statement.getLong(0))
      }
    }
  }

  private fun structure(vararg tables: Pair<String, List<String>>) = DatabaseStructure(
    tables = tables.associate { (name, columns) ->
      name to TableStructure(
        name = name,
        schema = "CREATE TABLE IF NOT EXISTS ${quoteSqlIdentifier(name)} (${columns.joinToString()})",
        columns = columns.map(::column)
      )
    }
  )

  private fun column(schema: String) = ColumnStructure(
    id = "PRIMARY KEY" in schema,
    autoIncrement = "AUTOINCREMENT" in schema,
    name = schema
      .substringBefore(' ')
      .trim('"'),
    onDeleteCascade = "ON DELETE CASCADE" in schema,
    sqlType = schema
      .split(' ')
      .getOrElse(index = 1) { "" },
    schema = schema,
    notNull = "NOT NULL" in schema,
    notNullDefault = schema
      .substringAfter(
        delimiter = " DEFAULT ",
        missingDelimiterValue = ""
      )
      .substringBefore(' ')
      .takeIf { "NOT NULL" in schema && it.isNotEmpty() }
  )

  private data class CounterCase(
    val previous: DatabaseStructure,
    val current: DatabaseStructure,
    val rows: List<String>,
    val nextId: Int,
    val table: String = "authors",
    val insert: String? = null
  )
}
