package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseMetadata
import com.siimkinks.sqlitemagic.dbconfig.SubmoduleDatabaseMetadata
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.ClassName

data class GeneratedDatabaseElement(
  val className: ClassName,
  val submoduleName: String?,
  val databaseMetadata: DatabaseMetadata,
  val isDebug: Boolean,
  val tables: List<TableElement>,
  val transformers: List<TransformerElement>,
  val submodules: List<SubmoduleDatabaseMetadata>
) {
  val isSubmodule get() = submoduleName != null
  val shouldGenerate get() = tables.isNotEmpty() || submodules.isNotEmpty()

  fun withDatabaseVersion(version: Int?) = copy(
    databaseMetadata = databaseMetadata.copy(
      dbVersion = version ?: databaseMetadata.dbVersion
    )
  )

  companion object {
    fun from(environment: Environment) = with(environment) {
      GeneratedDatabaseElement(
        className = getGenClassesManagerClassName(),
        submoduleName = submoduleName,
        databaseMetadata = dbMetadata,
        isDebug = options.isDebugVariant,
        tables = tableElements.values.sortedBy(TableElement::declarationOrder),
        transformers = transformerElements.values.toList(),
        submodules = submoduleDatabases.orEmpty()
      )
    }
  }
}
