package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewProjectionContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `reconstructs complete same-row projections without relying on identity`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CompleteProjections.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Table("no_id_row")
            data class NoIdRow(
              val message: String,
              val count: Int?
            )

            @Table("text_id_row")
            data class TextIdRow(
              @Id val key: String,
              val label: String
            )

            @Table("late_id_row")
            data class LateIdRow(
              val label: String,
              @Id val id: Long,
              val note: String?
            )

            @View
            data class CompleteProjectionView(
              @ViewColumn("no_id_row") val noId: NoIdRow,
              @ViewColumn("text_id_row") val textId: TextIdRow,
              @ViewColumn("late_id_row") val lateId: LateIdRow
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<NoIdRow, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_CompleteProjectionView_Dao.kt")
      .withGeneratedSource("SqliteMagic_CompleteProjectionView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "noId = NoIdRow(",
          "textId = TextIdRow(",
          "lateId = LateIdRow(",
          "columnOffset.value",
          "cursor.getString"
        )
        generatedSource.assertDoesNotContain(
          "newInstanceWithOnlyId",
          "execute()",
          "observe()"
        )
      }
  }

  @Test
  fun `keeps repeated nullable projections isolated and applies all-null presence`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RepeatedProjections.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Table("no_id_row")
            data class NoIdRow(
              val message: String?,
              val count: Int?
            )

            @View
            data class RepeatedProjectionView(
              @ViewColumn("first_no_id") val first: NoIdRow?,
              @ViewColumn("second_no_id") val second: NoIdRow?
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<NoIdRow, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_RepeatedProjectionView_Dao.kt")
      .withGeneratedSource("SqliteMagic_RepeatedProjectionView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "first_no_id",
          "second_no_id",
          "columnIndex",
          "first = if (",
          "second = if (",
          "NoIdRow("
        )
        generatedSource.assertDoesNotContain("newInstanceWithOnlyId")
      }
  }

  @Test
  fun `reconstructs a nested view from the same cursor row`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NestedViewProjection.kt",
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

            @View("inner_author")
            data class InnerAuthor(
              @ViewColumn("name") val name: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Author, SelectN> = error("compile-only")
              }
            }

            @View("outer_author")
            data class OuterAuthor(
              @ViewColumn("inner_author") val inner: InnerAuthor?
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<InnerAuthor, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_InnerAuthor_Dao.kt",
        "SqliteMagic_OuterAuthor_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_OuterAuthor_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "InnerAuthor(",
          "`inner` =",
          "columnIndex",
          "shallowObjectFromCursorPosition"
        )
        generatedSource.assertDoesNotContain(
          "InnerAuthorTable.INNER_AUTHOR.execute",
          "newInstanceWithOnlyId"
        )
      }
  }

  @Test
  fun `composes full view selections with scalar tails aliases joins and partial selections`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ViewComposition.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.AS
            import com.siimkinks.sqlitemagic.COLUMN
            import com.siimkinks.sqlitemagic.COLUMNS
            import com.siimkinks.sqlitemagic.FROM
            import com.siimkinks.sqlitemagic.INNER_JOIN
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery
            import com.siimkinks.sqlitemagic.BaseViewTable.Companion.BASE

            @Table
            data class Author(
              @Id val id: Long,
              val name: String
            )

            @View("base")
            data class BaseView(
              @ViewColumn("name") val name: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Author, SelectN> = error("compile-only")
              }
            }

            @View("composed")
            data class ComposedView(
              @ViewColumn("base") val base: BaseView,
              @ViewColumn("tail") val tail: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<BaseView, SelectN> = error("compile-only")
              }
            }

            @View("partial")
            data class PartialView(
              @ViewColumn("name") val name: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<BaseView, SelectN> = error("compile-only")
              }
            }

            fun requireComposedViewQuerySurface() {
              val left = BASE.`as`("left")
              val right = BASE.`as`("right")
              (SELECT COLUMNS arrayOf(BASE.all(), BASE.NAME AS "tail") FROM BASE).compile()
              (SELECT COLUMN left.NAME FROM left).compile()
              (SELECT COLUMNS arrayOf(left.all(), right.all())
                FROM left INNER_JOIN right.on(left.NAME.`is`(right.NAME))).compile()
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ComposedView_Dao.kt",
        "ComposedViewTable.kt",
        "SqliteMagic_PartialView_Dao.kt"
      )
      .withGeneratedSource("ComposedViewTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "Table<ComposedView>",
          "TAIL",
          "mapper =",
          "viewDefinition =",
          "QUERY"
        )
      }
      .withGeneratedSource("SqliteMagic_ComposedView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "base = BaseView(",
          "tail =",
          "columnOffset.value",
          "shallowObjectFromCursorPosition",
          "fullObjectFromCursorPosition"
        )
      }
  }

  @Test
  fun `flattens embedded scalar transformer and model leaves with nested nullable boundaries`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class Email(val value: String)

            object EmbeddedEmailTransformer {
              @ObjectToDbValue
              fun toDb(value: Email): String = value.value

              @DbValueToObject
              fun fromDb(value: String): Email = Email(value)
            }

            @Table
            data class Author(
              @Id val id: Long,
              val name: String
            )

            data class Contact(
              @ViewColumn("email_alias") val email: Email,
              val city: String,
              @IgnoreColumn val ignored: String = "ignored"
            )

            data class Details(
              @Embedded(prefix = "contact_") val contact: Contact?,
              @ViewColumn("author") val author: Author?
            )

            @View
            data class EmbeddedView(
              @Embedded(prefix = "details_") val details: Details,
              @Embedded(prefix = "other_") val other: Contact?
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Author, SelectN> = error("compile-only")
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagic_EmbeddedView_Dao.kt", "EmbeddedViewTable.kt")
      .withGeneratedSource("EmbeddedViewTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "DETAILS_CONTACT_EMAIL_ALIAS",
          "DETAILS_CONTACT_CITY",
          "OTHER_EMAIL_ALIAS",
          "OTHER_CITY"
        )
        generatedSource.assertDoesNotContain("DETAILS_AUTHOR")
      }
      .withGeneratedSource("SqliteMagic_EmbeddedView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "Details(",
          "Contact(",
          "fromDb",
          "cursor.isNull",
          "details =",
          "other ="
        )
        generatedSource.assertDoesNotContain("ignored =")
      }
  }

  @Test
  fun `rejects recursive embedded view shapes`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "RecursiveEmbeddedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            data class RecursiveDetails(
              @Embedded val child: RecursiveDetails?
            )

            @View
            data class RecursiveEmbeddedView(
              @Embedded val details: RecursiveDetails
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Any, SelectN> = null as CompiledSelect<Any, SelectN>
              }
            }
          """
        )
      )
      .assertCompilationError()
  }

  @Test
  fun `rejects collisions between resolved embedded scalar keys`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedViewCollision.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            data class FirstDetails(
              @ViewColumn("same") val first: String
            )

            data class SecondDetails(
              @ViewColumn("same") val second: String
            )

            @View
            data class EmbeddedViewCollision(
              @Embedded val left: FirstDetails,
              @Embedded val right: SecondDetails
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<Any, SelectN> = null as CompiledSelect<Any, SelectN>
              }
            }
          """
        )
      )
      .assertCompilationError()
  }
}
