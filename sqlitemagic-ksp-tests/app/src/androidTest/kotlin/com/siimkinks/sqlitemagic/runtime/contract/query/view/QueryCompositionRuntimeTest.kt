package com.siimkinks.sqlitemagic.runtime.contract.query.view

import android.database.Cursor
import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.AccentedIdentifierViewTable.Companion.ACCENTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.DottedIdentifierViewTable.Companion.DOTTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.HyphenIdentifierViewTable.Companion.HYPHEN_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.KeywordIdentifierViewTable.Companion.KEYWORD_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.NonbreakingSpaceIdentifierViewTable.Companion.NONBREAKING_SPACE_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.PunctuationIdentifierViewTable.Companion.PUNCTUATION_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.QuotedIdentifierViewTable.Companion.QUOTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SpacedIdentifierViewTable.Companion.SPACED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

class QueryCompositionRuntimeTest : RuntimeDatabaseTest() {
  private val expectedAuthors = listOf(
    QueryCompositionAuthor(
      id = 7L,
      name = "Ada"
    ),
    QueryCompositionAuthor(
      id = 9L,
      name = "Grace"
    )
  )
  private val expectedViews = listOf(
    QueryCompositionAuthorView(
      name = "Ada",
      id = 7L
    ),
    QueryCompositionAuthorView(
      name = "Grace",
      id = 9L
    )
  )

  @Test
  fun generatedViewsWithQuotedNamesReadScalarsAndAliases() {
    seedAuthors()
    val dottedAlias = DOTTED_IDENTIFIER_VIEW AS "safe_alias"
    val cases = listOf(
      "dotted" to Select.column(DOTTED_IDENTIFIER_VIEW.NAME)
        .from(DOTTED_IDENTIFIER_VIEW)
        .execute(),
      "spaced" to Select.column(SPACED_IDENTIFIER_VIEW.NAME)
        .from(SPACED_IDENTIFIER_VIEW)
        .execute(),
      "embedded quote" to Select.column(QUOTED_IDENTIFIER_VIEW.NAME)
        .from(QUOTED_IDENTIFIER_VIEW)
        .execute(),
      "keyword" to Select.column(KEYWORD_IDENTIFIER_VIEW.NAME)
        .from(KEYWORD_IDENTIFIER_VIEW)
        .execute(),
      "punctuation" to Select.column(PUNCTUATION_IDENTIFIER_VIEW.NAME)
        .from(PUNCTUATION_IDENTIFIER_VIEW)
        .execute(),
      "nonbreaking space" to Select.column(NONBREAKING_SPACE_IDENTIFIER_VIEW.NAME)
        .from(NONBREAKING_SPACE_IDENTIFIER_VIEW)
        .execute(),
      "hyphen" to Select.column(HYPHEN_IDENTIFIER_VIEW.NAME)
        .from(HYPHEN_IDENTIFIER_VIEW)
        .execute(),
      "accented" to Select.column(ACCENTED_IDENTIFIER_VIEW.NAME)
        .from(ACCENTED_IDENTIFIER_VIEW)
        .execute(),
      "safe alias" to Select.column(dottedAlias.NAME)
        .from(dottedAlias)
        .execute()
    )
    val expected = expectedAuthors.map(QueryCompositionAuthor::name)

    for ((label, values) in cases) {
      assertWithMessage(label).that(values)
        .containsExactlyElementsIn(expected)
        .inOrder()
    }
  }

  @Test
  fun definingQueryColumnOrderMapsViewProperties() {
    seedAuthors()

    assertThat(
      Select.all()
        .from(QUERY_COMPOSITION_AUTHOR_VIEW)
        .orderBy(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_ID.asc())
        .execute()
    ).containsExactlyElementsIn(expectedViews)
      .inOrder()
  }

  @Test
  fun viewAllKeepsScalarTailAfterItsMappedColumns() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val query = Select
      .columns(
        view.all(),
        view.QUERY_COMPOSITION_AUTHOR_NAME AS "tail"
      )
      .from(view)
      .orderBy(view.QUERY_COMPOSITION_AUTHOR_ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedViews)
      .inOrder()
    assertThat(
      columnValues(
        cursor = query.toCursor().execute(),
        columnName = "tail"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun repeatedViewAliasesMapTheRootAndExposeTheJoinedScalar() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val left = view AS "left_author"
    val right = view AS "right_author"
    val query = Select
      .columns(
        left.all(),
        right.all(),
        right.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"
      )
      .from(left)
      .innerJoin(right.on(left.QUERY_COMPOSITION_AUTHOR_NAME IS right.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(left.QUERY_COMPOSITION_AUTHOR_ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedViews)
      .inOrder()
    assertThat(
      columnValues(
        cursor = query.toCursor().execute(),
        columnName = "joined_name"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun implicitSelectStarKeepsJoinedViewColumnsAfterRootColumns() {
    seedAuthors()
    val root = QUERY_COMPOSITION_AUTHOR
    val joinedView = QUERY_COMPOSITION_AUTHOR_VIEW AS "joined_author_view"
    val query = Select
      .all()
      .from(root)
      .innerJoin(joinedView.on(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(root.ID.asc())
      .compile()

    assertThat(query.execute())
      .containsExactlyElementsIn(expectedAuthors)
      .inOrder()
    val joinedViews = query.toCursor()
      .execute()
      .use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              QueryCompositionAuthorView(
                name = cursor.getString(3),
                id = cursor.getLong(2)
              )
            )
          }
        }
      }
    assertThat(joinedViews)
      .containsExactlyElementsIn(expectedViews)
      .inOrder()

    val scalarQuery = Select
      .columns(
        root.all(),
        joinedView.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"
      )
      .from(root)
      .innerJoin(joinedView.on(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME))
      .orderBy(root.ID.asc())
      .compile()
    assertThat(
      columnValues(
        cursor = scalarQuery.toCursor().execute(),
        columnName = "joined_name"
      )
    ).containsExactlyElementsIn(expectedAuthors.map(QueryCompositionAuthor::name))
      .inOrder()
  }

  @Test
  fun missingRequiredViewPositionReportsTheColumn() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val failure = assertThrows(SQLException::class.java) {
      Select
        .columns(view.QUERY_COMPOSITION_AUTHOR_NAME)
        .from(view)
        .execute()
    }

    assertThat(failure).hasMessageThat()
      .contains("Selected columns did not contain required column \"query_composition_author.id\"")
  }

  @Test
  fun unrelatedJoinedAuthorIdCannotFillMissingViewId() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val author = QUERY_COMPOSITION_AUTHOR
    val failure = assertThrows(SQLException::class.java) {
      Select
        .columns(
          view.QUERY_COMPOSITION_AUTHOR_NAME,
          author.ID
        )
        .from(view)
        .innerJoin(author.on(view.QUERY_COMPOSITION_AUTHOR_NAME IS author.NAME))
        .execute()
    }

    assertThat(failure).hasMessageThat()
      .contains("Selected columns did not contain required column \"query_composition_author.id\"")
  }

  private fun seedAuthors() = expectedAuthors.forEach(::insertAuthor)

  private fun insertAuthor(author: QueryCompositionAuthor) {
    when (author.insert().execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> error("Author seed insert was ignored")
    }
  }

  private fun columnValues(
    cursor: Cursor,
    columnName: String
  ) = cursor.use { cursor ->
    val columnIndex = cursor.getColumnIndexOrThrow(columnName)
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.getString(columnIndex))
      }
    }
  }

}
