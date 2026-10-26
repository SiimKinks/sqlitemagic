package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.squareup.kotlinpoet.STRING

internal fun mockRelationshipElement(
  referencedTableType: ParsedType,
  isHandledRecursively: Boolean
): RelationshipElement {
  val referencedIdType = mockParsedType(typeName = STRING)
  return RelationshipElement(
    referencedTableType = referencedTableType,
    referencedTableName = "referenced_table",
    referencedIdProperty = mockPropertyPath("id"),
    referencedIdColumnName = "id",
    referencedIdType = referencedIdType,
    referencedIdSerializedType = referencedIdType,
    referencedIdTransformer = null,
    referencedIdIsNullable = false,
    isHandledRecursively = isHandledRecursively,
    onDeleteCascade = false,
    canConstructWithOnlyId = true,
    needsGeneratedId = false,
    referencedTableNames = GenerationNames("ReferencedTable"),
    referencedIdRelationship = null
  )
}
