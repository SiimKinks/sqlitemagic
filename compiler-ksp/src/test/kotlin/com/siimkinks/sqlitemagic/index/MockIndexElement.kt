package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockIndexElement(
  identity: SqliteSchemaIdentity = mockSqliteSchemaIdentity(),
  tableType: ClassName = ClassName(PACKAGE, "TestTable"),
  tableName: String = "test_table",
  kind: IndexKind = IndexKind.FIELD,
  columns: List<IndexColumnElement> = listOf(mockIndexColumnElement()),
  isUnique: Boolean = false,
  sourcePropertyPath: PropertyPath? = mockPropertyPath()
) = IndexElement(
  identity = identity,
  tableType = tableType,
  tableName = tableName,
  kind = kind,
  columns = columns,
  isUnique = isUnique,
  sourcePropertyPath = sourcePropertyPath
)
