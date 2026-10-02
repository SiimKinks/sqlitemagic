package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.model.ModelCollectionReporter
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.TableReadLayout
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.siimkinks.sqlitemagic.utils.displayName
import com.squareup.kotlinpoet.ClassName

internal class ViewSeedResolver(
  private val environment: Environment,
  private val seeds: Map<TypeKey, ViewSeed>,
  private val reporter: ModelCollectionReporter
) {
  private val resolvedViews = mutableMapOf<TypeKey, ViewElement?>()
  private val tableReadLayout = TableReadLayout(environment.tableElements)

  fun resolve(): List<ViewElement> = seeds.values.mapNotNull { seed ->
    resolveView(
      seed = seed,
      projectionPath = emptySet()
    )
  }

  private fun resolveView(
    seed: ViewSeed,
    projectionPath: Set<TypeKey>
  ): ViewElement? {
    if (seed.typeKey in resolvedViews) return resolvedViews[seed.typeKey]
    if (seed.typeKey in projectionPath) {
      error(
        message = "Cyclic view projection graph: ${seed.declaration.displayName()}",
        symbol = seed.declaration
      )
      return null
    }
    val errorsBefore = reporter.errorCount
    val properties = seed.properties.mapNotNull { property ->
      resolveProperty(
        property = property,
        projectionPath = projectionPath + seed.typeKey
      )
    }
    val view = ViewElement(
      parsedType = seed.parsedType,
      identity = seed.identity,
      artifactStem = seed.artifactStem,
      declarationOrder = seed.declarationOrder,
      moduleName = environment.submoduleName,
      construction = seed.construction,
      query = ViewQueryElement(
        ownerType = checkNotNull(seed.parsedType.typeName as? ClassName),
        propertyName = seed.query.name,
        declaredType = seed.query.typeName
      ),
      properties = properties,
      isPublic = seed.isPublic
    ).takeIf { reporter.errorCount == errorsBefore }
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
      prefix = property.prefix,
      cumulativePrefix = property.cumulativePrefix,
      construction = property.construction,
      properties = property.properties.mapNotNull { child ->
        resolveProperty(
          property = child,
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
    val table = environment.tableElements[property.typeKey]
    if (table != null) {
      return projection(
        seed = seed,
        target = table
      )
    }
    val view = environment.viewElements[property.typeKey]
      ?: seeds[property.typeKey]?.let { target ->
        resolveView(
          seed = target,
          projectionPath = projectionPath
        )
      }
    if (view != null) {
      return ViewProjectionPropertyElement(
        access = seed.access,
        deserializedType = property.parsedType,
        isNullable = property.canBeNull,
        selectionKey = seed.selectionKey,
        targetKind = ViewProjectionKind.VIEW,
        targetArtifactStem = view.artifactStem,
        targetConstruction = view.construction,
        expandedWidth = view.expandedWidth
      )
    }
    error(
      message = "@View property type must be a supported SQLite type, transformed type, @Table, or @View: " +
          "${seed.access.path.displayName}; ${property.qualifiedName}",
      symbol = property.sourceDeclaration
    )
    return null
  }

  private fun projection(
    seed: ViewLeafSeed,
    target: TableElement
  ) = ViewProjectionPropertyElement(
    access = seed.access,
    deserializedType = seed.roundElement.parsedType,
    isNullable = seed.roundElement.canBeNull,
    selectionKey = seed.selectionKey,
    targetKind = ViewProjectionKind.TABLE,
    targetArtifactStem = target.artifactStem,
    targetConstruction = target.construction,
    expandedWidth = tableReadLayout.width(
      table = target,
      recursive = true
    )
  )

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
        error(
          message = "Duplicate @ViewColumn key '$key': $paths",
          symbol = seeds[view.typeKey]?.declaration
        )
      }
    view.scalarProperties
      .groupBy(ViewScalarPropertyElement::generatedFieldName)
      .values
      .filter { it.size > 1 }
      .forEach { duplicates ->
        error(
          message = "Duplicate generated view column name '${duplicates.first().generatedFieldName}'",
          symbol = seeds[view.typeKey]?.declaration
        )
      }
  }

  private fun error(
    message: String,
    symbol: KSAnnotated?
  ) = reporter.error(
    message = message,
    symbol = symbol
  )
}

private fun String.generatedFieldName() =
  camelCaseToSnakeCase()
    .uppercase()
    .replace(oldChar = '.', newChar = '_')
