package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.squareup.kotlinpoet.STRING

internal fun mockColumnElement(
  deserializedType: ParsedType = mockParsedType(typeName = STRING),
  columnName: String = "value",
  relationship: RelationshipElement? = null,
  isNullable: Boolean = false,
  id: IdElement? = null,
  isUnique: Boolean = false
) = ColumnElement(
  access = mockPropertyAccess(),
  deserializedType = deserializedType,
  isNullable = isNullable,
  columnName = columnName,
  isValueNullable = isNullable,
  isModelPathNullable = isNullable,
  defaultValue = checkNotNull((relationship?.referencedIdSerializedType ?: deserializedType).sqlStorageType)
    .defaultValue,
  transformer = null,
  relationship = relationship,
  id = id,
  isUnique = isUnique,
  index = null,
  belongsToIndex = null
)
