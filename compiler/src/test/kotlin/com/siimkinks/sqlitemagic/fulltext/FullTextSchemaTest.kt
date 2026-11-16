package com.siimkinks.sqlitemagic.fulltext

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.manager.genClassesManagerProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessorCompilationResult
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class FullTextSchemaTest {
  @Test
  fun `keeps every index in sync with its table`() {
    val cases = mapOf(
      "inserts" to listOf(
        """INSERT INTO "article" ("id", "title", "body", "author") VALUES (1, 'Dune', 'desert planet', 'Frank')""",
        """INSERT INTO "article" ("title", "body", "author") VALUES ('Emma', 'novel', 'Jane')"""
      ),
      "updates with unchanged values" to listOf(
        ARTICLE,
        """UPDATE "article" SET "title"='Dune', "body"='desert planet', "author"='Frank' WHERE "id"=1""",
        """UPDATE "article" SET "author"='F. Herbert' WHERE "id"=1"""
      ),
      "updates of indexed columns" to listOf(
        ARTICLE,
        """UPDATE "article" SET "title"='Arrakis', "body"='desert planet', "author"='Frank' WHERE "id"=1"""
      ),
      "updates of the row ID" to listOf(
        ARTICLE,
        """UPDATE "article" SET rowid=7 WHERE "id"=1"""
      ),
      "updates of the row ID as oid" to listOf(
        ARTICLE,
        """UPDATE "article" SET oid=7 WHERE "id"=1"""
      ),
      "updates of the row ID as _rowid_" to listOf(
        ARTICLE,
        """UPDATE "article" SET _rowid_=7 WHERE "id"=1"""
      ),
      "updates of the ID column" to listOf(
        ARTICLE,
        """UPDATE "article" SET "ID"=7 WHERE "id"=1"""
      ),
      "upserts of indexed columns" to listOf(
        ARTICLE,
        """INSERT INTO "article" ("id", "title", "body", "author") VALUES (1, 'Arrakis', 'sand', 'Frank') """ +
            """ON CONFLICT ("id") DO UPDATE SET "title"=excluded."title""""
      ),
      "upserts of other columns" to listOf(
        ARTICLE,
        """INSERT INTO "article" ("id", "title", "body", "author") VALUES (1, 'Arrakis', 'sand', 'Frank') """ +
            """ON CONFLICT ("id") DO UPDATE SET "author"=excluded."author""""
      ),
      "deletes" to listOf(
        ARTICLE,
        """DELETE FROM "article" WHERE "id"=1"""
      ),
      "replaces" to listOf(
        ARTICLE,
        """INSERT OR REPLACE INTO "article" ("id", "title", "body", "author") VALUES (1, 'Emma', 'novel', 'Jane')"""
      ),
      "stemmed inserts" to listOf(
        ARTICLE,
        """INSERT INTO "comment" ("article", "text") VALUES (1, 'Dune again')"""
      ),
      "cascades" to listOf(
        ARTICLE,
        """INSERT INTO "comment" ("article", "text") VALUES (1, 'Dune again')""",
        """DELETE FROM "article" WHERE "id"=1"""
      ),
      "cascades of replaced rows" to listOf(
        ARTICLE,
        """INSERT INTO "comment" ("article", "text") VALUES (1, 'Dune again')""",
        """INSERT OR REPLACE INTO "article" ("id", "title", "body", "author") VALUES (1, 'Emma', 'novel', 'Jane')"""
      ),
      "replaces in a table with a declared row ID" to listOf(
        """INSERT INTO "note" ("id", "title", "tag") VALUES ('a', 'Dune', 'books'), ('b', 'Emma', 'books')""",
        """INSERT OR REPLACE INTO "note" ("id", "title", "tag") VALUES ('a', 'Dunes', 'sand')""",
        """UPDATE "note" SET "title"='Emma', "tag"='novels' WHERE "id"='b'"""
      )
    )

    assertThat(cases.mapValues { (_, statements) -> database().openWith(statements).matches() }).isEqualTo(
      mapOf(
        "inserts" to mapOf("article_fts dune" to listOf(1L), "article_fts emma" to listOf(2L)),
        "updates with unchanged values" to mapOf("article_fts dune" to listOf(1L)),
        "updates of indexed columns" to mapOf("article_fts arrakis" to listOf(1L)),
        "updates of the row ID" to mapOf("article_fts dune" to listOf(7L)),
        "updates of the row ID as oid" to mapOf("article_fts dune" to listOf(7L)),
        "updates of the row ID as _rowid_" to mapOf("article_fts dune" to listOf(7L)),
        "updates of the ID column" to mapOf("article_fts dune" to listOf(7L)),
        "upserts of indexed columns" to mapOf("article_fts arrakis" to listOf(1L)),
        "upserts of other columns" to mapOf("article_fts dune" to listOf(1L)),
        "deletes" to emptyMap(),
        "replaces" to mapOf("article_fts emma" to listOf(1L)),
        "stemmed inserts" to mapOf("article_fts dune" to listOf(1L), "comment_fts dunes" to listOf(1L)),
        "cascades" to emptyMap(),
        "cascades of replaced rows" to mapOf("article_fts emma" to listOf(1L)),
        "replaces in a table with a declared row ID" to mapOf(
          "note_fts dunes" to listOf(3L),
          "note_fts emma" to listOf(2L)
        )
      )
    )
  }

  @Test
  fun `clears the data of indexed tables and keeps indexing new rows with their settings`() {
    val database = database()
    val connection = database.openWith(
      listOf(
        ARTICLE,
        """INSERT INTO "comment" ("article", "text") VALUES (1, 'Dune again')""",
        """INSERT INTO "article_fts"("article_fts", rank) VALUES ('rank', 'bm25(10.0, 1.0)')"""
      )
    )
    database.clearData(connection)
    connection.execSQL("""INSERT INTO "article" ("title", "body", "author") VALUES ('Emma', 'novel', 'Jane')""")
    val rank = connection
      .prepare("""SELECT v FROM "article_fts_config" WHERE k = 'rank'""")
      .use { statement -> statement.takeIf(SQLiteStatement::step)?.getText(0) }

    assertThat(connection.matches() to rank).isEqualTo(mapOf("article_fts emma" to listOf(2L)) to "bm25(10.0, 1.0)")
  }

  @Test
  fun `keeps the indexes in the main schema beside temporary tables of their names`() {
    val database = SqliteMagicCompiler(::genClassesManagerProcessingSteps)
      .compile(
        SourceFile.kotlin(
          name = "Drafts.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.FullText
            import com.siimkinks.sqlitemagic.annotation.FullTextIndex
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table
            @FullTextIndex
            data class Article(
              @Id val id: Long? = null,
              @FullText val title: String,
              @FullText val body: String,
              val author: String
            )

            @Table("article", options = [TEMPORARY])
            data class ArticleDraft(@Id val id: Long? = null, val title: String)

            @Table("article_fts", options = [TEMPORARY])
            data class SearchDraft(@Id val id: Long? = null, val term: String)
          """
        )
      )
      .isOk()
      .database()
    val connection = database.openWith(
      listOf(
        """INSERT INTO main."article" ("id", "title", "body", "author") VALUES (1, 'Dune', 'desert', 'Frank')""",
        """INSERT INTO temp."article" ("title") VALUES ('Draft')""",
        """INSERT INTO temp."article_fts" ("term") VALUES ('dune')"""
      )
    )
    database.clearData(connection)
    listOf(
      """INSERT INTO main."article" ("title", "body", "author") VALUES ('Emma', 'novel', 'Jane')""",
      """INSERT INTO temp."article_fts" ("term") VALUES ('emma')"""
    ).forEach(connection::execSQL)

    assertThat(connection.matches(mapOf("article_fts" to listOf("dune", "emma"))))
        .isEqualTo(mapOf("article_fts emma" to listOf(2L)))
  }

  private fun GeneratedDatabase.openWith(statements: List<String>) = BundledSQLiteDriver()
    .open(":memory:")
    .also { connection ->
      applySchemaPragmas(connection)
      createSchema(connection)
      createTemporarySchema(connection)
      statements.forEach(connection::execSQL)
    }

  private fun SQLiteConnection.matches(searchedTerms: Map<String, List<String>> = SEARCHED_TERMS) = searchedTerms
    .flatMap { (index, terms) ->
      execSQL("""INSERT INTO main."$index"("$index", rank) VALUES ('integrity-check', 1)""")
      terms.map { term ->
        "$index $term" to rowIds(
          index = index,
          term = term
        )
      }
    }
    .filter { (_, rowIds) -> rowIds.isNotEmpty() }
    .toMap()

  private fun SQLiteConnection.rowIds(
    index: String,
    term: String
  ) = prepare("""SELECT rowid FROM main."$index" WHERE "$index" MATCH ? ORDER BY rowid""").use { statement ->
    statement.bindText(
      index = 1,
      value = term
    )
    buildList {
      while (statement.step()) {
        add(statement.getLong(0))
      }
    }
  }

  private companion object {
    const val ARTICLE =
      """INSERT INTO "article" ("id", "title", "body", "author") VALUES (1, 'Dune', 'desert planet', 'Frank')"""

    val SEARCHED_TERMS = mapOf(
      "article_fts" to listOf("dune", "emma", "arrakis"),
      "comment_fts" to listOf("dunes"),
      "note_fts" to listOf("dunes", "emma", "books")
    )

    private val GENERATED by lazy {
      SqliteMagicCompiler(::genClassesManagerProcessingSteps)
        .compile(
          SourceFile.kotlin(
            name = "Articles.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.FullTextDetail.COLUMN
              import com.siimkinks.sqlitemagic.annotation.FullText
              import com.siimkinks.sqlitemagic.annotation.FullTextIndex
              import com.siimkinks.sqlitemagic.annotation.FullTextRole.UNINDEXED
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.RowId
              import com.siimkinks.sqlitemagic.annotation.Table

              @Table
              @FullTextIndex
              data class Article(
                @Id val id: Long? = null,
                @FullText val title: String,
                @FullText val body: String,
                val author: String
              )

              @Table
              @FullTextIndex(tokenize = "porter unicode61", detail = COLUMN)
              data class Comment(
                @Id val id: Long? = null,
                @Column(onDeleteCascade = true) val article: Article,
                @FullText val text: String
              )

              @Table
              @FullTextIndex(prefix = [2, 3], columnSize = false)
              data class Note(
                @Id val id: String,
                @RowId val rowId: Long? = null,
                @FullText val title: String,
                @FullText(role = UNINDEXED) val tag: String
              )
            """
          )
        )
        .isOk()
    }

    fun database() = GENERATED.database()

    fun ProcessorCompilationResult.database() = loadClass("com.siimkinks.sqlitemagic.SqliteMagicDatabase")
      .getConstructor()
      .newInstance() as GeneratedDatabase
  }
}
