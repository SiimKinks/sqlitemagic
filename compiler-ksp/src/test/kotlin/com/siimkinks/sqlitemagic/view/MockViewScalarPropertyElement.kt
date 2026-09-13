package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.mockPropertyAccess
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName

fun mockViewScalarPropertyElement(
  access: PropertyAccess = mockPropertyAccess(),
  deserializedType: ParsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "Value")
  ),
  isNullable: Boolean = false,
  selectionKey: String = "value",
  generatedFieldName: String = "VALUE",
  transformer: TransformerElement? = null
) = ViewScalarPropertyElement(
  access = access,
  deserializedType = deserializedType,
  isNullable = isNullable,
  selectionKey = selectionKey,
  generatedFieldName = generatedFieldName,
  transformer = transformer
)
