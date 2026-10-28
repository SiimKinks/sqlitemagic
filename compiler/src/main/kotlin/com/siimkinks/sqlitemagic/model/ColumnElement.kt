package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.SqlAffinity.BLOB
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.DEEP
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.generatedFieldName

internal enum class AutoIncrementMode {
  AUTOMATIC,
  ENABLED,
  DISABLED
}

internal data class IdElement(
  val isAutoIncrement: Boolean,
  val canAssignGeneratedId: Boolean,
  val isNotNullKey: Boolean
)

internal data class FieldIndexElement(
  val name: String,
  val isUnique: Boolean
)

internal data class RelationshipElement(
  val referencedTableType: ParsedType,
  val referencedTableName: String,
  val referencedIdProperty: PropertyPath,
  val referencedIdColumnName: String,
  val referencedIdType: ParsedType,
  val referencedIdSerializedType: ParsedType,
  val referencedIdTransformer: TransformerElement?,
  val referencedIdIsNullable: Boolean,
  val isHandledRecursively: Boolean,
  val onDeleteCascade: Boolean,
  val canConstructWithOnlyId: Boolean,
  val needsGeneratedId: Boolean,
  val referencedTableNames: GenerationNames,
  val referencedIdRelationship: RelationshipElement?
) {
  val referencedTableTypeKey get() = referencedTableType.typeKey

  val read
    get() = when {
      !isHandledRecursively -> ID_ONLY
      canConstructWithOnlyId -> DEEP
      else -> ALWAYS
    }
  val referencedIdValueCanBeNull get() = referencedIdType.typeName.isNullable
  val serializedValueCanBeNull: Boolean
    get() = referencedIdTransformer?.serializedTypeCanBeNull == true ||
        referencedIdRelationship?.databaseValueCanBeNull == true
  val databaseValueCanBeNull get() = referencedIdIsNullable || serializedValueCanBeNull
}

internal data class ColumnElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  val columnName: String,
  val isValueNullable: Boolean,
  val isModelPathNullable: Boolean,
  val defaultValue: String?,
  val transformer: TransformerElement?,
  val relationship: RelationshipElement?,
  val id: IdElement?,
  val isUnique: Boolean,
  val index: FieldIndexElement?,
  val belongsToIndex: String?
) : PropertyMetadata {
  init {
    require(transformer == null || relationship == null) {
      "A column cannot be both transformed and a relationship"
    }
  }

  val isId get() = id != null
  val isNotNullKey get() = id?.isNotNullKey == true
  val isSqlNullable get() = isModelPathNullable && !isNotNullKey
  val declaresNotNull get() = !isModelPathNullable && !isNotNullKey
  val schemaDefaultValue get() = defaultValue.takeUnless { isId || isUnique }
  val isRelationship get() = relationship != null
  val isHandledRecursively get() = relationship?.isHandledRecursively == true
  val referencedTableTypeKey get() = relationship?.referencedTableTypeKey
  val fieldName get() = columnName.generatedFieldName()

  val sqlStorageType
    get() = checkNotNull(serializedType.sqlStorageType) {
      "Column serialized type [${serializedType.typeKey}] is not a supported SQL storage type"
    }

  val serializedType
    get() = when {
      relationship != null -> relationship.referencedIdSerializedType
      transformer != null -> transformer.serializedType
      else -> deserializedType
    }

  val serializedValueCanBeNull
    get() = transformer?.serializedTypeCanBeNull == true ||
        relationship?.serializedValueCanBeNull == true

  val hasEntityKeyValue
    get() = !isValueNullable &&
        !serializedValueCanBeNull &&
        sqlStorageType.affinity != BLOB
  val isEligibleEntityKey get() = isUnique && hasEntityKeyValue

  val needsGeneratedRelationshipId get() = relationship?.run { isHandledRecursively && needsGeneratedId } == true

  val bindingValueCanBeNull
    get() = when {
      relationship != null -> isModelPathNullable
      else -> isValueNullable || serializedValueCanBeNull
    }
}
