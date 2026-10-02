package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.model.mockPropertyPath

fun mockIndexColumnElement(
  propertyPath: PropertyPath = mockPropertyPath(),
  columnName: String = "value"
) = IndexColumnElement(
  propertyPath = propertyPath,
  columnName = columnName
)
