package com.siimkinks.sqlitemagic.index

fun mockSqliteSchemaIdentity(
  schema: SqliteSchema = SqliteSchema.MAIN,
  identifier: SqliteIdentifier = mockSqliteIdentifier()
) = SqliteSchemaIdentity(
  schema = schema,
  identifier = identifier
)
