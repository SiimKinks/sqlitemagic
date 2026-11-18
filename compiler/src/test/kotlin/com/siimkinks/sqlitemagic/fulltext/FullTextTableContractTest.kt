package com.siimkinks.sqlitemagic.fulltext

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class FullTextTableContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generates an index object with its columns and the row ID of its table`() {
    GENERATED
      .withGeneratedSource("ArticleFtsTable.kt") {
        it.assertContainsUnwrapped(
          """public class ArticleFtsTable private constructor(alias: String?) : Fts5Table(name = "article_fts", """ +
              """alias = alias, contentTable = ArticleTable.ARTICLE, contentRowIdColumnName = "id")""",
          "public val TITLE: Fts5Column<String, Required, String, String, Required, String> = Fts5Column(" +
              """table = fieldTable, name = "title", position = 0,""",
          "public val AUTHOR_NAME: Fts5Column<String, SqlNullable, String?, String?, SqlNullable, String?> = " +
              """Fts5Column(table = fieldTable, name = "author_name", position = 1,""",
          "public val ROWID: Column<Nothing, Long, Required, Long, Long?, SqlNullable, Long?> =",
          """name = "rowid",""",
          "override infix fun AS(alias: String): ArticleFtsTable = ArticleFtsTable(alias)",
          "public val ARTICLE_FTS: ArticleFtsTable = ArticleFtsTable(null)"
        )
      }
      .withGeneratedSource("LetterFtsTable.kt") {
        it.assertContainsUnwrapped(
          """public class LetterFtsTable private constructor(alias: String?) : Fts4Table(name = "letter_fts", """ +
              """alias = alias, contentTable = LetterTable.LETTER, contentRowIdColumnName = "id")""",
          "public val SUBJECT: Fts4Column<String, Required, String, String, Required, String> = Fts4Column(" +
              """table = fieldTable, name = "subject", position = 0,""",
          "public val TAG: Fts4Column<String, SqlNullable, String?, String?, SqlNullable, String?> = " +
              """Fts4Column(table = fieldTable, name = "tag", position = 1,""",
          "public val LANGUAGE: Column<Nothing, Int, Required, Int, Int, Required, Int> = Column(" +
              """table = fieldTable, name = "language", nullness = Required,""",
          "public val LETTER_FTS: LetterFtsTable = LetterFtsTable(null)"
        )
      }
      .withGeneratedSource("NoteSearchTable.kt") {
        it.assertContainsUnwrapped(
          """contentTable = NoteTable.NOTE, contentRowIdColumnName = "row_id")""",
          """name = "body", position = 1,""",
          "public val ROWID: Column<Nothing, Long, Required, Long, Long?, SqlNullable, Long?> =",
          "public val NOTE_SEARCH: NoteSearchTable = NoteSearchTable(null)"
        )
      }
  }

  @Test
  fun `index objects search, rank and compare row IDs with their tables`() {
    compileConsumer(
      """
        fun searches(connection: DbConnection, query: String) {
          val articles = SELECT
            .FROM(ARTICLE)
            .WHERE(ARTICLE_FTS MATCH query)
            .ORDER_BY(ARTICLE_FTS.RANK.ASC())
            .usingConnection(connection)
            .execute()
          val titles = SELECT
            .COLUMN(ARTICLE.TITLE)
            .FROM(ARTICLE)
            .WHERE(ARTICLE_FTS.TITLE MATCH ARTICLE.TITLE.asParameter("query"))
            .usingConnection(connection)
            .execute()
          val indexed = SELECT
            .COLUMNS(ARTICLE_FTS.ROWID, ARTICLE_FTS.TITLE, ARTICLE_FTS.RANK)
            .FROM(ARTICLE_FTS)
            .WHERE(ARTICLE_FTS MATCH query)
            .usingConnection(connection)
            .execute()
          val found = ARTICLE_FTS AS "found"
          val joined = SELECT
            .FROM(ARTICLE)
            .INNER_JOIN(found)
            .ON(found.ROWID IS ARTICLE.ID)
            .WHERE(found MATCH query)
          val deleted = DELETE
            .FROM(ARTICLE)
            .WHERE(ARTICLE.ID IN (SELECT COLUMN ARTICLE_FTS.ROWID FROM ARTICLE_FTS WHERE (ARTICLE_FTS MATCH query)))
          val notes = SELECT
            .FROM(NOTE)
            .WHERE(NOTE.ROW_ID IN (SELECT COLUMN NOTE_SEARCH.ROWID FROM NOTE_SEARCH WHERE (NOTE_SEARCH MATCH query)))
          val hits = (SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite OR room") ORDER_BY
              ARTICLE_FTS.RANK.ASC())
            .usingConnection(connection)
            .execute()
          val inTitles = (SELECT COLUMN ARTICLE.TITLE FROM ARTICLE WHERE (ARTICLE_FTS.TITLE MATCH "sqlite"))
            .usingConnection(connection)
            .execute()
          val explicit = SELECT
            .FROM(ARTICLE)
            .INNER_JOIN(ARTICLE_FTS)
            .ON(ARTICLE_FTS.ROWID IS ARTICLE.ID)
          val removed = DELETE FROM ARTICLE WHERE (ARTICLE.ID IN (SELECT COLUMN ARTICLE_FTS.ROWID FROM ARTICLE_FTS
              WHERE (ARTICLE_FTS MATCH query)))
          val results = SELECT
            .COLUMNS(
              HIGHLIGHT(ARTICLE_FTS.TITLE, "<b>", "</b>"),
              HIGHLIGHT(ARTICLE_FTS.AUTHOR_NAME, "<b>", "</b>"),
              SNIPPET(ARTICLE_FTS, "<b>", "</b>", "…", 10),
              SNIPPET(ARTICLE_FTS.AUTHOR_NAME, "<b>", "</b>", "…", 10)
            )
            .FROM(ARTICLE)
            .WHERE(ARTICLE_FTS MATCH ARTICLE_FTS.terms(query, prefix = true))
            .ORDER_BY(BM25(ARTICLE_FTS.TITLE to 10.0, ARTICLE_FTS.AUTHOR_NAME to 2.0).ASC(), BM25(ARTICLE_FTS).ASC())
            .usingConnection(connection)
            .execute()
          val snippets = (SELECT COLUMN SNIPPET(ARTICLE_FTS.TITLE, "<b>", "</b>", "…", 10) FROM ARTICLE
              WHERE (ARTICLE_FTS MATCH ARTICLE_FTS.terms(query)) ORDER_BY BM25(ARTICLE_FTS.TITLE to 10.0).ASC())
            .usingConnection(connection)
            .execute()
          val noteHits = SELECT
            .COLUMN(HIGHLIGHT(NOTE_SEARCH.BODY, "<b>", "</b>"))
            .FROM(NOTE)
            .WHERE(NOTE_SEARCH MATCH query)
            .usingConnection(connection)
            .execute()
          val documented = (
              SELECT.COLUMNS(
                  ARTICLE.ID,
                  HIGHLIGHT(ARTICLE_FTS.TITLE, "<b>", "</b>"),
                  SNIPPET(ARTICLE_FTS.AUTHOR_NAME, "<b>", "</b>", "…", 10)
              )
                  FROM ARTICLE
                  WHERE (ARTICLE_FTS MATCH ARTICLE_FTS.terms(query, prefix = true))
                  ORDER_BY BM25(ARTICLE_FTS.TITLE to 10.0).ASC()
                  LIMIT 20
              )
            .usingConnection(connection)
            .execute()
          val changes = listOf(ARTICLE_FTS.rebuild(), ARTICLE_FTS.optimize(), ARTICLE_FTS.integrityCheck())
            .map { it.usingConnection(connection).execute() }
          val letters = SELECT
            .COLUMNS(
              SNIPPET(LETTER_FTS.SUBJECT, "<b>", "</b>", "…", 10),
              SNIPPET(LETTER_FTS, "<b>", "</b>", "…", 10),
              OFFSETS(LETTER_FTS),
              MATCHINFO(LETTER_FTS),
              MATCHINFO(LETTER_FTS, "pcnalx")
            )
            .FROM(LETTER)
            .WHERE((LETTER_FTS MATCH LETTER_FTS.terms(query, prefix = true)) AND (LETTER_FTS.LANGUAGE IS 1))
            .usingConnection(connection)
            .execute()
          val letterRows = SELECT
            .COLUMNS(LETTER_FTS.ROWID, LETTER_FTS.TAG, LETTER_FTS.LANGUAGE)
            .FROM(LETTER_FTS)
            .WHERE(LETTER_FTS.SUBJECT MATCH query)
            .usingConnection(connection)
            .execute()
          val memos = (
              SELECT.COLUMNS(MEMO, SNIPPET(MEMO_FTS.TEXT, "<b>", "</b>", "…", 10), OFFSETS(MEMO_FTS))
                  FROM MEMO
                  WHERE ((MEMO_FTS MATCH MEMO_FTS.terms(query, prefix = true)) AND (MEMO_FTS.LANGUAGE IS 1))
              )
            .usingConnection(connection)
            .execute()
          val letterChanges = listOf(
            LETTER_FTS.rebuild(),
            LETTER_FTS.optimize(),
            LETTER_FTS.integrityCheck()
          ).map { it.usingConnection(connection).execute() }

          val articlesType = articles.exactType()
          val exactArticles: Exact<List<Article>> = articlesType
          val titlesType = titles.exactType()
          val exactTitles: Exact<List<String>> = titlesType
          val indexedType = indexed.exactType()
          val exactIndexed: Exact<List<Row3<Long, String, Double?>>> = indexedType
          val hitsType = hits.exactType()
          val exactHits: Exact<List<Article>> = hitsType
          val inTitlesType = inTitles.exactType()
          val exactInTitles: Exact<List<String>> = inTitlesType
          val resultsType = results.exactType()
          val exactResults: Exact<List<Row4<String, String?, String?, String?>>> = resultsType
          val snippetsType = snippets.exactType()
          val exactSnippets: Exact<List<String>> = snippetsType
          val noteHitsType = noteHits.exactType()
          val exactNoteHits: Exact<List<String>> = noteHitsType
          val documentedType = documented.exactType()
          val exactDocumented: Exact<List<Row3<Long, String, String?>>> = documentedType
          val changesType = changes.exactType()
          val exactChanges: Exact<List<Int>> = changesType
          val lettersType = letters.exactType()
          val exactLetters: Exact<List<Row5<String, String?, String, ByteArray, ByteArray>>> = lettersType
          val letterRowsType = letterRows.exactType()
          val exactLetterRows: Exact<List<Row3<Long, String?, Int>>> = letterRowsType
          val memosType = memos.exactType()
          val exactMemos: Exact<List<Row3<Memo, String, String>>> = memosType
          val letterChangesType = letterChanges.exactType()
          val exactLetterChanges: Exact<List<Int>> = letterChangesType
        }
      """
    ).isOk()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrograms")
  fun `index objects have no entity and take no writes`(
    label: String,
    statement: String,
    diagnostic: String
  ) {
    compileConsumer("fun invalid() { val statement = $statement }").assertCompilationError(diagnostic)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generatedNameCollisions")
  fun `generated names that an index shares with another declaration are rejected`(
    label: String,
    models: String,
    diagnostics: List<String>
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "Models.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.FullText
            import com.siimkinks.sqlitemagic.annotation.FullTextIndex
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            $models
          """
        )
      )
      .assertCompilationError(*diagnostics.toTypedArray())
  }

  private fun compileConsumer(consumer: String) = PlainKotlinCompilation.compile(
    SourceFile.kotlin(
      name = "Consumer.kt",
      contents = """
        package consumer

        import com.siimkinks.sqlitemagic.AND
        import com.siimkinks.sqlitemagic.ArticleFtsTable.Companion.ARTICLE_FTS
        import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
        import com.siimkinks.sqlitemagic.BM25
        import com.siimkinks.sqlitemagic.DELETE
        import com.siimkinks.sqlitemagic.DbConnection
        import com.siimkinks.sqlitemagic.HIGHLIGHT
        import com.siimkinks.sqlitemagic.IN
        import com.siimkinks.sqlitemagic.INSERT
        import com.siimkinks.sqlitemagic.IS
        import com.siimkinks.sqlitemagic.LetterFtsTable.Companion.LETTER_FTS
        import com.siimkinks.sqlitemagic.LetterTable.Companion.LETTER
        import com.siimkinks.sqlitemagic.MATCHINFO
        import com.siimkinks.sqlitemagic.MemoFtsTable.Companion.MEMO_FTS
        import com.siimkinks.sqlitemagic.MemoTable.Companion.MEMO
        import com.siimkinks.sqlitemagic.NoteSearchTable.Companion.NOTE_SEARCH
        import com.siimkinks.sqlitemagic.NoteTable.Companion.NOTE
        import com.siimkinks.sqlitemagic.OFFSETS
        import com.siimkinks.sqlitemagic.Row3
        import com.siimkinks.sqlitemagic.Row4
        import com.siimkinks.sqlitemagic.Row5
        import com.siimkinks.sqlitemagic.SELECT
        import com.siimkinks.sqlitemagic.SNIPPET
        import com.siimkinks.sqlitemagic.UPDATE
        import $PACKAGE.Article
        import $PACKAGE.Memo
        import typewitness.Exact
        import typewitness.exactType

        $consumer
      """
    ),
    classpaths = listOf(GENERATED.outputDirectory)
  )

  companion object {
    private const val SHARED_NAME = "they map to the same generated name"
    private const val LIBRARY_CLASS = "is already declared on the classpath"

    private val GENERATED by lazy {
      SqliteMagicCompiler(::modelProcessingSteps)
        .compile(
          SourceFile.kotlin(
            name = "Models.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.FullText
              import com.siimkinks.sqlitemagic.annotation.FullTextIndex
              import com.siimkinks.sqlitemagic.annotation.FullTextRole
              import com.siimkinks.sqlitemagic.annotation.FullTextVersion
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.RowId
              import com.siimkinks.sqlitemagic.annotation.Table

              data class Byline(@FullText val name: String?)

              @Table
              @FullTextIndex
              data class Article(
                @Id val id: Long? = null,
                @FullText val title: String,
                @Embedded(prefix = "author_") val author: Byline,
                val pages: Int
              )

              @Table
              @FullTextIndex(version = FullTextVersion.FTS4)
              data class Letter(
                @Id val id: Long? = null,
                @FullText val subject: String,
                @FullText(role = FullTextRole.UNINDEXED) val tag: String?,
                @FullText(role = FullTextRole.LANGUAGE_ID) val language: Int
              )

              @Table
              @FullTextIndex(version = FullTextVersion.FTS4, tokenize = "unicode61")
              data class Memo(
                @Id val id: Long? = null,
                @FullText val text: String,
                @FullText(role = FullTextRole.LANGUAGE_ID) val language: Int
              )

              @Table
              @FullTextIndex("note_search")
              data class Note(
                @Id val id: String,
                @RowId val rowId: Long? = null,
                @FullText("note_search", role = FullTextRole.UNINDEXED) val tag: String,
                @FullText("note_search") val body: String
              )
            """
          )
        )
        .isOk()
    }

    @JvmStatic
    fun generatedNameCollisions() = listOf(
      Arguments.of(
        "indexes of two tables",
        """
          @Table @FullTextIndex("book_fts") data class Book(@Id val id: Long, @FullText val title: String)
          @Table @FullTextIndex("bookFts") data class Shelf(@Id val id: Long, @FullText val name: String)
        """,
        listOf(
          "$SHARED_NAME 'BookFts'",
          "full-text index 'book_fts' of '$PACKAGE.Book'",
          "full-text index 'bookFts' of '$PACKAGE.Shelf'"
        )
      ),
      Arguments.of(
        "index and its own table",
        """
          @Table("books") @FullTextIndex("book") data class Book(@Id val id: Long, @FullText val title: String)
        """,
        listOf(
          "Cannot generate code for '$PACKAGE.Book', full-text index 'book' of '$PACKAGE.Book': $SHARED_NAME 'Book'"
        )
      ),
      Arguments.of(
        "index and another table",
        """
          @Table @FullTextIndex data class Article(@Id val id: Long, @FullText val title: String)
          @Table("article_search") data class ArticleFts(@Id val id: Long)
        """,
        listOf(
          "$SHARED_NAME 'ArticleFts'",
          "'$PACKAGE.ArticleFts'",
          "full-text index 'article_fts' of '$PACKAGE.Article'"
        )
      ),
      Arguments.of(
        "table and a runtime class",
        """
          @Table data class FullText(@Id val id: Long)
        """,
        listOf(
          "Cannot generate code for '$PACKAGE.FullText': its generated class " +
              "'com.siimkinks.sqlitemagic.FullTextTable' $LIBRARY_CLASS. Rename the declaration or an enclosing class."
        )
      ),
      Arguments.of(
        "index and a runtime class",
        """
          @Table @FullTextIndex("fts5") data class Book(@Id val id: Long, @FullText("fts5") val title: String)
        """,
        listOf(
          "Cannot generate code for full-text index 'fts5' of '$PACKAGE.Book': its generated class " +
              "'com.siimkinks.sqlitemagic.Fts5Table' $LIBRARY_CLASS. Rename the full-text index."
        )
      )
    )

    @JvmStatic
    fun invalidPrograms() = listOf(
      Arguments.of(
        "entity query",
        "SELECT FROM ARTICLE_FTS",
        "None of the following candidates is applicable"
      ),
      Arguments.of(
        "insert",
        "INSERT INTO ARTICLE_FTS",
        "Argument type mismatch: actual type is 'ArticleFtsTable', " +
            "but 'Table<uninferred P (of fun <P> INTO)>' was expected"
      ),
      Arguments.of(
        "update",
        "UPDATE TABLE ARTICLE_FTS",
        "Argument type mismatch: actual type is 'ArticleFtsTable', " +
            "but 'Table<uninferred P (of fun <P> TABLE)>' was expected"
      ),
      Arguments.of(
        "delete",
        "DELETE FROM ARTICLE_FTS",
        "Argument type mismatch: actual type is 'ArticleFtsTable', " +
            "but 'Table<uninferred P (of fun <P> FROM)>' was expected"
      ),
      Arguments.of(
        "update of the table through an index column",
        """UPDATE TABLE ARTICLE SET (ARTICLE_FTS.TITLE to "Dune")""",
        "actual type is 'Pair<Fts5Column<String"
      ),
      Arguments.of(
        "highlight of an FTS4 column",
        """HIGHLIGHT(LETTER_FTS.SUBJECT, "<b>", "</b>")""",
        "Argument type mismatch: actual type is 'Fts4Column<String, Required, String, String, Required, String>', " +
            "but 'Fts5Column<*, Required, *, *, *, *>' was expected"
      ),
      Arguments.of(
        "bm25 of an FTS4 index",
        "BM25(LETTER_FTS)",
        "Argument type mismatch: actual type is 'LetterFtsTable', but 'Fts5Table' was expected"
      ),
      Arguments.of(
        "rank of an FTS4 index",
        "LETTER_FTS.RANK",
        "Unresolved reference 'RANK'"
      ),
      Arguments.of(
        "offsets of an FTS5 index",
        "OFFSETS(ARTICLE_FTS)",
        "Argument type mismatch: actual type is 'ArticleFtsTable', but 'Fts4Table' was expected"
      ),
      Arguments.of(
        "match on the language ID",
        """LETTER_FTS.LANGUAGE MATCH "x"""",
        "Unresolved reference 'MATCH'"
      ),
      Arguments.of(
        "snippet of the language ID",
        """SNIPPET(LETTER_FTS.LANGUAGE, "<b>", "</b>", "…", 10)""",
        "None of the following candidates is applicable"
      ),
      Arguments.of(
        "highlight of a nullable column read as required",
        """(SELECT COLUMN HIGHLIGHT(ARTICLE_FTS.AUTHOR_NAME, "<b>", "</b>") FROM ARTICLE).execute().first().length""",
        "Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'"
      )
    )
  }
}
