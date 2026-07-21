package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.STRING

fun mockRelationshipElement(
  referencedTableType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "ReferencedTable")
  ),
  referencedTableName: String = "referenced_table",
  referencedIdProperty: PropertyPath = mockPropertyPath("id"),
  referencedIdColumnName: String = "id",
  referencedIdType: ParsedType = mockParsedType(
    typeName = STRING
  ),
  referencedIdSerializedType: ParsedType = referencedIdType,
  referencedIdTransformer: TransformerElement? = null,
  isHandledRecursively: Boolean = true,
  onDeleteCascade: Boolean = false,
  canConstructWithOnlyId: Boolean = true
) = RelationshipElement(
  referencedTableType = referencedTableType,
  referencedTableName = referencedTableName,
  referencedIdProperty = referencedIdProperty,
  referencedIdColumnName = referencedIdColumnName,
  referencedIdType = referencedIdType,
  referencedIdSerializedType = referencedIdSerializedType,
  referencedIdTransformer = referencedIdTransformer,
  isHandledRecursively = isHandledRecursively,
  onDeleteCascade = onDeleteCascade,
  canConstructWithOnlyId = canConstructWithOnlyId
)
