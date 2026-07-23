package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier.PRIVATE
import com.google.devtools.ksp.symbol.Modifier.PROTECTED
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.element.toRoundTypeElement
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.ModelKind.EMBEDDED
import com.siimkinks.sqlitemagic.model.ModelKind.TABLE
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.getLocalAndInheritedProperties
import com.siimkinks.sqlitemagic.utils.hasAnyAnnotationWithSimpleName
import com.siimkinks.sqlitemagic.utils.isAccessibleFromGeneratedCode
import com.siimkinks.sqlitemagic.utils.modelConstructor
import com.siimkinks.sqlitemagic.utils.typeParameterResolver

internal data class CollectedModelShape(
  val properties: List<PropertyRoundElement>,
  val construction: ModelConstruction
)

internal val EXPLICIT_PERSISTENCE_ANNOTATIONS = setOf(
  checkNotNull(Column::class.simpleName),
  checkNotNull(Embedded::class.simpleName),
  checkNotNull(Id::class.simpleName)
)

internal class ModelShapeCollector(
  private val reporter: ModelCollectionReporter
) {
  fun collect(
    declaration: KSClassDeclaration,
    persistAll: Boolean,
    rootPath: PropertyPath,
    modelKind: ModelKind
  ): CollectedModelShape? {
    val primaryConstructor = declaration.modelConstructor()
    val properties = collectRoundProperties(
      declaration = declaration,
      persistAll = persistAll,
      primaryConstructor = primaryConstructor
    )
    if (modelKind == TABLE && properties.isEmpty()) {
      reporter.error(
        message = "Table must define at least one persisted column: ${declaration.displayName()}",
        symbol = declaration
      )
      return null
    }
    val construction = classifyConstruction(
      declaration = declaration,
      properties = properties,
      rootPath = rootPath,
      modelKind = modelKind,
      primaryConstructor = primaryConstructor
    ) ?: return null
    return CollectedModelShape(
      properties = properties,
      construction = construction
    )
  }

  private fun collectRoundProperties(
    declaration: KSClassDeclaration,
    persistAll: Boolean,
    primaryConstructor: KSFunctionDeclaration?
  ): List<PropertyRoundElement> {
    val constructorParameters = primaryConstructor
      ?.parameters
      .orEmpty()
      .associateBy { it.name?.asString() }
    return declaration
      .getLocalAndInheritedProperties()
      .mapNotNull { property ->
        when {
          !property.hasBackingField || property.isDelegated() -> return@mapNotNull null
          !persistAll && !property.hasAnyAnnotationWithSimpleName(EXPLICIT_PERSISTENCE_ANNOTATIONS) -> return@mapNotNull null
        }
        val annotations = PropertyRoundAnnotations.from(property)
        val selected = when {
          annotations.embedded != null -> true
          annotations.ignoreColumn != null -> false
          persistAll -> true
          else -> annotations.column != null || annotations.id != null
        }
        if (!selected) return@mapNotNull null
        PropertyRoundElement(
          sourceDeclaration = property,
          roundTypeElement = property.type.toRoundTypeElement(
            property.parentDeclaration.typeParameterResolver()
          ),
          isConstructorProperty = constructorParameters[property.simpleName.asString()]
            ?.let { parameter -> parameter.isVal || parameter.isVar }
            ?: false,
          annotations = annotations,
          isInherited = property.parentDeclaration?.qualifiedName != declaration.qualifiedName,
          isMutable = property.isMutable,
          isReadable = property.isAccessibleFromGeneratedCode(),
          isWritable = property.isMutable && when (val setter = property.setter) {
            null -> property.isAccessibleFromGeneratedCode()
            else -> setter.modifiers.none { modifier ->
              modifier == PRIVATE || modifier == PROTECTED
            }
          }
        )
      }
  }

  private fun classifyConstruction(
    declaration: KSClassDeclaration,
    properties: List<PropertyRoundElement>,
    rootPath: PropertyPath,
    modelKind: ModelKind,
    primaryConstructor: KSFunctionDeclaration?
  ): ModelConstruction? {
    fun propertyPath(name: String) = when (modelKind) {
      TABLE -> PropertyPath(listOf(name))
      EMBEDDED -> rootPath.child(name)
    }

    fun diagnosticPath(name: String) = rootPath.child(name).displayName

    val localProperties = properties.filterNot(PropertyRoundElement::isInherited)
    val parameterNames = primaryConstructor
      ?.parameters
      .orEmpty()
      .mapNotNull { it.name?.asString() }
    val localNames = localProperties.map(PropertyRoundElement::name)
    val constructorProperties = localProperties.filter(PropertyRoundElement::isConstructorProperty)
    val exactConstructorSurface = primaryConstructor != null &&
        constructorProperties.size == primaryConstructor.parameters.size &&
        parameterNames == localNames
    val hasConstructorProperty = constructorProperties.isNotEmpty()
    if (exactConstructorSurface) {
      if (!primaryConstructor.isAccessibleFromGeneratedCode()) {
        reporter.error(
          message = "Constructor-backed $modelKind models require an accessible primary constructor: ${declaration.displayName()}",
          symbol = declaration
        )
        return null
      }
      val inherited = properties.firstOrNull(PropertyRoundElement::isInherited)
      if (inherited != null) {
        reporter.error(
          message = "Constructor-backed $modelKind models cannot persist inherited properties: ${
            diagnosticPath(
              inherited.name
            )
          }",
          symbol = inherited.sourceDeclaration
        )
        return null
      }
      val unreadable = properties.firstOrNull { !it.isReadable }
      if (unreadable != null) {
        reporter.error(
          message = "Constructor-backed $modelKind properties must be readable: ${diagnosticPath(unreadable.name)}",
          symbol = unreadable.sourceDeclaration
        )
        return null
      }
      return ModelConstruction(
        strategy = PRIMARY_CONSTRUCTOR,
        constructorParameters = parameterNames.map(::propertyPath),
        defaultableParameters = primaryConstructor.parameters
          .filter(KSValueParameter::hasDefault)
          .mapNotNull { it.name?.asString() }
          .mapTo(linkedSetOf(), ::propertyPath)
      )
    }

    if (hasConstructorProperty) {
      val unmatchedName = (parameterNames + localNames)
        .groupingBy(String::toString)
        .eachCount()
        .entries
        .firstOrNull { it.value == 1 }
        ?.key
        ?: declaration.simpleName.asString()
      val diagnosticPath = diagnosticPath(unmatchedName)
      reporter.error(
        message = "Constructor-backed $modelKind properties must correspond exactly to primary-constructor parameters: $diagnosticPath",
        symbol = declaration
      )
      return null
    }

    val hasZeroArgumentConstructor = declaration
      .getConstructors()
      .any { constructor ->
        constructor.isAccessibleFromGeneratedCode() &&
            constructor.parameters.all(KSValueParameter::hasDefault)
      }
    val inaccessibleProperty = properties.firstOrNull { property ->
      !property.isReadable || !property.isWritable
    }
    if (hasZeroArgumentConstructor && inaccessibleProperty == null) {
      return ModelConstruction(
        strategy = MUTABLE_PROPERTIES,
        constructorParameters = emptyList(),
        defaultableParameters = emptySet()
      )
    }
    if (hasZeroArgumentConstructor && inaccessibleProperty != null) {
      reporter.error(
        message = "Mutable $modelKind properties must be readable and writable: ${diagnosticPath(inaccessibleProperty.name)}",
        symbol = inaccessibleProperty.sourceDeclaration
      )
      return null
    }
    if (properties.isNotEmpty() && properties.all(PropertyRoundElement::isMutable)) {
      reporter.error(
        message = "Mutable $modelKind models require an accessible zero-argument constructor: ${declaration.displayName()}",
        symbol = declaration
      )
      return null
    }
    reporter.error(
      message = "Unsupported $modelKind model shape: ${declaration.displayName()}",
      symbol = declaration
    )
    return null
  }
}

internal enum class ModelKind(
  private val annotationLabel: String
) {
  TABLE("@${Table::class.simpleName}"),
  EMBEDDED("@${Embedded::class.simpleName}");

  override fun toString() = annotationLabel
}
