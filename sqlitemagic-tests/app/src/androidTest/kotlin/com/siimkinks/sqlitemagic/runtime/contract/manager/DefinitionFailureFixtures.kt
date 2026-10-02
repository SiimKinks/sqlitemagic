package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import java.lang.reflect.Proxy

@SubmoduleDatabase("definitionFailure")
class DefinitionFailureDatabase

@Table("definition_failure_row")
data class DefinitionFailureRow(
  @Id(autoIncrement = false) val id: Long
)

@View("definition_failure_view")
data class DefinitionFailureView(
  @ViewColumn("value") val value: String
) {
  companion object {
    @Suppress("UNCHECKED_CAST")
    @ViewQuery
    val query = Proxy.newProxyInstance(
      CompiledSelect::class.java.classLoader,
      arrayOf(CompiledSelect::class.java)
    ) { _, _, _ -> null } as CompiledSelect<Any, Any>
  }
}
