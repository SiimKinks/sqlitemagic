package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

internal fun mockViewProjectionPropertyElement(
  access: PropertyAccess = mockPropertyAccess(),
  deserializedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "ProjectedValue")
  ),
  isNullable: Boolean = false,
  selectionKey: String = "projected_value",
  target: GenerationNames = GenerationNames("ProjectedValue")
) = ViewProjectionPropertyElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  selectionKey = selectionKey,
  target = target
)
