package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.utils.typeKey
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.ClassName

enum class SqliteSchema(
  val qualifier: String
) {
  MAIN("main"),
  TEMPORARY("temp")
}

@ConsistentCopyVisibility
data class SqliteIdentifier private constructor(
  val rawName: String,
  val normalizedName: String
) {
  companion object {
    fun from(rawName: String) = SqliteIdentifier(
      rawName = rawName,
      normalizedName = rawName.asciiLowercase()
    )
  }
}

data class SqliteSchemaIdentity(
  val schema: SqliteSchema,
  val identifier: SqliteIdentifier
)

enum class IndexKind {
  COMPOSITE,
  FIELD
}

data class IndexColumnElement(
  val propertyPath: PropertyPath,
  val columnName: String
)

data class IndexElement(
  val identity: SqliteSchemaIdentity,
  val tableType: ClassName,
  val tableName: String,
  val kind: IndexKind,
  val columns: List<IndexColumnElement>,
  val isUnique: Boolean,
  val sourcePropertyPath: PropertyPath?
) {
  init {
    require(columns.isNotEmpty()) {
      "An index must contain at least one physical column"
    }
    require(kind != IndexKind.FIELD || columns.size == 1) {
      "A field index must contain exactly one physical column"
    }
    require((kind == IndexKind.FIELD) == (sourcePropertyPath != null)) {
      "Only a field index has a source property path"
    }
  }

  val name get() = identity.identifier.rawName
  val normalizedName get() = identity.identifier.normalizedName
  val schema get() = identity.schema
  val tableTypeKey get() = tableType.typeKey()
}

data class IndexRoundElement(
  val index: IndexElement,
  val originatingFiles: OriginatingFiles
)

private fun String.asciiLowercase() = map(Char::asciiLowercase)
  .joinToString(separator = "")

private fun Char.asciiLowercase() =
  when (this) {
    in 'A'..'Z' -> this + ('a' - 'A')
    else -> this
  }
