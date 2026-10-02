package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SubmodulePersistentValueTable.Companion.SUBMODULE_PERSISTENT_VALUE
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("z_submodule_dependency_base")
data class SubmoduleDependencyBaseView(
  @ViewColumn("value") val value: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(SUBMODULE_PERSISTENT_VALUE.VALUE AS "value")
      .from(SUBMODULE_PERSISTENT_VALUE)
      .compile()
  }
}
