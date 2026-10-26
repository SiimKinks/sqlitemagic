package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.readDriverSupport
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewSelectContractTest {
  @Test
  fun `views define their query with the DSL and read through their generated handles`() {
    val generated = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ViewModels.kt",
          contents = """
            package fixtures.view

            import com.siimkinks.sqlitemagic.AuthorSummaryTable.Companion.AUTHOR_SUMMARY
            import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Table
            data class Author(
              @Id val id: Long? = null,
              val name: String,
              val rating: Int?
            )

            @View("author_summary")
            data class AuthorSummary(
              @ViewColumn("author.id") val id: Long,
              @ViewColumn("author.name") val name: String,
              @ViewColumn("author.rating") val rating: Int?
            ) {
              companion object {
                @ViewQuery
                val QUERY = SELECT FROM AUTHOR WHERE (AUTHOR.NAME LIKE "A%")
              }
            }

            @View("summary_holder")
            data class SummaryHolder(
              @ViewColumn("author_summary") val summary: AuthorSummary?
            ) {
              companion object {
                @ViewQuery
                val QUERY = SELECT FROM AUTHOR_SUMMARY
              }
            }
          """
        ),
        readDriverSupport(),
        SourceFile.kotlin(
          name = "ViewReadDriver.kt",
          contents = """
            package fixtures.view

            import com.siimkinks.sqlitemagic.SqliteMagic_AuthorSummary_Dao
            import com.siimkinks.sqlitemagic.SqliteMagic_SummaryHolder_Dao
            import fixtures.driver.cursor

            object ViewReadDriver {
              @JvmStatic
              fun read(): List<Any> = listOf(
                SqliteMagic_AuthorSummary_Dao.objectFromCursorPosition(
                  cursor = cursor("skipped", 7L, "Ada", 4L),
                  startIndex = 1
                ),
                SqliteMagic_AuthorSummary_Dao.objectFromCursorPosition(
                  cursor = cursor(8L, "Alan", null),
                  startIndex = 0
                ),
                SqliteMagic_AuthorSummary_Dao.objectFromSelectedPositions(
                  cursor = cursor("Ada", 7L),
                  positions = intArrayOf(1, 0, -1)
                ),
                SqliteMagic_SummaryHolder_Dao.objectFromSelectedPositions(
                  cursor = cursor(),
                  positions = intArrayOf()
                )
              )
            }
          """
        ),
        processingStepsFactory = null
      )
      .isOk()
      .withGeneratedSource("SqliteMagic_AuthorSummary_Dao.kt") {
        it.assertContains("query = { AuthorSummary.QUERY }")
      }

    val reader = generated
      .loadClass("fixtures.view.ViewReadDriver")
      .getMethod("read")
    assertThat(reader.invoke(null).toString()).isEqualTo(
      "[AuthorSummary(id=7, name=Ada, rating=4), AuthorSummary(id=8, name=Alan, rating=null), " +
          "AuthorSummary(id=7, name=Ada, rating=null), SummaryHolder(summary=null)]"
    )

    PlainKotlinCompilation
      .compile(
        SourceFile.kotlin(
          name = "ViewConsumer.kt",
          contents = """
            package downstream.view

            import com.siimkinks.sqlitemagic.AuthorSummaryTable
            import com.siimkinks.sqlitemagic.AuthorSummaryTable.Companion.AUTHOR_SUMMARY
            import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
            import com.siimkinks.sqlitemagic.Column
            import com.siimkinks.sqlitemagic.EntitySelection
            import com.siimkinks.sqlitemagic.IS
            import com.siimkinks.sqlitemagic.SELECT
            import com.siimkinks.sqlitemagic.SelectFromStage
            import com.siimkinks.sqlitemagic.SqlNullable
            import fixtures.view.AuthorSummary
            import typewitness.Exact
            import typewitness.exactType

            fun verify() {
              val summaries = SELECT FROM AUTHOR_SUMMARY
              val peer = AUTHOR_SUMMARY AS "peer"
              val names = SELECT COLUMN AUTHOR.NAME FROM AUTHOR INNER_JOIN peer ON (AUTHOR.ID IS peer.AUTHOR_ID)
              val summariesType = summaries.exactType()
              val peerType = peer.exactType()
              val ratingType = AUTHOR_SUMMARY.AUTHOR_RATING.exactType()
              val exactSummaries: Exact<SelectFromStage<AuthorSummary, EntitySelection<AuthorSummary>>> = summariesType
              val exactPeer: Exact<AuthorSummaryTable> = peerType
              val exactRating: Exact<Column<AuthorSummary, Int, SqlNullable, Int?, Int?, SqlNullable, Int?>> =
                ratingType
              names.compile()
            }
          """
        ),
        classpaths = listOf(generated.outputDirectory)
      )
      .isOk()
  }
}
