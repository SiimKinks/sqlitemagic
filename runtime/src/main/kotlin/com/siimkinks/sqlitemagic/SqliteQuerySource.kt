package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity

internal enum class SqliteObjectKind {
  TABLE,
  VIEW
}

internal data class SqliteQuerySource(
  val identity: SqliteSchemaIdentity,
  val kind: SqliteObjectKind
) {
  constructor(
    schema: SqliteSchema,
    name: String,
    kind: SqliteObjectKind = TABLE
  ) : this(
    identity = SqliteSchemaIdentity.from(
      schema = schema,
      rawName = name
    ),
    kind = kind
  )

  val schema get() = identity.schema
  val name get() = identity.rawName
}
