package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockEmbeddedPropertyElement(
  access: PropertyAccess = mockPropertyAccess(
    path = mockPropertyPath("details")
  ),
  deserializedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "Details")
  ),
  isNullable: Boolean = false,
  prefix: String = "details_",
  cumulativePrefix: String = prefix,
  construction: ModelConstruction = mockModelConstruction(
    constructorParameters = listOf(mockPropertyPath("details", "value"))
  ),
  properties: List<PropertyElement> = listOf(
    mockColumnPropertyElement(
      column = mockColumnElement(
        access = mockPropertyAccess(
          path = mockPropertyPath("details", "value")
        )
      )
    )
  )
) = EmbeddedPropertyElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  prefix = prefix,
  cumulativePrefix = cumulativePrefix,
  construction = construction,
  properties = properties
)
