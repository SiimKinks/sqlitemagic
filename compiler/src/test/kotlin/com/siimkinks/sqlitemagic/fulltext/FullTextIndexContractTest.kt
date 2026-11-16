package com.siimkinks.sqlitemagic.fulltext

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextRole.INDEXED
import com.siimkinks.sqlitemagic.manager.DatabaseStructureJson
import com.siimkinks.sqlitemagic.manager.FullTextIndexStructure
import com.siimkinks.sqlitemagic.manager.genClassesManagerProcessingSteps
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.debugMigrationOptions
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText

internal class FullTextIndexContractTest {
  private val compiler = SqliteMagicCompiler(::genClassesManagerProcessingSteps)

  @Test
  fun `creates each full-text index and its triggers after the indexes`() {
    compile(
      """
        @Table
        @FullTextIndex
        data class Article(
          @Id val id: Long? = null,
          @FullText val title: String,
          @FullText val body: String,
          @Index val author: String
        )
      """
    )
      .isOk()
      .withGeneratedSource(MANAGER) { generatedSource ->
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder(
            """CREATE INDEX IF NOT EXISTS main.\"index_article_author\" ON \"article\" (\"author\")""",
            """CREATE VIRTUAL TABLE IF NOT EXISTS main.\"article_fts\" USING fts5(\"title\", \"body\", """ +
                """content='article', content_rowid='id')""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_ai\" AFTER INSERT ON \"article\" BEGIN """ +
                """INSERT INTO \"article_fts\"(rowid, \"title\", \"body\") VALUES (new.\"id\", new.\"title\", """ +
                """new.\"body\"); END""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_ad\" AFTER DELETE ON \"article\" BEGIN """ +
                """INSERT INTO \"article_fts\"(\"article_fts\", rowid, \"title\", \"body\") VALUES ('delete', """ +
                """old.\"id\", old.\"title\", old.\"body\"); END""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_au\" AFTER UPDATE OF \"id\", \"title\", """ +
                """\"body\", rowid, oid, _rowid_ ON \"article\" WHEN old.\"id\" IS NOT new.\"id\" OR """ +
                """old.\"title\" IS NOT new.\"title\" OR old.\"body\" IS NOT new.\"body\" BEGIN """ +
                """INSERT INTO \"article_fts\"(\"article_fts\", rowid, \"title\", \"body\") VALUES ('delete', """ +
                """old.\"id\", old.\"title\", old.\"body\"); INSERT INTO \"article_fts\"(rowid, \"title\", """ +
                """\"body\") VALUES (new.\"id\", new.\"title\", new.\"body\"); END"""
          )
        generatedSource
          .functionBody("applySchemaPragmas")
          .assertContainsInOrder("PRAGMA recursive_triggers = ON")
        generatedSource
          .functionBody("clearTables")
          .unwrapped()
          .assertContainsInOrder(
            """DROP TRIGGER IF EXISTS main.\"article_fts_ai\"""",
            """DROP TRIGGER IF EXISTS main.\"article_fts_ad\"""",
            """DROP TRIGGER IF EXISTS main.\"article_fts_au\"""",
            """DELETE FROM main.\"article\"""",
            """INSERT INTO main.\"article_fts\"(\"article_fts\") VALUES ('delete-all')""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_ai\"""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_ad\"""",
            """CREATE TRIGGER IF NOT EXISTS main.\"article_fts_au\""""
          )
        generatedSource
          .functionBody("clearTables")
          .assertDoesNotContain("DROP TABLE", "CREATE VIRTUAL TABLE")
      }
  }

  @Test
  fun `turns on recursive triggers only for a schema with a full-text index`() {
    compile(
      """
        @Table
        data class Article(
          @Id val id: Long? = null,
          val title: String
        )
      """
    )
      .isOk()
      .withGeneratedSource(MANAGER) {
        it.assertDoesNotContain("recursive_triggers", "fts5")
      }
  }

  @Test
  fun `records each full-text index in the database structure`(@TempDir directory: Path) {
    compile(
      declarations = """
        @Table
        @FullTextIndex(columnSize = false)
        data class Article(@Id val id: Long? = null, @FullText val title: String, val author: String)
      """,
      kspOptions = debugMigrationOptions(
        projectDirectory = directory,
        migrateDebug = false,
        structureOutputDirectory = directory
      )
    ).isOk()

    assertThat(DatabaseStructureJson.read(directory.resolve("latest.struct").readText()).fullTextIndices).isEqualTo(
      mapOf(
        "article_fts" to FullTextIndexStructure.from(
          FullTextIndexElement(
            identity = fullTextIndexIdentity("article_fts"),
            tableName = "article",
            rowIdColumnName = "id",
            columns = listOf(
              FullTextColumnElement(
                columnName = "title",
                role = INDEXED
              )
            ),
            tokenize = "",
            prefix = emptyList(),
            detail = FULL,
            columnSize = false
          )
        )
      )
    )
  }

  @Test
  fun `ignores embedded columns of indexes their table does not declare`() {
    compile(
      """
        @Table
        @FullTextIndex
        data class Doc(@Id val id: Long? = null, @Embedded(prefix = "details_") val details: Details)

        @Table
        data class Note(@Id val id: Long? = null, @Embedded(prefix = "details_") val details: Details)
      """
    )
      .isOk()
      .withGeneratedSource(MANAGER) { generatedSource ->
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder("""USING fts5(\"details_text\", content='doc', content_rowid='id')""")
        generatedSource.assertDoesNotContain("note_fts")
      }
  }

  @Test
  fun `reads repeated memberships of an embedded value from a compiled library`() {
    val library = PlainKotlinCompilation
      .compile(
        fileName = "Passage.kt",
        contents = """
          package library

          import com.siimkinks.sqlitemagic.annotation.FullText

          data class Passage(@FullText @FullText("passage_texts") val text: String)
        """
      )
      .isOk()

    compile(
      declarations = """
        @Table
        @FullTextIndex
        @FullTextIndex("passage_texts")
        data class Doc(@Id val id: Long? = null, @Embedded val passage: library.Passage)
      """,
      classpaths = listOf(library.outputDirectory)
    )
      .isOk()
      .withGeneratedSource(MANAGER) {
        it
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder(
            """main.\"doc_fts\" USING fts5(\"text\", content='doc', content_rowid='id')""",
            """main.\"passage_texts\" USING fts5(\"text\", content='doc', content_rowid='id')"""
          )
      }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("indexDefinitions")
  fun `renders the index definition`(
    label: String,
    declaration: String,
    virtualTables: List<String>
  ) {
    compile(declaration)
      .isOk()
      .withGeneratedSource(MANAGER) {
        it
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder(*virtualTables.toTypedArray())
      }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidIndexes")
  fun `rejects invalid full-text indexes`(
    label: String,
    declaration: String,
    diagnostic: String
  ) {
    compile(declaration).assertCompilationError(diagnostic)
  }

  private fun compile(
    declarations: String,
    classpaths: List<File> = emptyList(),
    kspOptions: Map<String, String> = emptyMap()
  ) = compiler.compile(
    SourceFile.kotlin(
      name = "Models.kt",
      contents = """
        package $PACKAGE

        import com.siimkinks.sqlitemagic.SelectQuery
        import com.siimkinks.sqlitemagic.annotation.Column
        import com.siimkinks.sqlitemagic.annotation.Embedded
        import com.siimkinks.sqlitemagic.annotation.FullText
        import com.siimkinks.sqlitemagic.annotation.FullTextDetail
        import com.siimkinks.sqlitemagic.annotation.FullTextIndex
        import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
        import com.siimkinks.sqlitemagic.annotation.FullTextOrder
        import com.siimkinks.sqlitemagic.annotation.FullTextRole
        import com.siimkinks.sqlitemagic.annotation.FullTextVersion
        import com.siimkinks.sqlitemagic.annotation.Id
        import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
        import com.siimkinks.sqlitemagic.annotation.Index
        import com.siimkinks.sqlitemagic.annotation.RowId
        import com.siimkinks.sqlitemagic.annotation.Table
        import com.siimkinks.sqlitemagic.annotation.TableOption
        import com.siimkinks.sqlitemagic.annotation.View
        import com.siimkinks.sqlitemagic.annotation.ViewColumn
        import com.siimkinks.sqlitemagic.annotation.ViewQuery

        data class Details(@FullText val text: String, val note: String)

        $declarations
      """
    ),
    classpaths = classpaths,
    kspOptions = kspOptions
  )

  companion object {
    private const val MANAGER = "SqliteMagicDatabase.kt"

    @JvmStatic
    fun indexDefinitions() = listOf(
      Arguments.of(
        "tokenizer and prefixes",
        """
          @Table
          @FullTextIndex(tokenize = "porter unicode61 remove_diacritics 2", prefix = [2, 3])
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        listOf(
          """USING fts5(\"title\", content='doc', content_rowid='id', """ +
              """tokenize='porter unicode61 remove_diacritics 2', prefix='2 3')"""
        )
      ),
      Arguments.of(
        "column detail without column sizes",
        """
          @Table
          @FullTextIndex(detail = FullTextDetail.COLUMN, columnSize = false)
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        listOf("""USING fts5(\"title\", content='doc', content_rowid='id', detail=column, columnsize=0)""")
      ),
      Arguments.of(
        "no detail",
        """
          @Table
          @FullTextIndex(detail = FullTextDetail.NONE)
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        listOf("""USING fts5(\"title\", content='doc', content_rowid='id', detail=none)""")
      ),
      Arguments.of(
        "unindexed column left out of the update condition",
        """
          @Table
          @FullTextIndex
          data class Doc(
            @Id val id: Long? = null,
            @FullText(role = FullTextRole.UNINDEXED) val tag: String,
            @FullText val title: String
          )
        """,
        listOf(
          """USING fts5(\"tag\" UNINDEXED, \"title\", content='doc', content_rowid='id')""",
          """AFTER UPDATE OF \"id\", \"title\", rowid, oid, _rowid_ ON \"doc\" WHEN old.\"id\" IS NOT """ +
              """new.\"id\" OR old.\"title\" IS NOT new.\"title\" BEGIN"""
        )
      ),
      Arguments.of(
        "declared row ID",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: String, @RowId val rowId: Long? = null, @FullText val title: String)
        """,
        listOf(
          """USING fts5(\"title\", content='doc', content_rowid='row_id')""",
          """VALUES (new.\"row_id\", new.\"title\")"""
        )
      ),
      Arguments.of(
        "INTEGER ID of another type",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id @Column("doc_id") val id: Int, @FullText val title: String)
        """,
        listOf("""USING fts5(\"title\", content='doc', content_rowid='doc_id')""")
      ),
      Arguments.of(
        "embedded column",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @Embedded(prefix = "details_") val details: Details)
        """,
        listOf("""USING fts5(\"details_text\", content='doc', content_rowid='id')""")
      ),
      Arguments.of(
        "quotes in names",
        """
          @Table("doc's")
          @FullTextIndex("doc's search")
          data class Doc(@Id val id: Long? = null, @FullText("doc's search") val title: String)
        """,
        listOf(
          """CREATE VIRTUAL TABLE IF NOT EXISTS main.\"doc's search\" USING fts5(\"title\", content='doc''s', """ +
              """content_rowid='id')"""
        )
      ),
      Arguments.of(
        "several indexes sharing a column",
        """
          @Table
          @FullTextIndex("doc_titles")
          @FullTextIndex("doc_texts", prefix = [2])
          data class Doc(
            @Id val id: Long? = null,
            @FullText("doc_titles") @FullText("doc_texts") val title: String,
            @FullText("doc_texts") val body: String
          )
        """,
        listOf(
          """CREATE VIRTUAL TABLE IF NOT EXISTS main.\"doc_titles\" USING fts5(\"title\", content='doc', """ +
              """content_rowid='id')""",
          """CREATE VIRTUAL TABLE IF NOT EXISTS main.\"doc_texts\" USING fts5(\"title\", \"body\", content='doc', """ +
              """content_rowid='id', prefix='2')"""
        )
      ),
      Arguments.of(
        "property persisted by @FullText alone",
        """
          @Table(persistAll = false)
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        listOf("""USING fts5(\"title\", content='doc', content_rowid='id')""")
      )
    )

    @JvmStatic
    fun invalidIndexes() = listOf(
      Arguments.of(
        "WITHOUT_ROWID table",
        """
          @Table(options = [TableOption.WITHOUT_ROWID])
          @FullTextIndex
          data class Doc(@Id val id: Long, @FullText val title: String)
        """,
        "@FullTextIndex is not allowed in WITHOUT_ROWID or TEMPORARY tables: Doc"
      ),
      Arguments.of(
        "TEMPORARY table",
        """
          @Table(options = [TableOption.TEMPORARY])
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "@FullTextIndex is not allowed in WITHOUT_ROWID or TEMPORARY tables: Doc"
      ),
      Arguments.of(
        "text ID without a declared row ID",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: String, @FullText val title: String)
        """,
        "A full-text index needs a stable row ID; an @Id that is not stored as INTEGER needs " +
            "`@RowId val rowId: Long? = null` beside it: Doc.id"
      ),
      Arguments.of(
        "no ID",
        """
          @Table
          @FullTextIndex
          data class Doc(@FullText val title: String)
        """,
        "A full-text index needs a stable row ID; a table without an @Id declares it as " +
            "`@Id val id: Long? = null`: Doc"
      ),
      Arguments.of(
        "FTS4",
        """
          @Table
          @FullTextIndex(version = FullTextVersion.FTS4)
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "FTS4 full-text indexes are not supported yet: Doc"
      ),
      Arguments.of(
        "FTS4 matchinfo option",
        """
          @Table
          @FullTextIndex(matchinfo = FullTextMatchinfo.FTS3)
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "FTS5 index 'doc_fts' cannot use the FTS4 option matchinfo: Doc"
      ),
      Arguments.of(
        "FTS4 order option",
        """
          @Table
          @FullTextIndex(order = FullTextOrder.DESC)
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "FTS5 index 'doc_fts' cannot use the FTS4 option order: Doc"
      ),
      Arguments.of(
        "FTS4 language ID role",
        """
          @Table
          @FullTextIndex
          data class Doc(
            @Id val id: Long? = null,
            @FullText val title: String,
            @FullText(role = FullTextRole.LANGUAGE_ID) val language: Int
          )
        """,
        "FTS5 index 'doc_fts' cannot use the FTS4 role LANGUAGE_ID: Doc.language"
      ),
      Arguments.of(
        "column named rank",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String, @FullText val rank: String)
        """,
        "Column name 'rank' is reserved in FTS5 index 'doc_fts': Doc.rank"
      ),
      Arguments.of(
        "column named rowid",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText @Column("RowId") val title: String)
        """,
        "Column name 'RowId' is reserved in FTS5 index 'doc_fts': Doc.title"
      ),
      Arguments.of(
        "column named as the index",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText @Column("doc_fts") val title: String)
        """,
        "Column name 'doc_fts' is reserved in FTS5 index 'doc_fts': Doc.title"
      ),
      Arguments.of(
        "undeclared named index",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String, @FullText("other") val body: String)
        """,
        "@FullText refers to @FullTextIndex 'other', which Doc does not declare: Doc.body"
      ),
      Arguments.of(
        "undeclared unnamed index",
        """
          @Table
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "@FullText refers to the unnamed @FullTextIndex, which Doc does not declare: Doc.title"
      ),
      Arguments.of(
        "column added twice",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText @FullText val title: String)
        """,
        "Duplicate @FullText of full-text index 'doc_fts': Doc.title"
      ),
      Arguments.of(
        "duplicate index names",
        """
          @Table
          @FullTextIndex
          @FullTextIndex("DOC_FTS")
          data class Doc(@Id val id: Long? = null, @FullText val title: String, @FullText("DOC_FTS") val body: String)
        """,
        "Duplicate full-text index name 'doc_fts': Doc"
      ),
      Arguments.of(
        "index without columns",
        """
          @Table
          @FullTextIndex
          @FullTextIndex("empty")
          data class Doc(@Id val id: Long? = null, @FullText val title: String)
        """,
        "Full-text index 'empty' has no @FullText column: Doc"
      ),
      Arguments.of(
        "index without indexed columns",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText(role = FullTextRole.UNINDEXED) val title: String)
        """,
        "Full-text index 'doc_fts' has no INDEXED column: Doc"
      ),
      Arguments.of(
        "ignored column",
        """
          @Table
          @FullTextIndex
          data class Doc(
            @Id val id: Long? = null,
            @FullText val title: String,
            @FullText @IgnoreColumn val body: String = ""
          )
        """,
        "@FullText cannot be combined with @IgnoreColumn: Doc.body"
      ),
      Arguments.of(
        "embedded property",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText @Embedded val details: Details)
        """,
        "@Embedded cannot be combined with physical-column annotations: Doc.details"
      ),
      Arguments.of(
        "index on a class that is not a table",
        """
          @FullTextIndex
          data class Doc(val title: String)
        """,
        "@FullTextIndex is only valid on a @Table: Doc"
      ),
      Arguments.of(
        "index named as another table",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String)

          @Table("doc_fts")
          data class Other(@Id val id: Long? = null)
        """,
        "Full-text index name 'doc_fts' conflicts with table name 'doc_fts': $PACKAGE.Doc"
      ),
      Arguments.of(
        "index named as its table",
        """
          @Table
          @FullTextIndex("doc")
          data class Doc(@Id val id: Long? = null, @FullText("doc") val title: String)
        """,
        "Full-text index name 'doc' conflicts with table name 'doc': $PACKAGE.Doc"
      ),
      Arguments.of(
        "shadow table named as another table",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String)

          @Table("doc_fts_config")
          data class Other(@Id val id: Long? = null)
        """,
        "Full-text shadow table name 'doc_fts_config' conflicts with table name 'doc_fts_config': $PACKAGE.Doc"
      ),
      Arguments.of(
        "indexes of two tables with one name",
        """
          @Table
          @FullTextIndex("search")
          data class Doc(@Id val id: Long? = null, @FullText("search") val title: String)

          @Table
          @FullTextIndex("search")
          data class Other(@Id val id: Long? = null, @FullText("search") val title: String)
        """,
        "Full-text index name 'search' conflicts with full-text index name 'search': $PACKAGE.Other"
      ),
      Arguments.of(
        "reserved index name",
        """
          @Table
          @FullTextIndex("sqlite_search")
          data class Doc(@Id val id: Long? = null, @FullText("sqlite_search") val title: String)
        """,
        "Full-text index name 'sqlite_search' is reserved: $PACKAGE.Doc"
      ),
      Arguments.of(
        "B-tree index named as a full-text index",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText @Index("doc_fts") val title: String)
        """,
        "Index name 'doc_fts' conflicts with full-text index name 'doc_fts'"
      ),
      Arguments.of(
        "view named as a full-text index",
        """
          @Table
          @FullTextIndex
          data class Doc(@Id val id: Long? = null, @FullText val title: String)

          @View("doc_fts_data")
          data class DocView(@ViewColumn("title") val title: String) {
            companion object {
              @ViewQuery
              val QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "View name 'doc_fts_data' conflicts with full-text shadow table name 'doc_fts_data'"
      )
    )
  }
}
