package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.siimkinks.sqlitemagic.model.ModelCollectionReporter
import com.siimkinks.sqlitemagic.model.ModelConstruction
import com.siimkinks.sqlitemagic.model.ModelConstructionCollector
import com.siimkinks.sqlitemagic.model.ModelKind
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.model.scanModelPropertyCandidates
import com.siimkinks.sqlitemagic.model.toRoundPropertyMetadata
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.modelConstructor
import com.siimkinks.sqlitemagic.utils.typeParameterResolver

internal data class CollectedViewShape(
  val properties: List<ViewPropertyRoundElement>,
  val construction: ModelConstruction
)

internal enum class ViewShapeSelection {
  ROOT,
  EMBEDDED
}

internal class ViewShapeCollector(
  private val reporter: ModelCollectionReporter
) {
  private val constructionCollector = ModelConstructionCollector(reporter)

  fun collect(
    declaration: KSClassDeclaration,
    rootPath: PropertyPath,
    selection: ViewShapeSelection
  ): CollectedViewShape? {
    val primaryConstructor = declaration.modelConstructor()
    val constructionProperties = collectRoundProperties(
      declaration = declaration,
      primaryConstructor = primaryConstructor,
      selection = selection
    )
    val construction = constructionCollector.collect(
      declaration = declaration,
      properties = constructionProperties,
      rootPath = rootPath,
      modelKind = when (selection) {
        ViewShapeSelection.ROOT -> ModelKind.VIEW
        ViewShapeSelection.EMBEDDED -> ModelKind.EMBEDDED
      },
      primaryConstructor = primaryConstructor
    ) ?: return null
    return CollectedViewShape(
      properties = constructionProperties.filter { property ->
        when (selection) {
          ViewShapeSelection.ROOT -> property.annotations.isRootProperty()
          ViewShapeSelection.EMBEDDED -> property.annotations.ignoreColumn == null
        }
      },
      construction = construction
    )
  }

  private fun collectRoundProperties(
    declaration: KSClassDeclaration,
    primaryConstructor: KSFunctionDeclaration?,
    selection: ViewShapeSelection
  ): List<ViewPropertyRoundElement> = scanModelPropertyCandidates(
    declaration = declaration,
    modelConstructor = primaryConstructor
  ).mapNotNull { candidate ->
    val property = candidate.sourceDeclaration
    val annotations = ViewPropertyRoundAnnotations.from(property)
    val isOmittableConstructorProperty = candidate.isConstructorProperty && candidate.hasDefault
    if (selection == ViewShapeSelection.EMBEDDED &&
      annotations.ignoreColumn != null &&
      !isOmittableConstructorProperty
    ) {
      if (!candidate.hasBackingField || candidate.isDelegated) {
        reportUnsupportedProperty(property, declaration)
      }
      return@mapNotNull null
    }
    val selected = when (selection) {
      ViewShapeSelection.ROOT -> annotations.isRootProperty() || isOmittableConstructorProperty
      ViewShapeSelection.EMBEDDED -> true
    }
    if (!selected) return@mapNotNull null
    if (!candidate.hasBackingField || candidate.isDelegated) {
      if (annotations.viewColumn != null || annotations.embedded != null) {
        reportUnsupportedProperty(property, declaration)
      }
      return@mapNotNull null
    }
    ViewPropertyRoundElement(
      metadata = candidate.toRoundPropertyMetadata(
        property.parentDeclaration.typeParameterResolver()
      ),
      annotations = annotations
    )
  }

  private fun reportUnsupportedProperty(
    property: KSPropertyDeclaration,
    declaration: KSClassDeclaration
  ) {
    reporter.error(
      message = "@View properties must be field-backed and non-delegated: " +
          "${declaration.displayName()}.${property.simpleName.asString()}",
      symbol = property
    )
  }
}

private fun ViewPropertyRoundAnnotations.isRootProperty() =
  viewColumn != null ||
      embedded != null ||
      column != null ||
      id != null ||
      index != null ||
      unique != null
