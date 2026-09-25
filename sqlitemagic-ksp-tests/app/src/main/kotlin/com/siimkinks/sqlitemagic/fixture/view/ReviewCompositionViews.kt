package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReaderRelationshipBookTable.Companion.READER_RELATIONSHIP_BOOK
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("review_star_author")
data class ReviewStarAuthorView(
  @ViewColumn("query_composition_author.id") val id: Long,
  @ViewColumn("query_composition_author.name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .all()
      .from(QUERY_COMPOSITION_AUTHOR)
      .queryDeep()
      .compile()
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
    val query = Select
      .columns(
        bookAlias.all(),
        Select.asColumn("tail") AS "tail",
        authorAlias.all()
      )
      .from(bookAlias)
      .innerJoin(authorAlias.on(bookAlias.AUTHOR IS authorAlias.ID))
      .queryDeep()
      .compile()
  }
}
