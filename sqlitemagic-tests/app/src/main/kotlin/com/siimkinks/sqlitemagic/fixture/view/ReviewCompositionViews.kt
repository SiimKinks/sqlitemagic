package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReaderRelationshipBookTable.Companion.READER_RELATIONSHIP_BOOK
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.queryDeep

@View("review_star_author")
data class ReviewStarAuthorView(
  @ViewColumn("query_composition_author.id") val id: Long,
  @ViewColumn("query_composition_author.name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .FROM(QUERY_COMPOSITION_AUTHOR)
      .queryDeep()
  }
}

@View("review_relationship_book")
data class ReviewRelationshipBookView(
  @ViewColumn("book") val book: ReaderRelationshipBook
) {
  companion object {
    @ViewQuery
    val query = READER_RELATIONSHIP_QUERY
  }
}

@View("review_relationship_composition")
data class ReviewRelationshipCompositionView(
  @ViewColumn("book") val book: ReaderRelationshipBook,
  @ViewColumn("tail") val tail: String
) {
  companion object {
    private val bookAlias = READER_RELATIONSHIP_BOOK AS "book"
    private val authorAlias = READER_RELATIONSHIP_AUTHOR AS "author"

    @ViewQuery
    val query = SELECT
      .COLUMNS(bookAlias.all(), "tail".asValue() AS "tail", authorAlias.all())
      .FROM(bookAlias)
      .INNER_JOIN(authorAlias)
      .ON(bookAlias.AUTHOR IS authorAlias.ID)
  }
}
