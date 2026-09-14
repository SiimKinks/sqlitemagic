package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier.PRIVATE
import com.google.devtools.ksp.symbol.Modifier.PROTECTED
import com.siimkinks.sqlitemagic.element.toRoundTypeElement
import com.siimkinks.sqlitemagic.utils.getLocalAndInheritedProperties
import com.siimkinks.sqlitemagic.utils.isAccessibleFromGeneratedCode
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

internal data class ModelPropertyCandidate(
  val sourceDeclaration: KSPropertyDeclaration,
  val isConstructorProperty: Boolean,
  val hasDefault: Boolean,
  val isInherited: Boolean,
  val isMutable: Boolean,
  val isReadable: Boolean,
  val isWritable: Boolean,
  val hasBackingField: Boolean,
  val isDelegated: Boolean
)

internal fun scanModelPropertyCandidates(
  declaration: KSClassDeclaration,
  modelConstructor: KSFunctionDeclaration?
): List<ModelPropertyCandidate> {
  val constructorParameters = modelConstructor
    ?.parameters
    .orEmpty()
    .associateBy { it.name?.asString() }
  return declaration
    .getLocalAndInheritedProperties()
    .map { property ->
      val constructorParameter = constructorParameters[property.simpleName.asString()]
      ModelPropertyCandidate(
        sourceDeclaration = property,
        isConstructorProperty = constructorParameter
          ?.let { parameter -> parameter.isVal || parameter.isVar }
          ?: false,
        hasDefault = constructorParameter
          ?.let { parameter -> (parameter.isVal || parameter.isVar) && parameter.hasDefault }
          ?: false,
        isInherited = property.parentDeclaration?.qualifiedName != declaration.qualifiedName,
        isMutable = property.isMutable,
        isReadable = property.isAccessibleFromGeneratedCode(),
        isWritable = property.isMutable && when (val setter = property.setter) {
          null -> property.isAccessibleFromGeneratedCode()
          else -> setter.modifiers.none { modifier ->
            modifier == PRIVATE || modifier == PROTECTED
          }
        },
        hasBackingField = property.hasBackingField,
        isDelegated = property.isDelegated()
      )
    }
}

internal fun ModelPropertyCandidate.toRoundPropertyMetadata(
  typeParameterResolver: TypeParameterResolver
) = RoundPropertyMetadata(
  roundTypeElement = sourceDeclaration.type.toRoundTypeElement(typeParameterResolver),
  sourceDeclaration = sourceDeclaration,
  isConstructorProperty = isConstructorProperty,
  isInherited = isInherited,
  isMutable = isMutable,
  isReadable = isReadable,
  isWritable = isWritable
)
