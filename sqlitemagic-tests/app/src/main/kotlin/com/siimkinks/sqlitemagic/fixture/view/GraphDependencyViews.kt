package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.DependencyBaseViewTable.Companion.DEPENDENCY_BASE_VIEW
import com.siimkinks.sqlitemagic.DependencyMiddleViewTable.Companion.DEPENDENCY_MIDDLE_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SubmoduleDependencyBaseViewTable.Companion.SUBMODULE_DEPENDENCY_BASE_VIEW
import com.siimkinks.sqlitemagic.TemporaryDependencyMiddleViewTable.Companion.TEMPORARY_DEPENDENCY_MIDDLE_VIEW
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
    val query = SELECT
      .COLUMN(DEPENDENCY_MIDDLE_VIEW.NAME AS "name")
      .FROM(DEPENDENCY_MIDDLE_VIEW)
  }
}

@View("m_dependency_middle")
data class DependencyMiddleView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(DEPENDENCY_BASE_VIEW.NAME AS "name")
      .FROM(DEPENDENCY_BASE_VIEW)
  }
}

@View("z_dependency_base")
data class DependencyBaseView(
  @ViewColumn("name") val name: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(QUERY_COMPOSITION_AUTHOR.NAME AS "name")
      .FROM(QUERY_COMPOSITION_AUTHOR)
  }
}

@View("a_main_submodule_dependency_outer")
data class MainSubmoduleDependencyOuterView(
  @ViewColumn("value") val value: String
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(SUBMODULE_DEPENDENCY_BASE_VIEW.VALUE AS "value")
      .FROM(SUBMODULE_DEPENDENCY_BASE_VIEW)
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
    val query = SELECT
      .COLUMN(TEMPORARY_DEPENDENCY_MIDDLE_VIEW.NAME AS "name")
      .FROM(TEMPORARY_DEPENDENCY_MIDDLE_VIEW)
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
    val query = SELECT
      .COLUMN(DEPENDENCY_BASE_VIEW.NAME AS "name")
      .FROM(DEPENDENCY_BASE_VIEW)
  }
}
