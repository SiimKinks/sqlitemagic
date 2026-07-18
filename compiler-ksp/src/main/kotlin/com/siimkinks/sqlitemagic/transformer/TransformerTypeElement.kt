package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.symbol.KSTypeReference
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.RoundTypeElement
import com.siimkinks.sqlitemagic.element.toRoundTypeElement
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

interface TransformerTypeElement : ParsedType {
  val transformerName: String
}

data class TransformerTypeElementImpl(
  val parsedType: ParsedType,
  override val transformerName: String
) : TransformerTypeElement, ParsedType by parsedType

data class TransformerRoundTypeElement(
  val roundTypeElement: RoundTypeElement,
  val transformerTypeElement: TransformerTypeElement
) : RoundTypeElement by roundTypeElement

fun KSTypeReference.toTransformerRoundTypeElement(
  typeParameterResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
): TransformerRoundTypeElement {
  val roundTypeElement = toRoundTypeElement(typeParameterResolver)
  val typeName = roundTypeElement.parsedType.typeName
  val typeElement = TransformerTypeElementImpl(
    parsedType = roundTypeElement.parsedType,
    transformerName = typeName.transformerName()
  )
  return TransformerRoundTypeElement(
    roundTypeElement = roundTypeElement,
    transformerTypeElement = typeElement
  )
}

private fun TypeName.transformerName() = transformerSimpleNames()
  .joinToString(separator = "_")

private fun TypeName.transformerSimpleNames(): List<String> = when {
  this == STAR -> emptyList()
  this is ClassName -> simpleNames
  this is ParameterizedTypeName -> rawType.simpleNames + typeArguments.flatMap(TypeName::transformerSimpleNames)
  this is WildcardTypeName -> (inTypes + outTypes).flatMap(TypeName::transformerSimpleNames)
  else -> listOf(toString().substringAfterLast(delimiter = "."))
}
