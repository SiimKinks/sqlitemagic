package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.TypeKey

// TODO: implement
data class ColumnElement(
  val isHandledRecursively: Boolean,
  val isId: Boolean,
  val referencedTableTypeKey: TypeKey?
)