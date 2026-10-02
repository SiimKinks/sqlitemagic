package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.TOP_LEVEL
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LONG

fun mockTransformerElement(
  deserializedType: TransformerTypeElement = mockTransformerTypeElement(),
  serializedType: TransformerTypeElement = mockSerializedTransformerTypeElement(),
  objectToDbValueMethod: TransformerMethodElement = mockTransformerMethodElement("moneyToLong"),
  dbValueToObjectMethod: TransformerMethodElement = mockTransformerMethodElement("longToMoney"),
  serializedTypeCanBeNull: Boolean = false
) = TransformerElement(
  deserializedType = deserializedType,
  serializedType = serializedType,
  objectToDbValueMethod = objectToDbValueMethod,
  dbValueToObjectMethod = dbValueToObjectMethod,
  serializedTypeCanBeNull = serializedTypeCanBeNull
)

private fun mockTransformerTypeElement() = TransformerTypeElementImpl(
  parsedType = mockParsedType(
    typeName = ClassName(PACKAGE, "Money")
  ),
  transformerName = "Money"
)

private fun mockSerializedTransformerTypeElement() = TransformerTypeElementImpl(
  parsedType = mockParsedType(
    typeName = LONG
  ),
  transformerName = "Long"
)

private fun mockTransformerMethodElement(methodName: String) = TransformerMethodElementImpl(
  methodName = methodName,
  packageName = PACKAGE,
  ownerClassName = null,
  callableKind = TOP_LEVEL
)
