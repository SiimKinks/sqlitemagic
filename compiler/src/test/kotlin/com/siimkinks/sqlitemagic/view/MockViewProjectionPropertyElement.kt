package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.mockModelConstruction
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockViewProjectionPropertyElement(
  access: PropertyAccess = mockPropertyAccess(),
  deserializedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "ProjectedValue")
  ),
  isNullable: Boolean = false,
  selectionKey: String = "projected_value",
  targetKind: ViewProjectionKind = ViewProjectionKind.TABLE,
  targetArtifactStem: String = "ProjectedValue",
  targetConstruction: ModelConstruction = mockModelConstruction(),
  expandedWidth: Int = 1
) = ViewProjectionPropertyElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  selectionKey = selectionKey,
  targetKind = targetKind,
  targetArtifactStem = targetArtifactStem,
  targetConstruction = targetConstruction,
  expandedWidth = expandedWidth
)
