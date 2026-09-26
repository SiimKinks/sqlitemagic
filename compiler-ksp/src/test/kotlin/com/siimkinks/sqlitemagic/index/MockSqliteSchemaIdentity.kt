package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity

fun mockSqliteSchemaIdentity(
  schema: SqliteSchema = SqliteSchema.MAIN,
  identifier: SqliteIdentifier = mockSqliteIdentifier()
) = SqliteSchemaIdentity(
  schema = schema,
  identifier = identifier
)
