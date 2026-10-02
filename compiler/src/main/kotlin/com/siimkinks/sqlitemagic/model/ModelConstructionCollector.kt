package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.siimkinks.sqlitemagic.model.ModelKind.EMBEDDED
import com.siimkinks.sqlitemagic.model.ModelKind.TABLE
import com.siimkinks.sqlitemagic.model.ModelKind.VIEW
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.isAccessibleFromGeneratedCode

internal class ModelConstructionCollector(
  private val reporter: ModelCollectionReporter
) {
  fun collect(
    declaration: KSClassDeclaration,
    properties: List<RoundPropertyShape>,
    rootPath: PropertyPath,
    modelKind: ModelKind,
    primaryConstructor: KSFunctionDeclaration?
  ): ModelConstruction? {
    fun propertyPath(name: String) = when (modelKind) {
      TABLE,
      VIEW -> PropertyPath(listOf(name))
      EMBEDDED -> rootPath.child(name)
    }

    fun diagnosticPath(name: String) = rootPath.child(name).displayName

    val localProperties = properties.filterNot(RoundPropertyShape::isInherited)
    val parameterNames = primaryConstructor
      ?.parameters
      .orEmpty()
      .mapNotNull { it.name?.asString() }
    val localNames = localProperties.map(RoundPropertyShape::name)
    val constructorProperties = localProperties.filter(RoundPropertyShape::isConstructorProperty)
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
      val inherited = properties.firstOrNull(RoundPropertyShape::isInherited)
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
    if (properties.isNotEmpty() && properties.all(RoundPropertyShape::isMutable)) {
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
