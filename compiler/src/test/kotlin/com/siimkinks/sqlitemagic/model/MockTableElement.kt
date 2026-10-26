package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

internal fun mockTableElement(
  modelName: String = "TestTable",
  properties: List<PropertyElement>
) = TableElement(
  parsedType = mockParsedType(
    typeName = ClassName(PACKAGE, modelName)
  ),
  identity = SqliteSchemaIdentity(
    schema = SqliteSchema.MAIN,
    identifier = SqliteIdentifier("test_table")
  ),
  artifactStem = modelName,
  declarationOrder = 0,
  options = emptySet(),
  construction = mockModelConstruction(),
  properties = properties,
  isPublic = true
)
