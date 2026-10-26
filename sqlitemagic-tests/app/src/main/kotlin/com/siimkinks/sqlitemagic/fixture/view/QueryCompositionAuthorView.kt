package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@Table
data class QueryCompositionAuthor(
  @Id(autoIncrement = false) val id: Long,
  val name: String
)

@View("query_composition_author_view")
data class QueryCompositionAuthorView(
  @ViewColumn("query_composition_author.name") val name: String,
  @ViewColumn("query_composition_author.id") val id: Long
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMNS(QUERY_COMPOSITION_AUTHOR.ID, QUERY_COMPOSITION_AUTHOR.NAME)
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}
