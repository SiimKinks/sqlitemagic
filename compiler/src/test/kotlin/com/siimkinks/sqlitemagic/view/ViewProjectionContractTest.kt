package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNoWarnings
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewProjectionContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @Test
  fun `reconstructs complete same-row projections without relying on identity`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_CompleteProjectionView_Dao.kt")
      .withGeneratedSource("SqliteMagic_CompleteProjectionView_Dao.kt") {
        it.assertContainsUnwrapped(
          "columns = emptyList()",
          """ViewBlock(key = "no_id_row", target = NoIdRowTable.NO_ID_ROW)""",
          """ViewBlock(key = "text_id_row", target = TextIdRowTable.TEXT_ID_ROW)""",
          """ViewBlock(key = "late_id_row", target = LateIdRowTable.LATE_ID_ROW)""",
          "noId = related.read(cursor, 0) as NoIdRow",
          "textId = related.read(cursor, 1) as TextIdRow",
          "lateId = related.read(cursor, 2) as LateIdRow"
        )
      }
  }

  @Test
  fun `reads a complete table projection with nested relationships through one warning-free block`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_RelationshipGraphProjection_Dao.kt")
      .withGeneratedSource("SqliteMagic_RelationshipGraphProjection_Dao.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          """blocks = listOf(ViewBlock(key = "book", target = ProjectionBookTable.PROJECTION_BOOK))""",
          "book = related.read(cursor, 0) as ProjectionBook"
        )
        generatedSource.assertDoesNotContain(
          "ProjectionAuthor"
        )
      }
      .assertNoWarnings("SqliteMagic_RelationshipGraphProjection_Dao.kt")
  }

  @Test
  fun `keeps repeated nullable projections isolated and applies all-null presence`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_RepeatedProjectionView_Dao.kt")
      .withGeneratedSource("SqliteMagic_RepeatedProjectionView_Dao.kt") {
        it.assertContainsUnwrapped(
          """ViewBlock(key = "first_no_id", target = NoIdRowTable.NO_ID_ROW)""",
          """ViewBlock(key = "second_no_id", target = NoIdRowTable.NO_ID_ROW)""",
          "first = related.readOrNull(cursor, 0) as NoIdRow?",
          "second = related.readOrNull(cursor, 1) as NoIdRow?"
        )
      }
  }

  @Test
  fun `composes view field lists with scalar tails, aliased view joins and partial view reads`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ComposedView_Dao.kt",
        "ComposedViewTable.kt",
        "SqliteMagic_PartialView_Dao.kt",
        "PartialViewTable.kt"
      )
      .withGeneratedSource("ComposedViewTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "Table<ComposedView>",
          "val TAIL: Column<ComposedView,",
          "listOf(TAIL)",
          "generatedView = SqliteMagic_ComposedView_Dao.GENERATED_VIEW"
        )
        generatedSource.assertDoesNotContain(
          "entityProperties",
          "readPartialEntity"
        )
      }
      .withGeneratedSource("SqliteMagic_ComposedView_Dao.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          """ViewBlock(key = "base", target = BaseViewTable.BASE_VIEW)""",
          "base = related.read(cursor, 0) as BaseView",
          "tail = "
        )
        generatedSource.assertDoesNotContain(
          "objectFromSelectedPositions"
        )
      }
      .withGeneratedSource("PartialViewTable.kt") {
        it.assertContainsUnwrapped(
          """EntityProperty(path = "PartialView.name", required = true)""",
          "SqliteMagic_PartialView_Dao.objectFromSelectedPositions(cursor, positions)"
        )
      }
      .withGeneratedSource("SqliteMagic_PartialView_Dao.kt") {
        it.assertContains(
          "fun objectFromSelectedPositions(cursor: Cursor, positions: IntArray): PartialView",
          "base = null"
        )
      }
  }

  @Test
  fun `reconstructs a nested view from the same cursor row`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_InnerAuthor_Dao.kt",
        "SqliteMagic_OuterAuthor_Dao.kt"
      )
      .withGeneratedSource("SqliteMagic_OuterAuthor_Dao.kt") {
        it.assertContainsUnwrapped(
          """ViewBlock(key = "inner_author", target = InnerAuthorTable.INNER_AUTHOR)""",
          "`inner` = related.readOrNull(cursor, 0) as InnerAuthor?",
          "fun objectFromCursorPosition("
        )
      }
  }

  @Test
  fun `flattens embedded scalar transformer and model leaves with nested nullable boundaries`() {
    GENERATED
      .isOk()
      .assertGeneratedSources("SqliteMagic_EmbeddedView_Dao.kt", "EmbeddedViewTable.kt")
      .withGeneratedSource("EmbeddedViewTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "val DETAILS_CONTACT_EMAIL_ALIAS:",
          "val DETAILS_CONTACT_CITY:",
          "val OTHER_EMAIL_ALIAS:",
          "val OTHER_CITY:",
          "Column<EmbeddedView, String, SqlNullable, String?, String?, SqlNullable, String?>"
        )
        generatedSource.assertDoesNotContain("DETAILS_AUTHOR")
      }
      .withGeneratedSource("SqliteMagic_EmbeddedView_Dao.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          """columns = listOf("details_contact_email_alias", "details_contact_city", "other_email_alias", """ +
              """"other_city")""",
          """blocks = listOf(ViewBlock(key = "details_author", target = AuthorTable.AUTHOR))""",
          "details = Details(contact = if (column0IsNull && column1IsNull) null else Contact(",
          "EmbeddedEmailTransformer.fromDb(cursor.getString(startIndex))",
          "author = related.readOrNull(cursor, 0) as Author?",
          "other = if (column2IsNull && column3IsNull) null else Contact("
        )
        generatedSource.assertDoesNotContain("ignored =")
      }
  }

  @Test
  fun `reconstructs a complete view leaf inside an embedded container from the same row`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "EmbeddedViewProjectionTable.kt",
        "SqliteMagic_InnerView_Dao.kt",
        "SqliteMagic_EmbeddedViewProjection_Dao.kt"
      )
      .withGeneratedSource("EmbeddedViewProjectionTable.kt") { generatedSource ->
        generatedSource.assertContains("DETAILS_MARKER")
        generatedSource.assertDoesNotContain("DETAILS_INNER")
      }
      .withGeneratedSource("SqliteMagic_EmbeddedViewProjection_Dao.kt") {
        it.assertContainsUnwrapped(
          """columns = listOf("details_marker")""",
          """blocks = listOf(ViewBlock(key = "details_inner", target = InnerViewTable.INNER_VIEW))""",
          "details = NestedDetails(`inner` = related.read(cursor, 0) as InnerView",
          "marker = if (cursor.isNull(startIndex)) throw SQLException("
        )
      }
  }

  @Test
  fun `rejects recursive embedded view shapes`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "RecursiveEmbeddedView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
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
                val query: SelectQuery<Any, *> = error("compile-only")
              }
            }
          """
        )
      )
      .assertCompilationError("Cyclic embedded property graph: RecursiveEmbeddedView.details.child")
  }

  @Test
  fun `rejects collisions between resolved embedded scalar keys`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedViewCollision.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.SelectQuery
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
                val query: SelectQuery<Any, *> = error("compile-only")
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Duplicate @ViewColumn key 'same': EmbeddedViewCollision.left.first, EmbeddedViewCollision.right.second"
      )
  }

  companion object {
    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
            name = "CompleteProjections.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

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
                  val query: SelectQuery<NoIdRow, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "RelationshipGraphProjection.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.Column
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

              @Table
              data class ProjectionAuthor(
                @Id val id: Long,
                val name: String
              )

              @Table
              data class ProjectionBook(
                @Id val id: Long,
                @Column(handleRecursively = true) val author: ProjectionAuthor
              )

              @View
              data class RelationshipGraphProjection(
                @ViewColumn("book") val book: ProjectionBook
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<ProjectionBook, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "RepeatedProjections.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
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
                  val query: SelectQuery<NoIdRow, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "ViewComposition.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.BaseViewTable.Companion.BASE_VIEW
              import com.siimkinks.sqlitemagic.IS
              import com.siimkinks.sqlitemagic.PartialViewTable.Companion.PARTIAL_VIEW
              import com.siimkinks.sqlitemagic.SELECT
              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery
              import com.siimkinks.sqlitemagic.mapTo

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
                  val query: SelectQuery<Author, *> = error("compile-only")
                }
              }

              @View("composed")
              data class ComposedView(
                @ViewColumn("base") val base: BaseView,
                @ViewColumn("tail") val tail: String
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<BaseView, *> = error("compile-only")
                }
              }

              @View("partial")
              data class PartialView(
                @ViewColumn("name") val name: String,
                @ViewColumn("base") val base: BaseView?
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<BaseView, *> = error("compile-only")
                }
              }

              fun requireComposedViewQuerySurface() {
                val left = BASE_VIEW AS "left"
                val right = BASE_VIEW AS "right"
                SELECT
                  .COLUMNS(BASE_VIEW.all(), BASE_VIEW.NAME AS "tail")
                  .FROM(BASE_VIEW)
                  .compile()
                (SELECT COLUMN left.NAME FROM left).compile()
                SELECT
                  .COLUMNS(left.all(), right.all())
                  .FROM(left)
                  .INNER_JOIN(right)
                  .ON(left.NAME IS right.NAME)
                  .compile()
                SELECT
                  .COLUMN(PARTIAL_VIEW.NAME)
                  .FROM(PARTIAL_VIEW)
                  .mapTo(PARTIAL_VIEW)
                  .compile()
              }
            """
          ),
          SourceFile.kotlin(
            name = "NestedViewProjection.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

              @View("inner_author")
              data class InnerAuthor(
                @ViewColumn("name") val name: String
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<Author, *> = error("compile-only")
                }
              }

              @View("outer_author")
              data class OuterAuthor(
                @ViewColumn("inner_author") val inner: InnerAuthor?
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<InnerAuthor, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "EmbeddedView.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
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
                  val query: SelectQuery<Author, *> = error("compile-only")
                }
              }
            """
          ),
          SourceFile.kotlin(
            name = "EmbeddedViewProjection.kt",
            contents = """
              package $PACKAGE

              import com.siimkinks.sqlitemagic.SelectQuery
              import com.siimkinks.sqlitemagic.annotation.Embedded
              import com.siimkinks.sqlitemagic.annotation.Id
              import com.siimkinks.sqlitemagic.annotation.Table
              import com.siimkinks.sqlitemagic.annotation.View
              import com.siimkinks.sqlitemagic.annotation.ViewColumn
              import com.siimkinks.sqlitemagic.annotation.ViewQuery

              @Table
              data class SourceRow(
                @Id val id: Long,
                val name: String
              )

              @View("inner_view")
              data class InnerView(
                @ViewColumn("name") val name: String
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<SourceRow, *> = error("compile-only")
                }
              }

              data class NestedDetails(
                @ViewColumn("inner") val inner: InnerView,
                val marker: String
              )

              @View
              data class EmbeddedViewProjection(
                @Embedded(prefix = "details_") val details: NestedDetails
              ) {
                companion object {
                  @ViewQuery
                  val query: SelectQuery<InnerView, *> = error("compile-only")
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
