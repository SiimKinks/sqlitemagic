package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType

// TODO: implement
data class TableElement(
  val parsedType: ParsedType,
  val modelName: String,
  val allColumns: List<ColumnElement>
) : ParsedType by parsedType {
  val columnsExceptId get() = allColumns.filterNot(ColumnElement::isId)
}