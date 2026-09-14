package com.siimkinks.sqlitemagic.schema

enum class SqliteSchema(
  val qualifier: String
) {
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
      normalizedName = rawName.asciiLowercase()
    )
  }
}

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
}

data class SqliteSchemaKey(
  val schema: SqliteSchema,
  val normalizedName: String
)

private fun String.asciiLowercase() = map(Char::asciiLowercase)
  .joinToString(separator = "")

private fun Char.asciiLowercase() =
  when (this) {
    in 'A'..'Z' -> this + ('a' - 'A')
    else -> this
  }
