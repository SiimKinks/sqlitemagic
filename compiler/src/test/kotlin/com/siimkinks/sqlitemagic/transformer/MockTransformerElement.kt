package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.TOP_LEVEL
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LONG

internal fun mockTransformerElement() = TransformerElement(
  deserializedType = TransformerTypeElementImpl(
    parsedType = mockParsedType(
      typeName = ClassName(PACKAGE, "Money")
    ),
    transformerName = "Money"
  ),
  serializedType = TransformerTypeElementImpl(
    parsedType = mockParsedType(
      typeName = LONG
    ),
    transformerName = "Long"
  ),
  objectToDbValueMethod = mockTransformerMethodElement("moneyToLong"),
  dbValueToObjectMethod = mockTransformerMethodElement("longToMoney"),
  encoderInputNullable = false,
  serializedTypeCanBeNull = false,
  decoderResultNullable = false,
  decoderInputNullable = false
)

private fun mockTransformerMethodElement(methodName: String) = TransformerMethodElementImpl(
  methodName = methodName,
  packageName = PACKAGE,
  ownerClassName = null,
  callableKind = TOP_LEVEL
)
