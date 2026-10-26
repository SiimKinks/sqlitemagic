package com.siimkinks.sqlitemagic.internal

@InternalSqliteMagicApi
enum class SqliteSchema(val qualifier: String) {
  MAIN("main"),
  TEMPORARY("temp")
}

@InternalSqliteMagicApi
interface SqliteIdentifierProvider {
  val rawName: String
  val normalizedName: String
}

@InternalSqliteMagicApi
data class SqliteIdentifier(override val rawName: String) : SqliteIdentifierProvider {
  override val normalizedName = buildString(capacity = rawName.length) {
    for (character in rawName) {
      append(
        when (character) {
          in 'A'..'Z' -> character + ('a' - 'A')
          else -> character
        }
      )
    }
  }
}

@InternalSqliteMagicApi
data class SqliteSchemaKey(
  val schema: SqliteSchema,
  val normalizedName: String
)

@InternalSqliteMagicApi
interface SqliteSchemaProvider : SqliteIdentifierProvider {
  val schema: SqliteSchema
  val identifier: SqliteIdentifier
  val normalizedKey: SqliteSchemaKey
}

@InternalSqliteMagicApi
data class SqliteSchemaIdentity(
  override val schema: SqliteSchema,
  override val identifier: SqliteIdentifier
) : SqliteSchemaProvider, SqliteIdentifierProvider by identifier {
  override val normalizedKey = SqliteSchemaKey(
    schema = schema,
    normalizedName = normalizedName
  )

  companion object {
    fun from(
      schema: SqliteSchema,
      rawName: String
    ) = SqliteSchemaIdentity(
      schema = schema,
      identifier = SqliteIdentifier(rawName)
    )
  }
}
