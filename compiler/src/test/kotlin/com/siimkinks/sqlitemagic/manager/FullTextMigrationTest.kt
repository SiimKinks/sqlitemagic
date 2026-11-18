package com.siimkinks.sqlitemagic.manager

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder.ASC
import com.siimkinks.sqlitemagic.annotation.FullTextRole.INDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextVersion
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS5
import com.siimkinks.sqlitemagic.fulltext.FullTextColumnElement
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.fulltext.fullTextIndexIdentity
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class FullTextMigrationTest {
  @ParameterizedTest(name = "{0}")
  @EnumSource(FullTextVersion::class)
  fun `migrations keep full-text indexes in sync with their tables and equal to a fresh install`(
    version: FullTextVersion
  ) {
    val articleIndex = index(
      table = "article",
      columns = listOf("title", "body"),
      version = version
    )
    val commentIndex = index(
      table = "comment",
      columns = listOf("text"),
      version = version
    )
    val noteIndex = index(
      table = "note",
      columns = listOf("text"),
      version = version
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
              columns = listOf("title"),
              version = version
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "switching the version of an index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = when (version) {
                FTS5 -> FTS4
                FTS4 -> FTS5
              }
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
          indices = listOf(commentIndex)
        ),
        current = structure(
          ARTICLE_WITHOUT_AUTHOR,
          COMMENT,
          indices = listOf(commentIndex)
        )
      ),
      "renaming the content table" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = version,
              name = "search"
            )
          )
        ),
        current = structure(
          POST,
          indices = listOf(
            index(
              table = "post",
              columns = listOf("title", "body"),
              version = version,
              name = "search"
            )
          )
        )
      ),
      "renaming the content table of an index with the default name" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(
          POST,
          indices = listOf(
            index(
              table = "post",
              columns = listOf("title", "body"),
              version = version
            )
          )
        )
      ),
      "renaming an index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = version,
              name = "search"
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "renaming an index by case" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = version,
              name = "Search"
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = version,
              name = "search"
            )
          )
        )
      ),
      "adding an index next to a changed one" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title"),
              version = version,
              name = "search"
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = version,
              name = "search"
            ),
            articleIndex
          )
        )
      ),
      "removing the content table" to Change(
        previous = structure(
          ARTICLE,
          NOTE,
          indices = listOf(noteIndex)
        ),
        current = structure(ARTICLE)
      ),
      "adding columns to the content table" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(
          quotedMigrationTable(
            name = "article",
            columns = ARTICLE.columns + ColumnStructure(
              name = "year",
              sqlType = "INTEGER",
              schema = """"year" INTEGER"""
            )
          ),
          indices = listOf(articleIndex)
        )
      ),
      "rebuilding another table" to Change(
        previous = structure(
          ARTICLE,
          quotedMigrationTable(
            name = "note",
            columns = NOTE.columns + text("extra")
          ),
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
          indices = listOf(noteIndex)
        )
      ),
      "filling an added table with an index in the script" to Change(
        previous = structure(ARTICLE),
        current = structure(
          ARTICLE,
          NOTE,
          indices = listOf(noteIndex)
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
    fun articleMatches(index: String) = articleMatches.mapKeys { (key, _) ->
      key.replace(
        oldValue = "article_fts",
        newValue = index
      )
    }
    val articleRebuildWarning = recreatedFullTextIndexWarning(
      index = "article_fts",
      table = "article"
    )
    val expected = mapOf(
      "adding an index" to MigratedIndices(
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf("Migration rebuilds full-text index article_fts from all rows of article.")
      ),
      "removing an index" to MigratedIndices(dropped = listOf("article_fts")),
      "changing an index" to MigratedIndices(
        dropped = listOf("article_fts"),
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf(articleRebuildWarning)
      ),
      "switching the version of an index" to MigratedIndices(
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
          recreatedFullTextIndexWarning(
            index = "comment_fts",
            table = "comment"
          )
        )
      ),
      "renaming the content table" to MigratedIndices(
        dropped = listOf("search"),
        created = listOf("search"),
        rebuilt = listOf("search"),
        matches = articleMatches("search"),
        warnings = listOf(
          recreatedFullTextIndexWarning(
            index = "search",
            table = "post"
          )
        )
      ),
      "renaming the content table of an index with the default name" to MigratedIndices(
        dropped = listOf("article_fts"),
        created = listOf("post_fts"),
        rebuilt = listOf("post_fts"),
        matches = articleMatches("post_fts"),
        warnings = listOf(
          recreatedFullTextIndexWarning(
            index = "post_fts",
            table = "post"
          )
        )
      ),
      "renaming an index" to MigratedIndices(
        dropped = listOf("search"),
        created = listOf("article_fts"),
        rebuilt = listOf("article_fts"),
        matches = articleMatches,
        warnings = listOf(articleRebuildWarning)
      ),
      "renaming an index by case" to MigratedIndices(
        dropped = listOf("Search"),
        created = listOf("search"),
        rebuilt = listOf("search"),
        matches = articleMatches("search"),
        warnings = listOf(
          recreatedFullTextIndexWarning(
            index = "search",
            table = "article"
          )
        )
      ),
      "adding an index next to a changed one" to MigratedIndices(
        dropped = listOf("search"),
        created = listOf("search", "article_fts"),
        rebuilt = listOf("search", "article_fts"),
        matches = articleMatches("search") + articleMatches,
        warnings = listOf(
          recreatedFullTextIndexWarning(
            index = "search",
            table = "article"
          ),
          "Migration rebuilds full-text index article_fts from all rows of article."
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

    assertThat(cases.mapValues { (_, change) -> change.migrate(version) }).isEqualTo(expected)
  }

  @Test
  fun `migration SQL drops, creates and rebuilds indexes around the table statements`() {
    val articleIndex = index(
      table = "article",
      columns = listOf("title", "body"),
      version = FTS5
    )
    val titleIndex = index(
      table = "article",
      columns = listOf("title"),
      version = FTS5
    )
    val cases = mapOf(
      "adding an FTS5 index to an existing table" to Change(
        previous = structure(ARTICLE),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "removing an FTS5 index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(ARTICLE)
      ),
      "changing an FTS5 index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(titleIndex)
        ),
        current = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        )
      ),
      "rebuilding the content table of an FTS5 index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(articleIndex)
        ),
        current = structure(
          ARTICLE_WITHOUT_AUTHOR,
          indices = listOf(articleIndex)
        )
      ),
      "changing an FTS4 index" to Change(
        previous = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title"),
              version = FTS4
            )
          )
        ),
        current = structure(
          ARTICLE,
          indices = listOf(
            index(
              table = "article",
              columns = listOf("title", "body"),
              version = FTS4
            )
          )
        )
      )
    )
    val fts5Drops = listOf(
      """DROP TRIGGER IF EXISTS main."article_fts_ai"""",
      """DROP TRIGGER IF EXISTS main."article_fts_ad"""",
      """DROP TRIGGER IF EXISTS main."article_fts_au"""",
      """DROP TABLE IF EXISTS main."article_fts""""
    )
    val fts5Creates = listOf(
      """CREATE VIRTUAL TABLE IF NOT EXISTS main."article_fts" USING fts5("title", "body", content='article', """ +
          """content_rowid='id')""",
      """CREATE TRIGGER IF NOT EXISTS main."article_fts_ai" AFTER INSERT ON "article" BEGIN INSERT INTO """ +
          """"article_fts"(rowid, "title", "body") VALUES (new."id", new."title", new."body"); END""",
      """CREATE TRIGGER IF NOT EXISTS main."article_fts_ad" AFTER DELETE ON "article" BEGIN INSERT INTO """ +
          """"article_fts"("article_fts", rowid, "title", "body") VALUES ('delete', old."id", old."title", """ +
          """old."body"); END""",
      """CREATE TRIGGER IF NOT EXISTS main."article_fts_au" AFTER UPDATE OF "id", "title", "body", rowid, """ +
          """oid, _rowid_ ON "article" WHEN old."id" IS NOT new."id" OR old."title" IS NOT new."title" OR """ +
          """old."body" IS NOT new."body" BEGIN INSERT INTO "article_fts"("article_fts", rowid, "title", """ +
          """"body") VALUES ('delete', old."id", old."title", old."body"); INSERT INTO "article_fts"(rowid, """ +
          """"title", "body") VALUES (new."id", new."title", new."body"); END""",
      """INSERT INTO main."article_fts"("article_fts") VALUES ('rebuild')"""
    )

    assertThat(
      cases.mapValues { (_, change) ->
        planSchemaMigration(
          previous = change.previous,
          current = change.current
        ).statements
      }
    ).isEqualTo(
      mapOf(
        "adding an FTS5 index to an existing table" to fts5Creates,
        "removing an FTS5 index" to fts5Drops,
        "changing an FTS5 index" to fts5Drops + fts5Creates,
        "rebuilding the content table of an FTS5 index" to fts5Drops + listOf(
          """ALTER TABLE "article" RENAME TO "article_"""",
          """CREATE TABLE IF NOT EXISTS "article" ("id" INTEGER PRIMARY KEY, "title" TEXT, "body" TEXT)""",
          """INSERT INTO "article" ("id","title","body") SELECT "id","title","body" FROM "article_"""",
          """DROP TABLE IF EXISTS "article_""""
        ) + fts5Creates,
        "changing an FTS4 index" to listOf(
          """DROP TRIGGER IF EXISTS main."article_fts_ai"""",
          """DROP TRIGGER IF EXISTS main."article_fts_bd"""",
          """DROP TRIGGER IF EXISTS main."article_fts_bu"""",
          """DROP TRIGGER IF EXISTS main."article_fts_au"""",
          """DROP TABLE IF EXISTS main."article_fts"""",
          """CREATE VIRTUAL TABLE IF NOT EXISTS main."article_fts" USING fts4("title", "body", content='article')""",
          """CREATE TRIGGER IF NOT EXISTS main."article_fts_ai" AFTER INSERT ON "article" BEGIN INSERT INTO """ +
              """"article_fts"(docid, "title", "body") VALUES (new."id", new."title", new."body"); END""",
          """CREATE TRIGGER IF NOT EXISTS main."article_fts_bd" BEFORE DELETE ON "article" BEGIN DELETE FROM """ +
              """"article_fts" WHERE docid = old."id"; END""",
          """CREATE TRIGGER IF NOT EXISTS main."article_fts_bu" BEFORE UPDATE OF "id", "title", "body", rowid, """ +
              """oid, _rowid_ ON "article" WHEN old."id" IS NOT new."id" OR old."title" IS NOT new."title" OR """ +
              """old."body" IS NOT new."body" BEGIN DELETE FROM "article_fts" WHERE docid = old."id"; END""",
          """CREATE TRIGGER IF NOT EXISTS main."article_fts_au" AFTER UPDATE OF "id", "title", "body", rowid, """ +
              """oid, _rowid_ ON "article" WHEN old."id" IS NOT new."id" OR old."title" IS NOT new."title" OR """ +
              """old."body" IS NOT new."body" BEGIN INSERT INTO "article_fts"(docid, "title", "body") VALUES """ +
              """(new."id", new."title", new."body"); END""",
          """INSERT INTO main."article_fts"("article_fts") VALUES ('rebuild')"""
        )
      )
    )
  }

  private data class Change(
    val previous: DatabaseStructure,
    val current: DatabaseStructure,
    val statementsBeforeIndices: List<String> = emptyList()
  ) {
    fun migrate(version: FullTextVersion): MigratedIndices {
      val migration = planSchemaMigration(
        previous = previous,
        current = current
      )
      val freshSchemaRows = BundledSQLiteDriver().open(":memory:").use { fresh ->
        current.creationSql().forEach(fresh::execSQL)
        fresh.schemaRows()
      }
      return BundledSQLiteDriver().open(":memory:").use { connection ->
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
        MigratedIndices(
          dropped = migration.statements.objectNames(prefix = "DROP TABLE IF EXISTS main."),
          created = migration.statements.objectNames(prefix = CREATE_INDEX_PREFIX),
          rebuilt = migration.statements
            .filter { it.endsWith("VALUES ('rebuild')") }
            .objectNames(prefix = "INSERT INTO main."),
          matches = connection.matches(
            indices = current.fullTextIndices.keys,
            version = version
          ),
          matchesFreshInstall = connection.schemaRows() == freshSchemaRows,
          warnings = migration.warnings
        )
      }
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
    val ARTICLE = quotedMigrationTable(
      name = "article",
      columns = listOf(ID, text("title"), text("body"), text("author"))
    )
    val POST = quotedMigrationTable(
      name = "post",
      columns = ARTICLE.columns
    )
    val ARTICLE_WITHOUT_AUTHOR = quotedMigrationTable(
      name = "article",
      columns = ARTICLE.columns.dropLast(1)
    )
    val COMMENT = quotedMigrationTable(
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
    val NOTE = quotedMigrationTable(
      name = "note",
      columns = listOf(ID, text("text"))
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

    fun index(
      table: String,
      columns: List<String>,
      version: FullTextVersion,
      name: String = "${table}_fts"
    ) = FullTextIndexStructure.from(
      FullTextIndexElement(
        identity = fullTextIndexIdentity(name),
        tableName = table,
        rowIdColumnName = "id",
        version = version,
        columns = columns.map { column ->
          FullTextColumnElement(
            columnName = column,
            role = INDEXED
          )
        },
        tokenize = "",
        prefix = emptyList(),
        detail = FULL,
        columnSize = true,
        matchinfo = FullTextMatchinfo.FTS4,
        order = ASC
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

    fun List<String>.objectNames(prefix: String) = filter { it.startsWith(prefix) }
      .map { statement ->
        statement
          .removePrefix("""$prefix"""")
          .substringBefore('"')
      }

    fun SQLiteConnection.matches(
      indices: Set<String>,
      version: FullTextVersion
    ) = indices
      .flatMap { index ->
        execSQL(
          when (version) {
            FTS5 -> """INSERT INTO main."$index"("$index", rank) VALUES ('integrity-check', 1)"""
            FTS4 -> """INSERT INTO main."$index"("$index") VALUES ('integrity-check')"""
          }
        )
        SEARCHED_TERMS.map { term ->
          "$index $term" to longs("""SELECT rowid FROM "$index" WHERE "$index" MATCH '$term' ORDER BY rowid""")
        }
      }
      .filter { (_, rowIds) -> rowIds.isNotEmpty() }
      .toMap()
  }
}
