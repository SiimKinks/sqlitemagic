package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerElement

enum class AutoIncrementMode {
  AUTOMATIC,
  ENABLED,
  DISABLED
}

data class IdElement(
  val autoIncrementMode: AutoIncrementMode,
  val isAutoIncrement: Boolean,
  val canAssignGeneratedId: Boolean
)

data class IndexElement(
  val name: String,
  val isUnique: Boolean,
  val belongsToIndex: String?
)

data class RelationshipElement(
  val referencedTableType: ParsedType,
  val referencedTableName: String,
  val referencedIdProperty: PropertyPath,
  val referencedIdColumnName: String,
  val referencedIdType: ParsedType,
  val referencedIdSerializedType: ParsedType,
  val referencedIdTransformer: TransformerElement?,
  val isHandledRecursively: Boolean,
  val onDeleteCascade: Boolean,
  val canConstructWithOnlyId: Boolean
) {
  val referencedTableTypeKey get() = referencedTableType.typeKey
}

data class ColumnElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  val columnName: String,
  val isSchemaNullable: Boolean,
  val defaultValue: String,
  val transformer: TransformerElement?,
  val relationship: RelationshipElement?,
  val id: IdElement?,
  val isUnique: Boolean,
  val index: IndexElement?,
  val embeddedPrefixes: List<String>
) : PropertyMetadata {
  init {
    require(transformer == null || relationship == null) {
      "A column cannot be both transformed and a relationship"
    }
  }

  val serializedType
    get() = when {
      relationship != null -> relationship.referencedIdSerializedType
      transformer != null -> transformer.serializedType
      else -> deserializedType
    }
  val sqlStorageType
    get() = checkNotNull(serializedType.sqlStorageType) {
      "Column serialized type [${serializedType.typeKey}] is not a supported SQL storage type"
    }
  val isId get() = id != null
  val isEligibleEntityKey get() = isUnique && !isSchemaNullable
  val isRelationship get() = relationship != null
  val isHandledRecursively get() = relationship?.isHandledRecursively == true
  val referencedTableTypeKey get() = relationship?.referencedTableTypeKey
}
