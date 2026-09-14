package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockIndexElement(
  identity: SqliteSchemaIdentity = mockSqliteSchemaIdentity(),
  tableType: ClassName = ClassName(PACKAGE, "TestTable"),
  tableName: String = "test_table",
  tableIdentity: SqliteSchemaIdentity = mockSqliteSchemaIdentity(
    schema = identity.schema,
    identifier = mockSqliteIdentifier(rawName = tableName)
  ),
  kind: IndexKind = IndexKind.FIELD,
  columns: List<IndexColumnElement> = listOf(mockIndexColumnElement()),
  isUnique: Boolean = false,
  sourcePropertyPath: PropertyPath? = mockPropertyPath()
) = IndexElement(
  identity = identity,
  tableIdentity = tableIdentity,
  tableType = tableType,
  kind = kind,
  columns = columns,
  isUnique = isUnique,
  sourcePropertyPath = sourcePropertyPath
)
