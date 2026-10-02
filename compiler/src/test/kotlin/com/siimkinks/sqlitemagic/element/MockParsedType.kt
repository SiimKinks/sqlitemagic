package com.siimkinks.sqlitemagic.element

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.expandedTypeAlias
import com.siimkinks.sqlitemagic.utils.typeKey
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName

fun mockParsedType(
  typeName: TypeName = ClassName(PACKAGE, "Type"),
  typeKey: TypeKey = typeName.typeKey(),
  qualifiedName: String = typeName.mockQualifiedName(),
  sqlStorageType: SqlStorageType? = SqlStorageType.from(typeName)
): ParsedType = ParsedTypeImpl(
  typeKey = typeKey,
  typeName = typeName,
  qualifiedName = qualifiedName,
  sqlStorageType = sqlStorageType
)

private fun TypeName.mockQualifiedName() =
  when (val expandedTypeName = expandedTypeAlias()) {
    is ClassName -> expandedTypeName.canonicalName
    is ParameterizedTypeName -> expandedTypeName.rawType.canonicalName
    else -> expandedTypeName.toString()
  }
