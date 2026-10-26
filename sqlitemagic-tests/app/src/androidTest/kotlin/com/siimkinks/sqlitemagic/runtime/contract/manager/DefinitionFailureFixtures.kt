package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.siimkinks.sqlitemagic.DefinitionFailureRowTable.Companion.DEFINITION_FAILURE_ROW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@SubmoduleDatabase("definitionFailure")
class DefinitionFailureDatabase

@Table("definition_failure_row")
data class DefinitionFailureRow(
  @Id(autoIncrement = false) val id: Long
)

@View("definition_failure_view")
data class DefinitionFailureView(
  @ViewColumn("value") val value: Long
) {
  companion object {
    @ViewQuery
    val query = SELECT
      .COLUMN(DEFINITION_FAILURE_ROW.ID AS "value")
      .FROM(DEFINITION_FAILURE_ROW)
      .WHERE(DEFINITION_FAILURE_ROW.ID IS DEFINITION_FAILURE_ROW.ID.asParameter("id"))
  }
}
