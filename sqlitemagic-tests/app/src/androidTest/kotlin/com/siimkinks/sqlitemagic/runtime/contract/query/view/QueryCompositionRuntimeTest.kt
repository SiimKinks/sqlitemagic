package com.siimkinks.sqlitemagic.runtime.contract.query.view

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AccentedIdentifierViewTable.Companion.ACCENTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.DottedIdentifierViewTable.Companion.DOTTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.HyphenIdentifierViewTable.Companion.HYPHEN_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.KeywordIdentifierViewTable.Companion.KEYWORD_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.NonbreakingSpaceIdentifierViewTable.Companion.NONBREAKING_SPACE_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.PunctuationIdentifierViewTable.Companion.PUNCTUATION_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.QuotedIdentifierViewTable.Companion.QUOTED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SpacedIdentifierViewTable.Companion.SPACED_IDENTIFIER_VIEW
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test

internal class QueryCompositionRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

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
      "dotted" to SELECT
        .COLUMN(DOTTED_IDENTIFIER_VIEW.NAME)
        .FROM(DOTTED_IDENTIFIER_VIEW)
        .execute(),
      "spaced" to SELECT
        .COLUMN(SPACED_IDENTIFIER_VIEW.NAME)
        .FROM(SPACED_IDENTIFIER_VIEW)
        .execute(),
      "embedded quote" to SELECT
        .COLUMN(QUOTED_IDENTIFIER_VIEW.NAME)
        .FROM(QUOTED_IDENTIFIER_VIEW)
        .execute(),
      "keyword" to SELECT
        .COLUMN(KEYWORD_IDENTIFIER_VIEW.NAME)
        .FROM(KEYWORD_IDENTIFIER_VIEW)
        .execute(),
      "punctuation" to SELECT
        .COLUMN(PUNCTUATION_IDENTIFIER_VIEW.NAME)
        .FROM(PUNCTUATION_IDENTIFIER_VIEW)
        .execute(),
      "nonbreaking space" to SELECT
        .COLUMN(NONBREAKING_SPACE_IDENTIFIER_VIEW.NAME)
        .FROM(NONBREAKING_SPACE_IDENTIFIER_VIEW)
        .execute(),
      "hyphen" to SELECT
        .COLUMN(HYPHEN_IDENTIFIER_VIEW.NAME)
        .FROM(HYPHEN_IDENTIFIER_VIEW)
        .execute(),
      "accented" to SELECT
        .COLUMN(ACCENTED_IDENTIFIER_VIEW.NAME)
        .FROM(ACCENTED_IDENTIFIER_VIEW)
        .execute(),
      "safe alias" to SELECT
        .COLUMN(dottedAlias.NAME)
        .FROM(dottedAlias)
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
      SELECT
        .FROM(QUERY_COMPOSITION_AUTHOR_VIEW)
        .ORDER_BY(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_ID.ASC())
        .execute()
    ).containsExactlyElementsIn(expectedViews)
      .inOrder()
  }

  @Test
  fun entityQueryJoinedWithAViewReadsItsRootEntities() {
    seedAuthors()
    val root = QUERY_COMPOSITION_AUTHOR
    val joinedView = QUERY_COMPOSITION_AUTHOR_VIEW AS "joined_author_view"

    assertThat(
      SELECT
        .FROM(root)
        .INNER_JOIN(joinedView)
        .ON(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME)
        .ORDER_BY(root.ID.ASC())
        .execute()
    ).containsExactlyElementsIn(expectedAuthors)
      .inOrder()
  }

  @Test
  fun viewFieldListsReadBesideAScalarTail() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val tail = view.QUERY_COMPOSITION_AUTHOR_NAME AS "tail"

    assertThat(
      SELECT
        .COLUMNS(view.all(), tail)
        .FROM(view)
        .ORDER_BY(view.QUERY_COMPOSITION_AUTHOR_ID.ASC())
        .mapTo { row ->
          QueryCompositionAuthorView(
            name = checkNotNull(row[view.QUERY_COMPOSITION_AUTHOR_NAME]),
            id = row[view.QUERY_COMPOSITION_AUTHOR_ID]
          ) to row[tail]
        }
        .execute()
    ).containsExactlyElementsIn(expectedViews.map { it to it.name })
      .inOrder()
  }

  @Test
  fun repeatedViewAliasesReadTheRootAndTheJoinedScalar() {
    seedAuthors()
    val view = QUERY_COMPOSITION_AUTHOR_VIEW
    val left = view AS "left_author"
    val right = view AS "right_author"
    val joinedName = right.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"

    assertThat(
      SELECT
        .COLUMNS(left.all(), right.all(), joinedName)
        .FROM(left)
        .INNER_JOIN(right)
        .ON(left.QUERY_COMPOSITION_AUTHOR_NAME IS right.QUERY_COMPOSITION_AUTHOR_NAME)
        .ORDER_BY(left.QUERY_COMPOSITION_AUTHOR_ID.ASC())
        .mapTo { row ->
          QueryCompositionAuthorView(
            name = checkNotNull(row[left.QUERY_COMPOSITION_AUTHOR_NAME]),
            id = row[left.QUERY_COMPOSITION_AUTHOR_ID]
          ) to row[joinedName]
        }
        .execute()
    ).containsExactlyElementsIn(expectedViews.map { it to it.name })
      .inOrder()
  }

  @Test
  fun rootFieldListsReadBesideAJoinedViewScalar() {
    seedAuthors()
    val root = QUERY_COMPOSITION_AUTHOR
    val joinedView = QUERY_COMPOSITION_AUTHOR_VIEW AS "joined_author_view"
    val joinedName = joinedView.QUERY_COMPOSITION_AUTHOR_NAME AS "joined_name"

    assertThat(
      SELECT
        .COLUMNS(root.all(), joinedName)
        .FROM(root)
        .INNER_JOIN(joinedView)
        .ON(root.NAME IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME)
        .ORDER_BY(root.ID.ASC())
        .mapTo { row ->
          QueryCompositionAuthor(
            id = row[root.ID],
            name = checkNotNull(row[root.NAME])
          ) to row[joinedName]
        }
        .execute()
    ).containsExactlyElementsIn(expectedAuthors.map { it to it.name })
      .inOrder()
  }

  private fun seedAuthors() = expectedAuthors.forEach {
    insertedResult(
      result = it
        .insert()
        .execute(),
      modelName = "QueryCompositionAuthor"
    )
  }
}
