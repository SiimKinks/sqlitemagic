package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockTableElement(
  modelName: String = "TestTable",
  parsedType: ParsedType = ClassName(PACKAGE, modelName).let { typeName ->
    mockParsedType(
      typeName = typeName,
      typeKey = typeName.canonicalName
    )
  },
  allColumns: List<ColumnElement> = emptyList()
) = TableElement(
  parsedType = parsedType,
  modelName = modelName,
  allColumns = allColumns,
)