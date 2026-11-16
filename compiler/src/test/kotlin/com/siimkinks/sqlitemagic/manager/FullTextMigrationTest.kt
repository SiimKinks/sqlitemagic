package com.siimkinks.sqlitemagic.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextRole.INDEXED
import com.siimkinks.sqlitemagic.fulltext.FullTextColumnElement
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.fulltext.dropFullTextTableSql
import com.siimkinks.sqlitemagic.fulltext.dropFullTextTriggerSql
import com.siimkinks.sqlitemagic.fulltext.fullTextCommandSql
import com.siimkinks.sqlitemagic.fulltext.fullTextIndexIdentity
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
import org.junit.jupiter.api.Test

internal class FullTextMigrationTest {
  @Test
  fun `migrations keep full-text indexes in sync with their tables and equal to a fresh install`() {
    val articleIndex = index(
      table = "article",
      columns = listOf("title", "body")
    )
    val cases = mapOf(
      "adding an index" to Change(
        previous = structure(ARTICLE),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "removing an index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(ARTICLE)
      ),
      "changing an index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title")
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "rebuilding the content table" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(
          ARTICLE_WITHOUT_AUTHOR,
          indices = listOf(articleIndex)
        )
      ),
      "rebuilding the parent of the content table" to Change(
        previous = structure(
          ARTICLE,
          COMMENT,
          indices = listOf(COMMENT_INDEX)
        ),
        current = structure(
          ARTICLE_WITHOUT_AUTHOR,
          COMMENT,
          indices = listOf(COMMENT_INDEX)
        )
      ),
      "renaming the content table" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              name = "search"
            )
          )
        ),
        current = structure(
          ARTICLE.renamed("post"),
          indices = listOf(
            index(
              table = "post",
              columns = listOf("title", "body"),
              name = "search"
            )
          )
        )
      ),
      "removing the content table" to Change(
        previous = structure(
          ARTICLE,
          NOTE,
          indices = listOf(NOTE_INDEX)
        ),
        current = structure(ARTICLE)
      ),
      "adding columns to the content table" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(
          ARTICLE_WITH_YEAR,
          indices = listOf(articleIndex)
        )
      ),
      "rebuilding another table" to Change(
        previous = structure(
          ARTICLE,
          NOTE_WITH_EXTRA,
          indices = listOf(articleIndex)
        ),
        current = structure(
          ARTICLE,
          NOTE,
          indices = listOf(articleIndex)
        )
      ),
      "adding a table with an index" to Change(
        previous = structure(ARTICLE),
        current = structure(
          ARTICLE,
          NOTE,
          indices = listOf(NOTE_INDEX)
        )
      ),
      "filling an added table with an index in the script" to Change(
        previous = structure(ARTICLE),
        current = structure(
          ARTICLE,
          NOTE,
          indices = listOf(NOTE_INDEX)
        ),
        statementsBeforeIndices = listOf(
          """INSERT INTO "note" ("id", "text") SELECT "id" + 10, "body" FROM "article""""
        )
      )
    )
    val articleMatches = mapOf(
      "article_fts arrakis" to listOf(1L),
      "article_fts desert" to listOf(1L),
      "article_fts ocean" to listOf(3L),
      "article_fts solaris" to listOf(3L)
    )
    val articleRebuildWarning = "Migration rebuilds full-text index article_fts from all rows of article, which " +
        "can take long for a large table."
    val expected = mapOf(
      "adding an index" to MigratedIndices(
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf(articleRebuildWarning)
      ),
      "removing an index" to MigratedIndices(dropped = listOf("article_fts")),
      "changing an index" to MigratedIndices(
        dropped = listOf("article_fts"),
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf(articleRebuildWarning)
      ),
      "rebuilding the content table" to MigratedIndices(
        dropped = listOf("article_fts"),
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf(
          "Migration drops column(s) of article with their data: author. Edit the migration SQL to keep it.",
          articleRebuildWarning
        )
      ),
      "rebuilding the parent of the content table" to MigratedIndices(
        dropped = listOf("comment_fts"),
        created = listOf("comment_fts"),
        rebuilt = listOf("comment_fts"),
        matches = mapOf(
          "comment_fts mimoid" to listOf(3L),
          "comment_fts sandworm" to listOf(1L)
        ),
        warnings = listOf(
          "Migration drops column(s) of article with their data: author. Edit the migration SQL to keep it.",
          "Migration rebuilds full-text index comment_fts from all rows of comment, which can take long for a " +
              "large table."
        )
      ),
      "renaming the content table" to MigratedIndices(
        dropped = listOf("search"),
        created = listOf("search"),
        rebuilt = listOf("search"),
        matches = articleMatches.mapKeys { (key, _) -> key.replace(oldValue = "article_fts", newValue = "search") },
        warnings = listOf(
          "Migration rebuilds full-text index search from all rows of post, which can take long for a large table."
        )
      ),
      "removing the content table" to MigratedIndices(
        dropped = listOf("note_fts"),
        warnings = listOf("Migration drops table note with its rows. Edit the migration SQL to keep them.")
      ),
      "adding columns to the content table" to MigratedIndices(matches = articleMatches),
      "rebuilding another table" to MigratedIndices(
        matches = articleMatches,
        warnings = listOf(
          "Migration drops column(s) of note with their data: extra. Edit the migration SQL to keep it."
        )
      ),
      "adding a table with an index" to MigratedIndices(
        created = listOf("note_fts"),
        rebuilt = listOf("note_fts"),
        matches = mapOf("note_fts shopping" to listOf(2L))
      ),
      "filling an added table with an index in the script" to MigratedIndices(
        created = listOf("note_fts"),
        rebuilt = listOf("note_fts"),
        matches = mapOf(
          "note_fts desert" to listOf(11L),
          "note_fts shopping" to listOf(2L)
        )
      )
    )

    assertThat(cases.mapValues { (_, change) -> change.migrate() }).isEqualTo(expected)
  }

  @Test
  fun `drops the index before the table statements and creates and rebuilds it after them`() {
    val index = index(
      table = "article",
      columns = listOf("title", "body")
    )
    val statements = planSchemaMigration(
      previous = structure(
        ARTICLE,
        indices = listOf(index)
      ),
      current = structure(
        ARTICLE_WITHOUT_AUTHOR,
        indices = listOf(index)
      )
    ).statements

    assertThat(statements).isEqualTo(
      index.triggers.keys.map(::dropFullTextTriggerSql) +
          dropFullTextTableSql(index.name) +
          planSchemaMigration(
            previous = structure(ARTICLE),
            current = structure(ARTICLE_WITHOUT_AUTHOR)
          ).statements +
          index.tableSql +
          index.triggers.values +
          fullTextCommandSql(
            name = index.name,
            command = "rebuild"
          )
    )
  }

  private data class Change(
    val previous: DatabaseStructure,
    val current: DatabaseStructure,
    val statementsBeforeIndices: List<String> = emptyList()
  ) {
    fun migrate(): MigratedIndices {
      val migration = planSchemaMigration(
        previous = previous,
        current = current
      )
      val connection = BundledSQLiteDriver().open(":memory:")
      connection.execSQL("PRAGMA foreign_keys = ON")
      connection.execSQL("PRAGMA recursive_triggers = ON")
      previous.creationSql().forEach(connection::execSQL)
      previous.tables.keys
        .flatMap(PREVIOUS_ROWS::getValue)
        .forEach(connection::execSQL)
      val firstIndexStatement = migration.statements.firstOrNull { it.startsWith(CREATE_INDEX_PREFIX) }
      connection.execSQL("BEGIN EXCLUSIVE")
      migration.statements
        .flatMap { statement ->
          when (statement) {
            firstIndexStatement -> statementsBeforeIndices + statement
            else -> listOf(statement)
          }
        }
        .forEach(connection::execSQL)
      connection.execSQL("COMMIT")
      current.tables.keys
        .flatMap(CURRENT_CHANGES::getValue)
        .forEach(connection::execSQL)
      val fresh = BundledSQLiteDriver().open(":memory:")
      current.creationSql().forEach(fresh::execSQL)
      return MigratedIndices(
        dropped = migration.statements.objectNames(prefix = "DROP TABLE IF EXISTS main."),
        created = migration.statements.objectNames(prefix = CREATE_INDEX_PREFIX),
        rebuilt = migration.statements
          .filter { it.endsWith("VALUES ('rebuild')") }
          .objectNames(prefix = "INSERT INTO main."),
        matches = connection.matches(current.fullTextIndices.keys),
        matchesFreshInstall = connection.schemaRows() == fresh.schemaRows(),
        warnings = migration.warnings
      )
    }
  }

  private data class MigratedIndices(
    val dropped: List<String> = emptyList(),
    val created: List<String> = emptyList(),
    val rebuilt: List<String> = emptyList(),
    val matches: Map<String, List<Long>> = emptyMap(),
    val matchesFreshInstall: Boolean = true,
    val warnings: List<String> = emptyList()
  )

  private companion object {
    const val CREATE_INDEX_PREFIX = "CREATE VIRTUAL TABLE IF NOT EXISTS main."
    val ID = ColumnStructure(
      id = true,
      name = "id",
      sqlType = "INTEGER",
      schema = """"id" INTEGER PRIMARY KEY"""
    )
    val ARTICLE = table(
      name = "article",
      columns = listOf(ID, text("title"), text("body"), text("author"))
    )
    val ARTICLE_WITHOUT_AUTHOR = ARTICLE.withColumns(ARTICLE.columns.dropLast(1))
    val ARTICLE_WITH_YEAR = ARTICLE.withColumns(
      ARTICLE.columns + ColumnStructure(
        name = "year",
        sqlType = "INTEGER",
        schema = """"year" INTEGER"""
      )
    )
    val COMMENT = table(
      name = "comment",
      columns = listOf(
        ID,
        ColumnStructure(
          name = "article",
          onDeleteCascade = true,
          sqlType = "INTEGER",
          schema = """"article" INTEGER REFERENCES "article"("id") ON DELETE CASCADE"""
        ),
        text("text")
      )
    )
    val NOTE = table(
      name = "note",
      columns = listOf(ID, text("text"))
    )
    val NOTE_WITH_EXTRA = NOTE.withColumns(NOTE.columns + text("extra"))
    val COMMENT_INDEX = index(
      table = "comment",
      columns = listOf("text")
    )
    val NOTE_INDEX = index(
      table = "note",
      columns = listOf("text")
    )
    val PREVIOUS_ROWS = mapOf(
      "article" to listOf(
        """INSERT INTO "article" ("id", "title", "body") VALUES (1, 'Dune', 'desert planet'), (2, 'Emma', 'novel')"""
      ),
      "comment" to listOf(
        """INSERT INTO "comment" ("id", "article", "text") VALUES (1, 1, 'sandworm'), (2, 2, 'matchmaking')"""
      ),
      "note" to listOf("""INSERT INTO "note" ("id", "text") VALUES (1, 'groceries')""")
    )
    val CURRENT_CHANGES = mapOf(
      "article" to articleChanges("article"),
      "post" to articleChanges("post"),
      "comment" to listOf("""INSERT INTO "comment" ("id", "article", "text") VALUES (3, 3, 'mimoid')"""),
      "note" to listOf("""INSERT INTO "note" ("id", "text") VALUES (2, 'shopping list')""")
    )
    val SEARCHED_TERMS = listOf(
      "arrakis", "desert", "dune", "emma", "groceries", "matchmaking", "mimoid", "ocean", "sandworm", "shopping",
      "solaris"
    )

    fun articleChanges(table: String) = listOf(
      """UPDATE "$table" SET "title" = 'Arrakis' WHERE "id" = 1""",
      """DELETE FROM "$table" WHERE "id" = 2""",
      """INSERT INTO "$table" ("id", "title", "body") VALUES (3, 'Solaris', 'ocean')"""
    )

    fun text(name: String) = ColumnStructure(
      name = name,
      sqlType = "TEXT",
      schema = """"$name" TEXT"""
    )

    fun table(
      name: String,
      columns: List<ColumnStructure>
    ) = TableStructure(
      name = name,
      schema = "CREATE TABLE IF NOT EXISTS ${quoteSqlIdentifier(name)} " +
          "(${columns.joinToString(transform = ColumnStructure::schema)})",
      columns = columns
    )

    fun TableStructure.withColumns(columns: List<ColumnStructure>) = table(
      name = name,
      columns = columns
    )

    fun TableStructure.renamed(name: String) = table(
      name = name,
      columns = columns
    )

    fun index(
      table: String,
      columns: List<String>,
      name: String = "${table}_fts"
    ) = FullTextIndexStructure.from(
      FullTextIndexElement(
        identity = fullTextIndexIdentity(name),
        tableName = table,
        rowIdColumnName = "id",
        columns = columns.map { column ->
          FullTextColumnElement(
            columnName = column,
            role = INDEXED
          )
        },
        tokenize = "",
        prefix = emptyList(),
        detail = FULL,
        columnSize = true
      )
    )

    fun structure(
      vararg tables: TableStructure,
      indices: List<FullTextIndexStructure> = emptyList()
    ) = DatabaseStructure(
      tables = tables.associateBy(TableStructure::name),
      fullTextIndices = indices.associateBy(FullTextIndexStructure::name)
    )

    fun DatabaseStructure.creationSql() = tables.values.map(TableStructure::schema) +
        fullTextIndices.values.flatMap { listOf(it.tableSql) + it.triggers.values }

    fun List<String>.objectNames(prefix: String) = filter { it.startsWith(prefix) }.map { statement ->
      statement
        .removePrefix("$prefix\"")
        .substringBefore('"')
    }

    fun SQLiteConnection.matches(indices: Set<String>) = indices
      .flatMap { index ->
        execSQL("""INSERT INTO main."$index"("$index", rank) VALUES ('integrity-check', 1)""")
        SEARCHED_TERMS.map { term ->
          "$index $term" to longs("""SELECT rowid FROM "$index" WHERE "$index" MATCH '$term' ORDER BY rowid""")
        }
      }
      .filter { (_, rowIds) -> rowIds.isNotEmpty() }
      .toMap()

    fun SQLiteConnection.schemaRows() = prepare(
      "SELECT type || ' ' || name || ' ' || tbl_name || ' ' || IFNULL(sql, '') FROM sqlite_master ORDER BY name"
    ).use { statement ->
      buildList {
        while (statement.step()) {
          add(statement.getText(0))
        }
      }
    }

    fun SQLiteConnection.longs(sql: String) = prepare(sql).use { statement ->
      buildList {
        while (statement.step()) {
          add(statement.getLong(0))
        }
      }
    }
  }
}
