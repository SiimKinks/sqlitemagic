package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.model.ModelCollectionReporter
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.generatedFieldName

internal class ViewSeedResolver(
  private val environment: Environment,
  private val seeds: Map<TypeKey, ViewSeed>,
  private val reporter: ModelCollectionReporter
) {
  private val resolvedViews = mutableMapOf<TypeKey, ViewElement?>()

  fun resolve() = seeds.values.mapNotNull {
    resolveView(
      seed = it,
      projectionPath = emptySet()
    )
  }

  private fun resolveView(
    seed: ViewSeed,
    projectionPath: Set<TypeKey>
  ): ViewElement? {
    if (seed.typeKey in resolvedViews) return resolvedViews[seed.typeKey]
    if (seed.typeKey in projectionPath) {
      reporter.error(
        message = "Cyclic view projection graph: ${seed.declaration.displayName()}",
        symbol = seed.declaration
      )
      return null
    }
    val view = reporter.takeIfNoNewErrors {
      val viewProjectionPath = projectionPath + seed.typeKey
      ViewElement(
        parsedType = seed.parsedType,
        identity = seed.identity,
        artifactStem = seed.artifactStem,
        declarationOrder = seed.declarationOrder,
        moduleName = environment.submoduleName,
        construction = seed.construction,
        queryPropertyName = seed.query.name,
        properties = seed.properties.mapNotNull {
          resolveProperty(
            property = it,
            projectionPath = viewProjectionPath
          )
        },
        isPublic = seed.isPublic
      )
    }
    resolvedViews[seed.typeKey] = view
    view?.let(::validatePropertyNames)
    return view
  }

  private fun resolveProperty(
    property: ViewPropertySeed,
    projectionPath: Set<TypeKey>
  ): ViewPropertyElement? = when (property) {
    is ViewEmbeddedSeed -> ViewEmbeddedPropertyElement(
      access = property.access,
      deserializedType = property.roundElement.parsedType,
      isNullable = property.roundElement.canBeNull,
      construction = property.construction,
      properties = property.properties.mapNotNull {
        resolveProperty(
          property = it,
          projectionPath = projectionPath
        )
      }
    )
    is ViewLeafSeed -> resolveLeaf(
      seed = property,
      projectionPath = projectionPath
    )
  }

  private fun resolveLeaf(
    seed: ViewLeafSeed,
    projectionPath: Set<TypeKey>
  ): ViewLeafElement? {
    val property = seed.roundElement
    val transformer = environment.transformerElements[property.typeKey]
    if (property.sqlStorageType != null || transformer != null) {
      return ViewScalarPropertyElement(
        access = seed.access,
        deserializedType = property.parsedType,
        isNullable = property.canBeNull,
        selectionKey = seed.selectionKey,
        generatedFieldName = seed.selectionKey.generatedFieldName(),
        transformer = transformer
      )
    }
    val target = environment.tableElements[property.typeKey]?.generationNames
      ?: environment.viewElements[property.typeKey]?.generationNames
      ?: seeds[property.typeKey]
        ?.let {
          resolveView(
            seed = it,
            projectionPath = projectionPath
          )
        }
        ?.generationNames
    if (target != null) {
      return ViewProjectionPropertyElement(
        access = seed.access,
        deserializedType = property.parsedType,
        isNullable = property.canBeNull,
        selectionKey = seed.selectionKey,
        target = target
      )
    }
    reporter.error(
      message = "@View property type must be a supported SQLite type, transformed type, @Table, or @View: " +
          "${seed.access.path.displayName}; ${property.qualifiedName}",
      symbol = property.sourceDeclaration
    )
    return null
  }

  private fun validatePropertyNames(view: ViewElement) {
    view.leaves
      .groupBy(ViewLeafElement::selectionKey)
      .values
      .filter { it.size > 1 }
      .forEach { duplicates ->
        val key = duplicates.first().selectionKey
        val paths = duplicates.joinToString { property ->
          "${view.modelName}.${property.access.path.displayName}"
        }
        reporter.error(
          message = "Duplicate @ViewColumn key '$key': $paths",
          symbol = seeds[view.typeKey]?.declaration
        )
      }
    view.scalarProperties
      .groupBy(ViewScalarPropertyElement::generatedFieldName)
      .values
      .filter { it.size > 1 }
      .forEach {
        reporter.error(
          message = "Duplicate generated view column name '${it.first().generatedFieldName}'",
          symbol = seeds[view.typeKey]?.declaration
        )
      }
  }
}
