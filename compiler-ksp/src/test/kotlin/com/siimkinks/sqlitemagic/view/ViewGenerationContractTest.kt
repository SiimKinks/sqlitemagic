package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewGenerationContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `generates a read-only table and DAO surface for a scalar view`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ScalarView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
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
                val query: CompiledSelect<Author, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
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
          "class AuthorSummaryTable",
          "Table<AuthorSummary>",
          "AUTHOR_NAME",
          "RATING",
          "override fun `as`(",
          "mapper =",
          "QUERY"
        )
        generatedSource.assertDoesNotContain(
          "ignoredLabel",
          "AuthorSummaryColumn",
          "SqliteMagic_AuthorSummary_Adapter"
        )
      }
      .withGeneratedSource("SqliteMagic_AuthorSummary_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "object SqliteMagic_AuthorSummary_Dao",
          "shallowObjectFromCursorPosition",
          "AuthorSummary(",
          "authorName =",
          "rating =",
          "QUERY",
          "Selected columns did not contain",
          "columnIndex"
        )
        generatedSource.assertContainsInOrder(
          "AuthorSummary(",
          "authorName =",
          "rating ="
        )
        generatedSource.assertDoesNotContain(
          "bindToInsertStatement",
          "bindToUpdateStatement",
          "DeleteBuilder",
          "InsertBuilder",
          "newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `reads transformed scalar leaves through the registered transformer`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TransformedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1
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
                val query: CompiledSelect<String, Select1> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "EmailColumn.kt",
        "SqliteMagic_EmailView_Dao.kt",
        "EmailViewTable.kt"
      )
      .withGeneratedSource("SqliteMagic_EmailView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "stringToEmail",
          "email =",
          "shallowObjectFromCursorPosition"
        )
      }
  }

  @Test
  fun `constructs mutable views while preserving nullable defaults and ignored members`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MutableView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
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
            class MutableAuthorSummary {
              @ViewColumn("name")
              var name: String = "default-name"

              @ViewColumn("rating")
              var rating: Int? = 42

              @IgnoreColumn
              var cache: String = "not mapped"

              companion object {
                @ViewQuery
                val query: CompiledSelect<Author, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_MutableAuthorSummary_Dao.kt")
      .withGeneratedSource("SqliteMagic_MutableAuthorSummary_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "MutableAuthorSummary().apply",
          "this.name =",
          "this.rating =",
          "cursor.isNull",
          "columnIndex"
        )
        generatedSource.assertDoesNotContain("this.cache =")
      }
  }

  @Test
  fun `keeps traversal and observation metadata at the generated query boundary`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "JoinedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Table
            data class Author(
              @Id val id: Long,
              val name: String
            )

            @Table
            data class Magazine(
              @Id val id: Long,
              val authorId: Long,
              val title: String
            )

            @View
            data class JoinedSummary(
              @ViewColumn("author.name") val authorName: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Magazine, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_JoinedSummary_Dao.kt",
        "JoinedSummaryTable.kt"
      )
      .withGeneratedSource("JoinedSummaryTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "Table<JoinedSummary>",
          "AUTHOR_NAME",
          "viewDefinition = { SqliteMagic_JoinedSummary_Dao.QUERY }",
          "mapper =",
          "createMapper(",
          """viewIdentifier = alias ?: "joined_summary"""",
          "private fun createMapper(",
          "queryDeep: Boolean",
          "queryDeep ->",
          "SqliteMagic_JoinedSummary_Dao::fullObjectFromCursorPosition",
          "SqliteMagic_JoinedSummary_Dao::shallowObjectFromCursorPosition",
          "SqliteMagic_JoinedSummary_Dao.fullObjectFromCursorPosition(" +
              "it, columnPositions, tableGraphNodeNames, viewIdentifier",
          "SqliteMagic_JoinedSummary_Dao.shallowObjectFromCursorPosition(" +
              "it, columnPositions, tableGraphNodeNames, viewIdentifier"
        )
      }
      .withGeneratedSource("SqliteMagic_JoinedSummary_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "shallowObjectFromCursorPosition",
          "fullObjectFromCursorPosition",
          "tableGraphNodeNames",
          "author",
          "SqlUtil.viewDefinition(query = JoinedSummary.query)"
        )
        generatedSource.assertDoesNotContain("viewName =")
        generatedSource.assertDoesNotContain("title =")
      }
  }

  @Test
  fun `keeps an opaque compiled definition behind an explicit runtime boundary`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "OpaqueDefinitionView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledCountSelect
            import com.siimkinks.sqlitemagic.CompiledCursorSelect
            import com.siimkinks.sqlitemagic.CompiledFirstSelect
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.ListQueryObservable
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            private object OpaqueDefinition : CompiledSelect<Any, SelectN> {
              override fun execute(): List<Any> = TODO()
              override fun observe(): ListQueryObservable<Any> = TODO()
              override fun takeFirst(): CompiledFirstSelect<Any, SelectN> = TODO()
              override fun count(): CompiledCountSelect<SelectN> = TODO()
              override fun toCursor(): CompiledCursorSelect<Any, SelectN> = TODO()
            }

            @View
            data class OpaqueView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Any, SelectN> = OpaqueDefinition
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_OpaqueView_Dao.kt")
      .withGeneratedSource("SqliteMagic_OpaqueView_Dao.kt") { generatedSource ->
        generatedSource.assertContains("QUERY", "SqlUtil.viewDefinition(query = OpaqueView.query)")
        generatedSource.assertDoesNotContain("CompiledSelectImpl", "CompiledSelect1Impl")
      }
  }

  @Test
  fun `rejects a view that does not declare a defining query`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MissingViewQuery.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn

            @View
            data class MissingViewQuery(
              @ViewColumn("value") val value: String
            )
          """
        )
      )
      .assertCompilationError()
  }

  @Test
  fun `rejects a view with more than one defining query`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "MultipleViewQueries.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            data class MultipleViewQueries(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val first: CompiledSelect<Any, SelectN> = null as CompiledSelect<Any, SelectN>

                @ViewQuery
                val second: CompiledSelect<Any, SelectN> = null as CompiledSelect<Any, SelectN>
              }
            }
          """
        )
      )
      .assertCompilationError()
  }

  @Test
  fun `rejects a defining query with the wrong declared type`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "WrongViewQueryType.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @View
            data class WrongViewQueryType(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val query: String = "not a compiled select"
              }
            }
          """
        )
      )
      .assertCompilationError()
  }
}
