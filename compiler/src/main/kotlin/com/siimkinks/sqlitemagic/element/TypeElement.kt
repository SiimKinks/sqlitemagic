package com.siimkinks.sqlitemagic.element

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability.NOT_NULL
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.utils.qualifiedNameOrSimpleName
import com.siimkinks.sqlitemagic.utils.resolveClassDeclaration
import com.siimkinks.sqlitemagic.utils.typeKey
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Canonical type identity that preserves type arguments but ignores root nullability.
 *
 * For example: `List<Email>?` becomes `kotlin.collections.List<com.example.Email>`
 */
typealias TypeKey = String

interface ParsedType {
  val typeKey: TypeKey
  val typeName: TypeName
  val qualifiedName: String
  val sqlStorageType: SqlStorageType?
}

data class ParsedTypeImpl(
  override val typeKey: TypeKey,
  override val typeName: TypeName,
  override val qualifiedName: String,
  override val sqlStorageType: SqlStorageType?
) : ParsedType

interface RoundTypeElement : ParsedType {
  val type: KSType
  val declaration: KSClassDeclaration?
  val parsedType: ParsedType

  val canBeNull get() = type.nullability != NOT_NULL
}

/** A parsed type together with KSP state that is valid only for the current round. */
data class RoundTypeElementImpl(
  override val type: KSType,
  override val declaration: KSClassDeclaration?,
  override val parsedType: ParsedType
) : RoundTypeElement, ParsedType by parsedType

fun KSTypeReference.toRoundTypeElement(
  typeParameterResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
): RoundTypeElement = resolve().toRoundTypeElement(typeParameterResolver)

fun KSType.toRoundTypeElement(
  typeParameterResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
): RoundTypeElement {
  val declaration = declaration.resolveClassDeclaration()
  return RoundTypeElementImpl(
    type = this,
    declaration = declaration,
    parsedType = toParsedType(
      typeParameterResolver = typeParameterResolver,
      declaration = declaration
    )
  )
}

fun KSType.toParsedType(
  typeParameterResolver: TypeParameterResolver = TypeParameterResolver.EMPTY,
  typeName: TypeName = toTypeName(typeParameterResolver),
  declaration: KSClassDeclaration? = this.declaration.resolveClassDeclaration()
): ParsedType = ParsedTypeImpl(
  typeKey = typeKey(typeName),
  typeName = typeName,
  qualifiedName = (declaration ?: this.declaration).qualifiedNameOrSimpleName(),
  sqlStorageType = SqlStorageType.from(typeName)
)