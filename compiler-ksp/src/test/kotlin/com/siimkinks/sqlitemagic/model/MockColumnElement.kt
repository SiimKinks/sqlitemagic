package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.STRING

fun mockColumnElement(
  access: PropertyAccess = mockPropertyAccess(),
  deserializedType: ParsedType = mockParsedType(typeName = STRING),
  columnName: String = "value",
  transformer: TransformerElement? = null,
  relationship: RelationshipElement? = null,
  isNullable: Boolean = false,
  isSchemaNullable: Boolean = isNullable,
  defaultValue: String = checkNotNull(
    when {
      relationship != null -> relationship.referencedIdSerializedType
      transformer != null -> transformer.serializedType
      else -> deserializedType
    }.sqlStorageType
  ).affinity.defaultValue,
  id: IdElement? = null,
  isUnique: Boolean = false,
  index: IndexElement? = null,
  embeddedPrefixes: List<String> = emptyList()
) = ColumnElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  columnName = columnName,
  isSchemaNullable = isSchemaNullable,
  defaultValue = defaultValue,
  transformer = transformer,
  relationship = relationship,
  id = id,
  isUnique = isUnique,
  index = index,
  embeddedPrefixes = embeddedPrefixes
)
