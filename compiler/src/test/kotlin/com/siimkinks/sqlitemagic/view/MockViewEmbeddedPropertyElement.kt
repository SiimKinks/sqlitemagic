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

internal fun mockViewEmbeddedPropertyElement(
  access: PropertyAccess = mockPropertyAccess(
    path = mockPropertyPath("details")
  ),
  deserializedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "Details")
  ),
  isNullable: Boolean = false,
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
  construction = construction,
  properties = properties
)
