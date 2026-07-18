package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.TypeKey

fun mockColumnElement(
  isHandledRecursively: Boolean = false,
  isId: Boolean = false,
  referencedTableTypeKey: TypeKey? = null
) = ColumnElement(
  isHandledRecursively = isHandledRecursively,
  isId = isId,
  referencedTableTypeKey = referencedTableTypeKey,
)