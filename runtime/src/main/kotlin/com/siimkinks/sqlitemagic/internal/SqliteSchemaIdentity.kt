package com.siimkinks.sqlitemagic.internal

enum class SqliteSchema(val qualifier: String) {
  MAIN("main"),
  TEMPORARY("temp")
}

interface SqliteIdentifierProvider {
  val rawName: String
  val normalizedName: String
}

@ConsistentCopyVisibility
data class SqliteIdentifier private constructor(
  override val rawName: String,
  override val normalizedName: String
) : SqliteIdentifierProvider {
  companion object {
    fun from(rawName: String) = SqliteIdentifier(
      rawName = rawName,
      normalizedName = buildString(capacity = rawName.length) {
        for (character in rawName) {
          append(
            when (character) {
              in 'A'..'Z' -> character + ('a' - 'A')
              else -> character
            }
          )
        }
      }
    )
  }
}

data class SqliteSchemaKey(
  val schema: SqliteSchema,
  val normalizedName: String
)

interface SqliteSchemaProvider : SqliteIdentifierProvider {
  val schema: SqliteSchema
  val identifier: SqliteIdentifier
  val normalizedKey: SqliteSchemaKey
}

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
      identifier = SqliteIdentifier.from(rawName)
    )
  }
}
