package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ReviewRelationshipCompositionViewTable.Companion.REVIEW_RELATIONSHIP_COMPOSITION
import com.siimkinks.sqlitemagic.ReviewStarAuthorViewTable.Companion.REVIEW_STAR_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Select.SelectN
import com.siimkinks.sqlitemagic.cursorOf
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipCompositionView
import com.siimkinks.sqlitemagic.fixture.view.ReviewStarAuthorView
import org.junit.jupiter.api.Test

internal class GeneratedViewCompositionTest {
  @Test
  fun `star definition maps explicit view selections at zero nonzero and repeated alias offsets`() {
    data class Case(
      val label: String,
      val query: CompiledSelect<ReviewStarAuthorView, SelectN>,
      val values: List<Any?>,
      val expected: ReviewStarAuthorView
    )

    val view = REVIEW_STAR_AUTHOR
    val left = view AS "left_author"
    val right = view AS "right_author"
    val cases = listOf(
      Case(
        label = "zero offset",
        query = Select
          .columns(view.all())
          .from(view)
          .compile(),
        values = listOf(7L, "Ada"),
        expected = ReviewStarAuthorView(
          id = 7L,
          name = "Ada"
        )
      ),
      Case(
        label = "scalar prefix",
        query = Select
          .columns(
            Select.asColumn(99) AS "prefix",
            view.all()
          )
          .from(view)
          .compile(),
        values = listOf(99, 7L, "Ada"),
        expected = ReviewStarAuthorView(
          id = 7L,
          name = "Ada"
        )
      ),
      Case(
        label = "repeated aliases with root second",
        query = Select
          .columns(
            right.all(),
            left.all()
          )
          .from(left)
          .innerJoin(right.on(left.QUERY_COMPOSITION_AUTHOR_ID IS right.QUERY_COMPOSITION_AUTHOR_ID))
          .compile(),
        values = listOf(9L, "Grace", 7L, "Ada"),
        expected = ReviewStarAuthorView(
          id = 7L,
          name = "Ada"
        )
      )
    )

    cases.forEach { case ->
      val actual = case.query
        .toCursor()
        .getFromCurrentPosition(cursorOf(*case.values.toTypedArray()))
      assertWithMessage(case.label)
        .that(actual)
        .isEqualTo(case.expected)
    }
  }

  @Test
  fun `deep relationship projection maps the complete joined row around a scalar`() {
    val view = REVIEW_RELATIONSHIP_COMPOSITION
    val actual = Select
      .all()
      .from(view)
      .queryDeep()
      .compile()
      .toCursor()
      .getFromCurrentPosition(cursorOf(1L, 2L, "tail", 2L, "Ada"))

    assertWithMessage("deep relationship view").that(actual).isEqualTo(
      ReviewRelationshipCompositionView(
        book = ReaderRelationshipBook(
          id = 1L,
          author = ReaderRelationshipAuthor(
            id = 2L,
            name = "Ada"
          )
        ),
        tail = "tail"
      )
    )
  }

  @Test
  fun `shallow relationship root stays within its span beside a deep scalar view definition`() {
    val root = ENTITY_WITH_RELATIONSHIP
    val joinedView = REVIEW_STAR_AUTHOR AS "joined_author"
    val actual = Select
      .all()
      .from(root)
      .innerJoin(joinedView.on(root.VALUE IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME))
      .compile()
      .toCursor()
      .getFromCurrentPosition(cursorOf(7L, "root", 2L, 3, 11L, "Ada"))

    val expected = EntityWithRelationship().apply {
      id = 7L
      value = "root"
      relatedEntity = SimpleMutableEntity(id = 2L)
      count = 3
    }
    assertWithMessage("shallow root next to joined view")
      .that(actual)
      .isEqualTo(expected)
  }
}
