package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNotGeneratedSources
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewGenerationContractTest {
  @Test
  fun `generates a read-only table and DAO surface for a scalar view`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AuthorSummary_Dao.kt",
        "AuthorSummaryTable.kt"
      )
      .assertNotGeneratedSources(
        "SqliteMagic_AuthorSummary_Adapter.kt",
        "_AuthorSummary.kt"
      )
      .withGeneratedSource("AuthorSummaryTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "public class AuthorSummaryTable private constructor(",
          """: Table<AuthorSummary>(name = "author_summary", alias = alias, """ +
              "generatedView = SqliteMagic_AuthorSummary_Dao.GENERATED_VIEW)",
          "val AUTHOR_NAME:",
          "Column<AuthorSummary, String, SqlNullable, String?, String, Required, String> = Column(",
          """name = "author.name"""",
          "val RATING: Column<AuthorSummary, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>",
          """name = "rating"""",
          "SqliteMagic_AuthorSummary_Dao.objectFromCursorPosition(cursor, startIndex)",
          "override infix fun AS(alias: String): AuthorSummaryTable",
          "public val AUTHOR_SUMMARY: AuthorSummaryTable"
        )
        generatedSource.assertDoesNotContain("ignoredLabel")
      }
      .withGeneratedSource("SqliteMagic_AuthorSummary_Dao.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          "internal object SqliteMagic_AuthorSummary_Dao",
          "internal val GENERATED_VIEW: GeneratedView = GeneratedView(",
          """viewName = "author_summary"""",
          "temporary = false",
          "query = { AuthorSummary.query }",
          """columns = listOf("author.name", "rating")""",
          "fun objectFromCursorPosition(cursor: Cursor, startIndex: Int): AuthorSummary",
          """Column \"author.name\" was NULL""",
          "cursor.getString(startIndex)",
          "rating = if (cursor.isNull(startIndex + 1)) null else cursor.getInt(startIndex + 1)"
        )
        generatedSource.assertContainsInOrder(
          "AuthorSummary(",
          "authorName =",
          "rating ="
        )
        generatedSource.assertDoesNotContain(
          "related",
          "blocks ="
        )
        assertThat(
          Regex("""AuthorSummary\(""")
            .findAll(generatedSource.functionBody("objectFromCursorPosition"))
            .count()
        ).isEqualTo(1)
        assertThat(
          Regex("""fun objectFromCursorPosition\(""")
            .findAll(generatedSource)
            .count()
        ).isEqualTo(1)
      }
  }

  @Test
  fun `reads transformed scalar leaves through the registered transformer`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "EmailSqlType.kt",
        "SqliteMagic_EmailView_Dao.kt",
        "EmailViewTable.kt"
      )
      .withGeneratedSource("SqliteMagic_EmailView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "fun objectFromCursorPosition(",
          "email = if (cursor.isNull(startIndex)) throw SQLException(",
          "else EmailTransformer.stringToEmail(cursor.getString(startIndex))"
        )
        assertThat(
          Regex("""EmailTransformer\.stringToEmail\(""")
            .findAll(generatedSource.functionBody("objectFromCursorPosition"))
            .count()
        ).isEqualTo(1)
      }
  }

  @Test
  fun `constructs mutable views while preserving nullable defaults and ignored members`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_MutableAuthorSummary_Dao.kt")
      .withGeneratedSource("SqliteMagic_MutableAuthorSummary_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "MutableAuthorSummary().apply",
          "this.name = if (cursor.isNull(startIndex)) throw SQLException(",
          "if (!cursor.isNull(startIndex + 1)) this.rating = cursor.getInt(startIndex + 1)",
          "(related.readOrNull(cursor, 0) as Author?)?.let { this.author = it }"
        )
        generatedSource.assertDoesNotContain("this.cache =")
        generatedSource
          .functionBody("objectFromSelectedPositions")
          .assertDoesNotContain("this.author")
      }
  }

  companion object {
    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "ScalarView.kt",
            contents = """
              package $PACKAGE

              import android.database.Cursor
              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.SqliteMagic_AuthorSummary_Dao
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

              @Table
              data class Author(
                @Id val id: Long,
                val name: String,
                val rating: Int?
              )

              @View
              data class AuthorSummary(
                @ViewColumn("author.name") val authorName: String,
                @ViewColumn("rating") val rating: Int?
              ) {
                val ignoredLabel = "ignored"

                companion object {
                  @ViewQuery
                  val query: SelectQuery<Author, *> = error("compile-only")
                }
              }

              private val positional: (Cursor, Int) -> AuthorSummary =
                SqliteMagic_AuthorSummary_Dao::objectFromCursorPosition
            """
          ),
          SourceFile.kotlin(
            name = "TransformedView.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery
              import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
              import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

              data class Email(val value: String)

              object EmailTransformer {
                @ObjectToDbValue
                fun emailToString(value: Email): String = value.value

                @DbValueToObject
                fun stringToEmail(value: String): Email = Email(value)
              }

              @View
              data class EmailView(
                @ViewColumn("email") val email: Email
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<String, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "MutableView.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

              @View
              class MutableAuthorSummary {
                @ViewColumn("name")
                var name: String = "default-name"

                @ViewColumn("rating")
                var rating: Int? = 42

                @ViewColumn("author")
                var author: Author? = Author(id = 0, name = "default-author", rating = null)

                @IgnoreColumn
                var cache: String = "not mapped"

                companion object {
                  @ViewQuery
                  val query: SelectQuery<Author, *> = error("compile-only")
                }
              }
            """
          ),
          processingStepsFactory = ::viewProcessingSteps
        )
        .generatedSources()
    }
  }
}
