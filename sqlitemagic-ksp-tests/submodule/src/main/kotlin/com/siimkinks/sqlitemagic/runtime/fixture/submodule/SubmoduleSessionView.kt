package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View(
  value = "submodule_session_view",
  options = [TEMPORARY]
)
data class SubmoduleSessionView(
  @ViewColumn("submodule_session_value.id") val id: String,
  @ViewColumn("submodule_session_value.value") val value: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(
        SUBMODULE_SESSION_VALUE.ID,
        SUBMODULE_SESSION_VALUE.VALUE
      )
      .from(SUBMODULE_SESSION_VALUE)
      .compile()
  }
}
