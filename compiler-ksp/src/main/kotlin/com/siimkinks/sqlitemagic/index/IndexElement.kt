package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.utils.typeKey
import com.siimkinks.sqlitemagic.writer.OriginatingFiles
import com.squareup.kotlinpoet.ClassName

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
  val tableIdentity: SqliteSchemaIdentity,
  val kind: IndexKind,
  val columns: List<IndexColumnElement>,
  val isUnique: Boolean,
  val sourcePropertyPath: PropertyPath?
) : SqliteSchemaProvider by identity {
  init {
    require(identity.schema == tableIdentity.schema) {
      "Index and table identities must belong to the same schema"
    }
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

  val name get() = rawName
  val tableName get() = tableIdentity.rawName
  val tableTypeKey get() = tableType.typeKey()
}

data class IndexRoundElement(
  val index: IndexElement,
  val originatingFiles: OriginatingFiles
)
