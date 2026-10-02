package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.mockModelConstruction
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyPath
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockViewEmbeddedPropertyElement(
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
  properties: List<ViewPropertyElement> = listOf(
    mockViewScalarPropertyElement(
      access = mockPropertyAccess(
        path = mockPropertyPath("details", "value")
      ),
      selectionKey = "details_value",
      generatedFieldName = "DETAILS_VALUE"
    )
  )
) = ViewEmbeddedPropertyElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  prefix = prefix,
  cumulativePrefix = cumulativePrefix,
  construction = construction,
  properties = properties
)
