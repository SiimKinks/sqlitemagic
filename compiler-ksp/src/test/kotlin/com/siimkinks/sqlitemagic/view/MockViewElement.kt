package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.index.mockSqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.mockModelConstruction
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockViewElement(
  parsedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "TestView")
  ),
  identity: SqliteSchemaIdentity = mockSqliteSchemaIdentity(),
  artifactStem: String = "TestView",
  declarationOrder: Int = 0,
  moduleName: String? = null,
  construction: ModelConstruction = mockModelConstruction(
    constructorParameters = listOf(mockPropertyPath("value"))
  ),
  query: ViewQueryElement = mockViewQueryElement(),
  properties: List<ViewPropertyElement> = listOf(
    mockViewScalarPropertyElement()
  ),
  isPublic: Boolean = true
) = ViewElement(
  parsedType = parsedType,
  identity = identity,
  artifactStem = artifactStem,
  declarationOrder = declarationOrder,
  moduleName = moduleName,
  construction = construction,
  query = query,
  properties = properties,
  isPublic = isPublic
)
