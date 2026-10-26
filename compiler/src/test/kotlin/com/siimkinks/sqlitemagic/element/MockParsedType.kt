package com.siimkinks.sqlitemagic.element

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.expandedTypeAlias
import com.siimkinks.sqlitemagic.utils.typeKey
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName

internal fun mockParsedType(
  typeName: TypeName = ClassName(PACKAGE, "Type"),
  sqlStorageType: SqlStorageType? = SqlStorageType.from(typeName)
): ParsedType = ParsedTypeImpl(
  typeKey = typeName.typeKey(),
  typeName = typeName,
  qualifiedName = typeName.mockQualifiedName(),
  sqlStorageType = sqlStorageType
)

private fun TypeName.mockQualifiedName() =
  when (val expandedTypeName = expandedTypeAlias()) {
    is ClassName -> expandedTypeName.canonicalName
    is ParameterizedTypeName -> expandedTypeName.rawType.canonicalName
    else -> expandedTypeName.toString()
  }
