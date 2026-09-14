package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity

fun mockSqliteSchemaIdentity(
  schema: SqliteSchema = SqliteSchema.MAIN,
  identifier: SqliteIdentifier = mockSqliteIdentifier()
) = SqliteSchemaIdentity(
  schema = schema,
  identifier = identifier
)
