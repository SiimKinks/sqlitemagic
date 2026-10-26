package com.siimkinks.sqlitemagic.utils

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.view.ViewLeafElement

internal data class MappingMetadata(
  val name: String,
  val columns: List<String>,
  val defaults: List<String>,
  val indexes: List<String>,
  val query: String?
)

internal fun Environment.mappingMetadata(isView: Boolean) = when {
  isView -> viewElements.values.singleOrNull()?.let { view ->
    MappingMetadata(
      name = view.viewName,
      columns = view.leaves.map(ViewLeafElement::selectionKey),
      defaults = emptyList(),
      indexes = indexElements.values.map(IndexElement::name),
      query = view.queryPropertyName
    )
  }
  else -> tableElements.values.singleOrNull()?.let { table ->
    MappingMetadata(
      name = table.tableName,
      columns = table.allColumns.map(ColumnElement::columnName),
      defaults = table.allColumns.map(ColumnElement::defaultValue),
      indexes = indexElements.values.map(IndexElement::name),
      query = null
    )
  }
}
