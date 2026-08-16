package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqlAffinity.BLOB
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.siimkinks.sqlitemagic.utils.firstCharToUpperCase

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
  val isUnique: Boolean
)

data class RelationshipElement(
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
  val referencedIdRelationship: RelationshipElement? = null,
  val referencedTableArtifactStem: String = ""
) {
  val referencedTableTypeKey get() = referencedTableType.typeKey
  val serializedValueCanBeNull: Boolean
    get() = referencedIdTransformer?.serializedTypeCanBeNull == true ||
        referencedIdRelationship?.serializedValueCanBeNull == true
  val databaseValueCanBeNull get() = referencedIdIsNullable || serializedValueCanBeNull
}

data class ColumnElement(
  override val access: PropertyAccess,
  override val deserializedType: ParsedType,
  override val isNullable: Boolean,
  val columnName: String,
  val isSchemaNullable: Boolean,
  val isModelPathNullable: Boolean = isSchemaNullable,
  val defaultValue: String,
  val transformer: TransformerElement?,
  val relationship: RelationshipElement?,
  val id: IdElement?,
  val isUnique: Boolean,
  val index: IndexElement?,
  val belongsToIndex: String?,
  val embeddedPrefixes: List<String>
) : PropertyMetadata {
  init {
    require(transformer == null || relationship == null) {
      "A column cannot be both transformed and a relationship"
    }
  }

  val isId get() = id != null
  val isRelationship get() = relationship != null
  val isHandledRecursively get() = relationship?.isHandledRecursively == true
  val referencedTableTypeKey get() = relationship?.referencedTableTypeKey
  val fieldName get() = columnName.camelCaseToSnakeCase().uppercase()

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

  val isEligibleEntityKey
    get() = isUnique &&
        !isSchemaNullable &&
        !serializedValueCanBeNull &&
        sqlStorageType.affinity != BLOB

  val relationshipColumnTypeSegment
    get() = access.path.segments.joinToString(
      separator = "_",
      transform = String::firstCharToUpperCase
    )

  val hasGeneratedColumnClass
    get() = transformer?.let { !it.isDefaultTransformer || isUnique || isId } == true ||
        relationship?.let { referenced ->
          referenced.referencedIdTransformer != null ||
              referenced.referencedIdRelationship != null ||
              isUnique ||
              isId
        } == true

  fun needsGeneratedRelationshipId(environment: Environment): Boolean {
    if (!isHandledRecursively) {
      return false
    }
    val referencedTable = relationship
      ?.referencedTableTypeKey
      ?.let(environment.tableElements::get)
      ?: return false
    return referencedTable.idColumn?.id?.run {
      isAutoIncrement && !canAssignGeneratedId
    } == true
  }

  fun bindingValueCanBeNull() = when {
    relationship != null -> isModelPathNullable
    else -> isSchemaNullable || serializedValueCanBeNull
  }
}
