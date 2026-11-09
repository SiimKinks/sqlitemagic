package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ReaderNestedSpansViewTable.Companion.READER_NESTED_SPANS_VIEW
import com.siimkinks.sqlitemagic.ReaderOuterJoinedBlockViewTable.Companion.READER_OUTER_JOINED_BLOCK_VIEW
import com.siimkinks.sqlitemagic.ReaderOuterJoinedRelationshipBlockViewTable.Companion.READER_OUTER_JOINED_RELATIONSHIP_BLOCK_VIEW
import com.siimkinks.sqlitemagic.ReviewRelationshipCompositionViewTable.Companion.REVIEW_RELATIONSHIP_COMPOSITION_VIEW
import com.siimkinks.sqlitemagic.ReviewStarAuthorViewTable.Companion.REVIEW_STAR_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.view.ReaderNestedNameView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNestedSpansView
import com.siimkinks.sqlitemagic.fixture.view.ReaderNoIdProjection
import com.siimkinks.sqlitemagic.fixture.view.ReaderOuterJoinedBlockView
import com.siimkinks.sqlitemagic.fixture.view.ReaderOuterJoinedRelationshipBlockView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipTrailingScalarView
import com.siimkinks.sqlitemagic.fixture.view.ReaderRepeatedNestedView
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipCompositionView
import com.siimkinks.sqlitemagic.fixture.view.ReviewStarAuthorView
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.statementOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GeneratedViewCompositionTest {
  private fun <T> SelectQuery<T, *>.readRow(vararg values: Any?) = takeFirst()
    .mapRows(
      statement = statementOf(*values),
      isDisposed = { false }
    )

  @Test
  fun `nullable field lists fill nullable blocks of an outer join definition`() {
    val ada = ReaderRelationshipAuthor(
      id = 7L,
      name = "Ada"
    )
    val flat = SELECT FROM READER_OUTER_JOINED_BLOCK_VIEW
    val related = SELECT FROM READER_OUTER_JOINED_RELATIONSHIP_BLOCK_VIEW

    assertThat(
      listOf(
        flat.readRow(7L, "Ada", null, null),
        flat.readRow(7L, "Ada", "Ada", 3L)
      )
    ).isEqualTo(
      listOf(
        ReaderOuterJoinedBlockView(
          author = ada,
          row = null
        ),
        ReaderOuterJoinedBlockView(
          author = ada,
          row = ReaderNoIdProjection(
            code = "Ada",
            count = 3
          )
        )
      )
    )
    assertThat(
      listOf(
        related.readRow(7L, "Ada", null, null, null, null),
        related.readRow(7L, "Ada", 1L, 7L, 7L, "Ada")
      )
    ).isEqualTo(
      listOf(
        ReaderOuterJoinedRelationshipBlockView(
          owner = ada,
          book = null
        ),
        ReaderOuterJoinedRelationshipBlockView(
          owner = ada,
          book = ReaderRelationshipBook(
            id = 1L,
            author = ada
          )
        )
      )
    )
    assertThat(READER_OUTER_JOINED_BLOCK_VIEW.viewDefinition?.sql).isEqualTo(
      """SELECT "reader_relationship_author"."id" AS "author.id", """ +
          """"reader_relationship_author"."name" AS "author.name", """ +
          """"reader_no_id_projection"."code" AS "row.code", "reader_no_id_projection"."count" AS "row.count" """ +
          """FROM "main"."reader_relationship_author" LEFT JOIN "main"."reader_no_id_projection" """ +
          """ON "reader_relationship_author"."name" = "reader_no_id_projection"."code""""
    )
  }

  @Test
  fun `view field lists map partial views at zero and nonzero positions and wide rows read aliased views`() {
    val view = REVIEW_STAR_AUTHOR_VIEW
    val left = view AS "left_author"
    val right = view AS "right_author"
    val zero = SELECT
      .COLUMNS(view.all())
      .FROM(view)
      .mapTo(view)
    val prefixed = SELECT
      .COLUMNS(view.QUERY_COMPOSITION_AUTHOR_NAME, view.all())
      .FROM(view)
      .mapTo(view)
    val rootSecond = SELECT
      .COLUMNS(right.all(), left.all())
      .FROM(left)
      .INNER_JOIN(right)
      .ON(left.QUERY_COMPOSITION_AUTHOR_ID IS right.QUERY_COMPOSITION_AUTHOR_ID)
      .mapTo { row ->
        ReviewStarAuthorView(
          id = row[left.QUERY_COMPOSITION_AUTHOR_ID],
          name = row[left.QUERY_COMPOSITION_AUTHOR_NAME]
        )
      }
    val computed = SELECT
      .COLUMNS(99.asValue() AS "prefix", view.all())
      .FROM(view)
      .mapTo(view)

    assertThat(
      listOf(
        zero.readRow(7L, "Ada"),
        prefixed.readRow("Ada", 7L, "Ada"),
        rootSecond.readRow(9L, "Grace", 7L, "Ada")
      )
    ).isEqualTo(
      List(size = 3) {
        ReviewStarAuthorView(
          id = 7L,
          name = "Ada"
        )
      }
    )
    assertThat(assertThrows<IllegalArgumentException>(computed::compile))
      .hasMessageThat()
      .isEqualTo("mapTo(review_star_author) reads plain fields, but selected value 1 is computed")
  }

  @Test
  fun `a relationship block around a literal tail reads the complete joined row`() {
    val view = REVIEW_RELATIONSHIP_COMPOSITION_VIEW
    val expected = ReviewRelationshipCompositionView(
      book = ReaderRelationshipBook(
        id = 1L,
        author = ReaderRelationshipAuthor(
          id = 2L,
          name = "Ada"
        )
      ),
      tail = "tail"
    )
    val partial = SELECT
      .COLUMNS(view.all())
      .FROM(view)
      .mapTo(view)

    assertThat((SELECT FROM view).readRow("tail", 1L, 2L, 2L, "Ada")).isEqualTo(expected)
    assertThat(assertThrows<IllegalArgumentException>(partial::compile))
      .hasMessageThat()
      .isEqualTo(
        "mapTo(review_relationship_composition) cannot read a view whose required properties hold tables or views"
      )
  }

  @Test
  fun `nested view blocks read the same model through the view and an alias`() {
    val view = READER_NESTED_SPANS_VIEW
    val values = arrayOf<Any?>("inner-tail", 1L, 2L, 2L, "Ada", "repeat-tail", "Left", "Right")
    val expected = ReaderNestedSpansView(
      relationship = ReaderRelationshipTrailingScalarView(
        book = ReaderRelationshipBook(
          id = 1L,
          author = ReaderRelationshipAuthor(
            id = 2L,
            name = "Ada"
          )
        ),
        tail = "inner-tail"
      ),
      repeated = ReaderRepeatedNestedView(
        left = ReaderNestedNameView(name = "Left"),
        right = ReaderNestedNameView(name = "Right"),
        tail = "repeat-tail"
      )
    )

    assertThat(
      listOf(
        (SELECT FROM view).readRow(*values),
        (SELECT FROM (view AS "nested_spans")).readRow(*values)
      )
    ).isEqualTo(listOf(expected, expected))
  }

  @Test
  fun `a shallow relationship root reads only its own columns beside a joined view`() {
    val root = ENTITY_WITH_RELATIONSHIP
    val joinedView = REVIEW_STAR_AUTHOR_VIEW AS "joined_author"
    val query = SELECT
      .FROM(root)
      .INNER_JOIN(joinedView)
      .ON(root.VALUE IS joinedView.QUERY_COMPOSITION_AUTHOR_NAME)

    assertThat(query.compile().sql).isEqualTo(
      """SELECT "entity_with_relationship"."id", "entity_with_relationship"."value", """ +
          """"entity_with_relationship"."related_entity", "entity_with_relationship"."count" """ +
          """FROM "main"."entity_with_relationship" """ +
          """INNER JOIN "main"."review_star_author" AS "joined_author" """ +
          """ON "entity_with_relationship"."value" = "joined_author"."query_composition_author.name""""
    )
    assertThat(query.readRow(7L, "root", 2L, 3L)).isEqualTo(
      EntityWithRelationship().apply {
        id = 7L
        value = "root"
        relatedEntity = SimpleMutableEntity(id = 2L)
        count = 3
      }
    )
  }
}
