package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability.NOT_NULL
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.utils.qualifiedNameOrSimpleName
import com.siimkinks.sqlitemagic.utils.resolveClassDeclaration
import com.siimkinks.sqlitemagic.utils.typeKey
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Canonical transformer lookup identity that preserves type arguments but ignores root nullability.
 *
 * For example: `List<Email>?` becomes `kotlin.collections.List<com.example.Email>`
 */
typealias TypeKey = String

interface TransformerTypeElement {
  val typeKey: TypeKey
  val typeName: TypeName
  val qualifiedName: String
  val transformerName: String
  val sqlStorageType: SqlStorageType?
}

data class TransformerTypeElementImpl(
  override val typeKey: TypeKey,
  override val typeName: TypeName,
  override val qualifiedName: String,
  override val transformerName: String,
  override val sqlStorageType: SqlStorageType?
) : TransformerTypeElement

data class TransformerRoundTypeElement(
  val type: KSType,
  val declaration: KSClassDeclaration?,
  val transformerTypeElement: TransformerTypeElement
) : TransformerTypeElement by transformerTypeElement {
  val canBeNull get() = type.nullability != NOT_NULL
}

fun KSTypeReference.toTransformerRoundTypeElement(
  typeParameterResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
): TransformerRoundTypeElement {
  val type = resolve()
  val typeName = type.toTypeName(typeParameterResolver)
  val declaration = type.declaration.resolveClassDeclaration()
  val typeElement = TransformerTypeElementImpl(
    typeKey = type.typeKey(typeName),
    typeName = typeName,
    qualifiedName = (declaration ?: type.declaration).qualifiedNameOrSimpleName(),
    transformerName = typeName.transformerName(),
    sqlStorageType = SqlStorageType.from(typeName)
  )
  return TransformerRoundTypeElement(
    type = type,
    declaration = declaration,
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
