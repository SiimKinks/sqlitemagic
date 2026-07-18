package com.siimkinks.sqlitemagic.element

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

// TODO: implement properly
fun mockParsedType(
  typeKey: TypeKey = "$PACKAGE.Type",
  typeName: TypeName = ClassName(PACKAGE, "Type"),
  qualifiedName: String = typeKey,
  sqlStorageType: SqlStorageType? = null
): ParsedType = ParsedTypeImpl(
  typeKey = typeKey,
  typeName = typeName,
  qualifiedName = qualifiedName,
  sqlStorageType = sqlStorageType,
)