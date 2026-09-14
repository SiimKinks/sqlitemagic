package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.model.ModelKind.TABLE
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.hasAnyAnnotationWithSimpleName
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
  private val constructionCollector = ModelConstructionCollector(reporter)

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
    val construction = constructionCollector.collect(
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
  ): List<PropertyRoundElement> = scanModelPropertyCandidates(
    declaration = declaration,
    modelConstructor = primaryConstructor
  ).mapNotNull { candidate ->
    val property = candidate.sourceDeclaration
    when {
      !candidate.hasBackingField || candidate.isDelegated -> return@mapNotNull null
      !persistAll && !property.hasAnyAnnotationWithSimpleName(
        EXPLICIT_PERSISTENCE_ANNOTATIONS
      ) -> return@mapNotNull null
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
      metadata = candidate.toRoundPropertyMetadata(
        property.parentDeclaration.typeParameterResolver()
      ),
      annotations = annotations
    )
  }
}

internal enum class ModelKind(
  private val annotationLabel: String
) {
  TABLE("@${Table::class.simpleName}"),
  EMBEDDED("@${Embedded::class.simpleName}"),
  VIEW("@${View::class.simpleName}");

  override fun toString() = annotationLabel
}
