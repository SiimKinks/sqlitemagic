package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockTableElement(
  modelName: String = "TestTable",
  parsedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, modelName)
  ),
  tableName: String = "test_table",
  artifactStem: String = modelName,
  declarationOrder: Int = 0,
  options: Set<TableOption> = emptySet(),
  construction: ModelConstruction = mockModelConstruction(),
  properties: List<PropertyElement> = emptyList()
) = TableElement(
  parsedType = parsedType,
  tableName = tableName,
  artifactStem = artifactStem,
  declarationOrder = declarationOrder,
  options = options,
  construction = construction,
  properties = properties
)
