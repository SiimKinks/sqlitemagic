package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.DependencyBaseViewTable.Companion.Z_DEPENDENCY_BASE
import com.siimkinks.sqlitemagic.DependencyMiddleViewTable.Companion.M_DEPENDENCY_MIDDLE
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SubmoduleDependencyBaseViewTable.Companion.Z_SUBMODULE_DEPENDENCY_BASE
import com.siimkinks.sqlitemagic.TemporaryDependencyMiddleViewTable.Companion.Z_TEMPORARY_DEPENDENCY_MIDDLE
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View("a_dependency_outer")
data class DependencyOuterView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(M_DEPENDENCY_MIDDLE.NAME AS "name")
      .from(M_DEPENDENCY_MIDDLE)
      .compile()
  }
}

@View("m_dependency_middle")
data class DependencyMiddleView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(Z_DEPENDENCY_BASE.NAME AS "name")
      .from(Z_DEPENDENCY_BASE)
      .compile()
  }
}

@View("z_dependency_base")
data class DependencyBaseView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .from(QUERY_COMPOSITION_AUTHOR)
      .compile()
  }
}

@View("a_main_submodule_dependency_outer")
data class MainSubmoduleDependencyOuterView(
  @ViewColumn("value") val value: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(Z_SUBMODULE_DEPENDENCY_BASE.VALUE AS "value")
      .from(Z_SUBMODULE_DEPENDENCY_BASE)
      .compile()
  }
}

@View(
  value = "a_temporary_dependency_outer",
  options = [TEMPORARY]
)
data class TemporaryDependencyOuterView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(Z_TEMPORARY_DEPENDENCY_MIDDLE.NAME AS "name")
      .from(Z_TEMPORARY_DEPENDENCY_MIDDLE)
      .compile()
  }
}

@View(
  value = "z_temporary_dependency_middle",
  options = [TEMPORARY]
)
data class TemporaryDependencyMiddleView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = Select
      .columns(Z_DEPENDENCY_BASE.NAME AS "name")
      .from(Z_DEPENDENCY_BASE)
      .compile()
  }
}
