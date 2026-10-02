package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View(
  value = "main_session_view",
  options = [TEMPORARY]
)
data class MainSessionView(
  @ViewColumn("main_session_value.id") val id: String,
  @ViewColumn("main_session_value.value") val value: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        MAIN_SESSION_VALUE.ID,
        MAIN_SESSION_VALUE.VALUE
      )
      .from(MAIN_SESSION_VALUE)
      .compile()
  }
}

@View(
  value = "persistent_source_session_view",
  options = [TEMPORARY]
)
data class PersistentSourceSessionView(
  @ViewColumn("query_composition_author.id") val id: Long,
  @ViewColumn("query_composition_author.name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        QUERY_COMPOSITION_AUTHOR.ID,
        QUERY_COMPOSITION_AUTHOR.NAME
      )
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}
