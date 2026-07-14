package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.tag
import com.squareup.kotlinpoet.tags.TypeAliasTag

/**
 * Returns a canonical lookup key.
 *
 * Example: `String? -> "kotlin.String"`.
 */
fun TypeName.typeKey() = canonicalTypeName(isRoot = true).toString()

/**
 * Returns this KSP type's lookup key.
 *
 * Example: `com.example.Box<T> parameter T -> "com.example.Box.T"`.
 */
fun KSType.typeKey(typeName: TypeName) =
  when (val declaration = declaration) {
    is KSTypeParameter -> "${declaration.parentDeclaration?.qualifiedNameOrSimpleName()}.${declaration.name.asString()}"
    else -> typeName.typeKey()
  }

/**
 * Expands a tagged type alias.
 *
 * Example: `typealias UserId = String -> String`.
 */
fun TypeName.expandedTypeAlias() =
  tag<TypeAliasTag>()
    ?.abbreviatedType
    ?: this

/**
 * Canonicalizes aliases, type arguments, and root nullability.
 *
 * Example: `List<UserId>? -> List<String>` when `typealias UserId = String`.
 */
private fun TypeName.canonicalTypeName(isRoot: Boolean): TypeName {
  val canonicalType = when (val expandedType = expandedTypeAlias()) {
    is ParameterizedTypeName -> expandedType.rawType
      .parameterizedBy(expandedType.typeArguments.map { it.canonicalTypeName(isRoot = false) })
      .copy(nullable = expandedType.isNullable)
    is WildcardTypeName -> when {
      expandedType.inTypes.isNotEmpty() -> WildcardTypeName.consumerOf(
        expandedType.inTypes.single().canonicalTypeName(isRoot = false)
      )
      else -> WildcardTypeName.producerOf(
        expandedType.outTypes.single().canonicalTypeName(isRoot = false)
      )
    }
    else -> expandedType
  }
  return when {
    isRoot -> canonicalType.copy(nullable = false)
    else -> canonicalType
  }
}
