package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
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
  identity: SqliteSchemaIdentity = SqliteSchemaIdentity(
    schema = when {
      TableOption.TEMPORARY in options -> SqliteSchema.TEMPORARY
      else -> SqliteSchema.MAIN
    },
    identifier = SqliteIdentifier.from(tableName)
  ),
  construction: ModelConstruction = mockModelConstruction(),
  properties: List<PropertyElement> = emptyList()
) = TableElement(
  parsedType = parsedType,
  identity = identity,
  artifactStem = artifactStem,
  declarationOrder = declarationOrder,
  options = options,
  construction = construction,
  properties = properties
)
