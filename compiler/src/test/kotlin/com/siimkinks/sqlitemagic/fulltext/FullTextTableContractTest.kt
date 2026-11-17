package com.siimkinks.sqlitemagic.fulltext

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.TYPE_WITNESSES
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
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
    compile(consumer = "")
      .isOk()
      .withGeneratedSource("ArticleFtsTable.kt") {
        it.assertContains(
          "public class ArticleFtsTable private constructor(\n  alias: String?,\n) : Fts5Table(name = " +
              "\"article_fts\", alias = alias, contentTable = ArticleTable.ARTICLE, contentRowIdColumnName = \"id\")",
          "public val TITLE: FullTextColumn<String, Required, String, String, Required, String> =\n" +
              "      FullTextColumn(\n        table = fieldTable,\n        name = \"title\",",
          "public val AUTHOR_NAME:\n      " +
              "FullTextColumn<String, SqlNullable, String?, String?, SqlNullable, String?> =",
          "public val ROWID: Column<Nothing, Long, Required, Long, Long?, SqlNullable, Long?> =",
          "name = \"rowid\",",
          "override infix fun AS(alias: String): ArticleFtsTable = ArticleFtsTable(alias)",
          "public val ARTICLE_FTS: ArticleFtsTable = ArticleFtsTable(null)"
        )
      }
      .withGeneratedSource("NoteSearchTable.kt") {
        it.assertContains(
          "contentTable = NoteTable.NOTE, contentRowIdColumnName = \"row_id\")",
          "public val ROWID: Column<Nothing, Long, Required, Long, Long?, SqlNullable, Long?> =",
          "public val NOTE_SEARCH: NoteSearchTable = NoteSearchTable(null)"
        )
      }
  }

  @Test
  fun `index objects search, rank and compare row IDs with their tables`() {
    compile(
      consumer = """
        fun searches(connection: DbConnection, query: String) {
          val articles: List<Article> = SELECT
            .FROM(ARTICLE)
            .WHERE(ARTICLE_FTS MATCH query)
            .ORDER_BY(ARTICLE_FTS.RANK.ASC())
            .usingConnection(connection)
            .execute()
          val titles: List<String> = SELECT
            .COLUMN(ARTICLE.TITLE)
            .FROM(ARTICLE)
            .WHERE(ARTICLE_FTS.TITLE MATCH ARTICLE.TITLE.asParameter("query"))
            .usingConnection(connection)
            .execute()
          val indexed: List<Row3<Long, String, Double?>> = SELECT
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
          val hits: List<Article> = (SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite OR room") ORDER_BY
              ARTICLE_FTS.RANK.ASC())
            .usingConnection(connection)
            .execute()
          val inTitles: List<String> = (SELECT COLUMN ARTICLE.TITLE FROM ARTICLE WHERE
              (ARTICLE_FTS.TITLE MATCH "sqlite"))
            .usingConnection(connection)
            .execute()
          val explicit = SELECT
            .FROM(ARTICLE)
            .INNER_JOIN(ARTICLE_FTS)
            .ON(ARTICLE_FTS.ROWID IS ARTICLE.ID)
          val removed = DELETE FROM ARTICLE WHERE (ARTICLE.ID IN (SELECT COLUMN ARTICLE_FTS.ROWID FROM ARTICLE_FTS
              WHERE (ARTICLE_FTS MATCH query)))
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
    compile(consumer = "fun invalid() { val statement = $statement }").assertCompilationError(diagnostic)
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

  private fun compile(consumer: String) = compiler.compile(
    SourceFile.kotlin(
      name = "Models.kt",
      contents = """
        package $PACKAGE

        import com.siimkinks.sqlitemagic.annotation.Embedded
        import com.siimkinks.sqlitemagic.annotation.FullText
        import com.siimkinks.sqlitemagic.annotation.FullTextIndex
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
        @FullTextIndex("note_search")
        data class Note(@Id val id: String, @RowId val rowId: Long? = null, @FullText("note_search") val body: String)
      """
    ),
    SourceFile.kotlin(
      name = "Consumer.kt",
      contents = """
        package consumer

        import com.siimkinks.sqlitemagic.ArticleFtsTable.Companion.ARTICLE_FTS
        import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
        import com.siimkinks.sqlitemagic.DELETE
        import com.siimkinks.sqlitemagic.DbConnection
        import com.siimkinks.sqlitemagic.IN
        import com.siimkinks.sqlitemagic.INSERT
        import com.siimkinks.sqlitemagic.IS
        import com.siimkinks.sqlitemagic.NoteSearchTable.Companion.NOTE_SEARCH
        import com.siimkinks.sqlitemagic.NoteTable.Companion.NOTE
        import com.siimkinks.sqlitemagic.Row3
        import com.siimkinks.sqlitemagic.SELECT
        import com.siimkinks.sqlitemagic.UPDATE
        import $PACKAGE.Article

        $consumer
      """
    ),
    TYPE_WITNESSES
  )

  companion object {
    private const val SHARED_NAME = "they map to the same generated name"
    private const val LIBRARY_CLASS = "is already declared on the classpath"

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
        "actual type is 'Pair<FullTextColumn<String"
      )
    )
  }
}
