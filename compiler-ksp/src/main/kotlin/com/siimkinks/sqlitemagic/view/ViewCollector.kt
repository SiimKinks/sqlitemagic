package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier.JAVA_STATIC
import com.google.devtools.ksp.symbol.Modifier.LATEINIT
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.element.toParsedType
import com.siimkinks.sqlitemagic.element.toRoundTypeElement
import com.siimkinks.sqlitemagic.model.ModelCollectionReporter
import com.siimkinks.sqlitemagic.model.ModelKind.VIEW
import com.siimkinks.sqlitemagic.model.PropertyAccess
import com.siimkinks.sqlitemagic.model.PropertyPath
import com.siimkinks.sqlitemagic.model.defaultSqlName
import com.siimkinks.sqlitemagic.model.generatedArtifactStem
import com.siimkinks.sqlitemagic.model.isSupportedEmbeddedDeclaration
import com.siimkinks.sqlitemagic.model.validateRootModelDeclaration
import com.siimkinks.sqlitemagic.schema.ArtifactStemOwner
import com.siimkinks.sqlitemagic.schema.ArtifactStemRegistry
import com.siimkinks.sqlitemagic.schema.SchemaIdentityOwner
import com.siimkinks.sqlitemagic.schema.SchemaIdentityRegistry
import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.LINE_BREAK
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.NUL
import com.siimkinks.sqlitemagic.schema.SqliteIdentifierProblem.RESERVED_PREFIX
import com.siimkinks.sqlitemagic.schema.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.artifactStemCollisionMessage
import com.siimkinks.sqlitemagic.schema.sqliteIdentifierProblem
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.isAccessibleFromGeneratedCode
import com.siimkinks.sqlitemagic.utils.isEffectivelyPublic
import com.siimkinks.sqlitemagic.utils.isUncheckedAnnotationPresent
import com.siimkinks.sqlitemagic.utils.typeParameterResolver
import com.siimkinks.sqlitemagic.schema.SqliteSchema.TEMPORARY as TEMPORARY_SCHEMA

internal class ViewCollector(
  private val environment: Environment,
  private val compiledSelectType: KSType
) {
  private val reporter = ModelCollectionReporter(environment)
  private val shapeCollector = ViewShapeCollector(reporter)
  private val seeds = linkedMapOf<TypeKey, ViewSeed>()

  fun collect(declarations: List<KSClassDeclaration>): Boolean {
    declarations.forEachIndexed { index, declaration ->
      collectSeed(
        declaration = declaration,
        declarationOrder = environment.viewElements.size + index
      )?.let { seed ->
        seeds[seed.typeKey] = seed
      }
    }
    validateArtifactStems()
    validateSchemaIdentities()
    if (reporter.hasErrors) return false

    val views = ViewSeedResolver(
      environment = environment,
      seeds = seeds,
      reporter = reporter
    ).resolve()
    if (reporter.hasErrors) return false

    views.forEach { view ->
      val seed = seeds.getValue(view.typeKey)
      environment.addViewElement(
        ViewRoundElement(
          view = view,
          originatingFiles = ViewOriginatingFilesCollector(
            environment = environment,
            seeds = seeds
          ).collect(seed),
          sourceDeclaration = seed.declaration,
          sourceProperties = seed.sourceProperties(),
          query = seed.query
        )
      )
    }
    return true
  }

  private fun collectSeed(
    declaration: KSClassDeclaration,
    declarationOrder: Int
  ): ViewSeed? {
    val annotation = declaration.findAnnotationWithType<View>() ?: return null
    if (declaration.isUncheckedAnnotationPresent<Table>()) {
      error(
        message = "@Table and @View cannot be used together: ${declaration.displayName()}",
        symbol = declaration
      )
      return null
    }
    val isValidDeclaration = validateRootModelDeclaration(
      declaration = declaration,
      modelKind = VIEW,
      reporter = reporter
    )
    if (!isValidDeclaration) return null
    val parsedType = declaration
      .asStarProjectedType()
      .toParsedType(declaration.typeParameterResolver())
    val query = collectQuery(declaration) ?: return null
    val rootPath = PropertyPath(listOf(declaration.simpleName.asString()))
    val shape = shapeCollector.collect(
      declaration = declaration,
      rootPath = rootPath,
      selection = ViewShapeSelection.ROOT
    ) ?: return null
    val properties = shape.properties.mapNotNull { property ->
      collectPropertySeed(
        viewName = declaration.displayName(),
        property = property,
        path = PropertyPath(listOf(property.name)),
        prefixes = emptyList(),
        embeddingTypes = emptySet()
      )
    }
    if (properties.isEmpty() && !reporter.hasErrors) {
      error(
        message = "@View must define at least one mapped property: ${declaration.displayName()}",
        symbol = declaration
      )
      return null
    }
    return ViewSeed(
      declaration = declaration,
      parsedType = parsedType,
      identity = SqliteSchemaIdentity(
        schema = when {
          TEMPORARY in annotation.options -> TEMPORARY_SCHEMA
          else -> MAIN
        },
        identifier = SqliteIdentifier.from(
          annotation.value
            .takeIf(String::isNotEmpty)
            ?: declaration.defaultSqlName()
        )
      ),
      artifactStem = declaration.generatedArtifactStem(),
      declarationOrder = declarationOrder,
      construction = shape.construction,
      query = query,
      properties = properties,
      isPublic = declaration.isEffectivelyPublic()
    )
  }

  private fun collectQuery(declaration: KSClassDeclaration): ViewQueryRoundElement? {
    val queryProperties = declaration.directQueryProperties()
    if (queryProperties.size != 1) {
      error(
        message = "@View must declare exactly one @ViewQuery: ${declaration.displayName()}",
        symbol = declaration
      )
      return null
    }
    val query = queryProperties.single()
    val parent = query.parentDeclaration as? KSClassDeclaration
    val isCompanion = parent?.isCompanionObject == true && parent.parentDeclaration == declaration
    val isStatic = parent == declaration && JAVA_STATIC in query.modifiers
    val displayName = "${declaration.displayName()}.${query.simpleName.asString()}"
    when {
      !isCompanion && !isStatic -> return errorQuery(
        message = "@ViewQuery must be a companion or static property: $displayName",
        symbol = query
      )
      query.isMutable || LATEINIT in query.modifiers -> return errorQuery(
        message = "@ViewQuery property must be immutable: $displayName",
        symbol = query
      )
      !query.isAccessibleFromGeneratedCode() -> return errorQuery(
        message = "@ViewQuery property must be accessible to generated code: $displayName",
        symbol = query
      )
    }
    val roundType = query.type.toRoundTypeElement(query.parentDeclaration.typeParameterResolver())
    when {
      roundType.type.nullability == NULLABLE -> return errorQuery(
        message = "@ViewQuery property must not be nullable: $displayName",
        symbol = query
      )
      !compiledSelectType.isAssignableFrom(roundType.type) -> return errorQuery(
        message = "@ViewQuery property must have a CompiledSelect type: $displayName",
        symbol = query
      )
    }
    return ViewQueryRoundElement(
      sourceDeclaration = query,
      roundTypeElement = roundType
    )
  }

  private fun collectPropertySeed(
    viewName: String,
    property: ViewPropertyRoundElement,
    path: PropertyPath,
    prefixes: List<String>,
    embeddingTypes: Set<TypeKey>
  ): ViewPropertySeed? {
    val annotations = property.annotations
    val tableOnlyAnnotation = when {
      annotations.column != null -> "@Column"
      annotations.id != null -> "@Id"
      annotations.index != null -> "@Index"
      annotations.unique != null -> "@Unique"
      else -> null
    }
    if (tableOnlyAnnotation != null) {
      error(
        message = "$tableOnlyAnnotation is not valid on a @View property: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    val embedded = annotations.embedded
    if (embedded != null) {
      if (annotations.viewColumn != null || annotations.ignoreColumn != null) {
        error(
          message = "@Embedded cannot be combined with other mapping annotations: $viewName.${path.displayName}",
          symbol = property.sourceDeclaration
        )
        return null
      }
      return collectEmbeddedSeed(
        viewName = viewName,
        property = property,
        path = path,
        prefixes = prefixes,
        embeddingTypes = embeddingTypes,
        prefix = embedded.prefix
      )
    }
    val baseKey = annotations.viewColumn
      ?.value
      ?: property.name.camelCaseToSnakeCase()
    if (baseKey.isBlank()) {
      error(
        message = "@ViewColumn key must not be blank: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    return ViewLeafSeed(
      roundElement = property,
      access = PropertyAccess(
        path = path,
        isMutable = property.isMutable
      ),
      selectionKey = prefixes.joinToString(separator = "") + baseKey
    )
  }

  private fun collectEmbeddedSeed(
    viewName: String,
    property: ViewPropertyRoundElement,
    path: PropertyPath,
    prefixes: List<String>,
    embeddingTypes: Set<TypeKey>,
    prefix: String
  ): ViewEmbeddedSeed? {
    val declaration = property.declaration
    if (
      property.sqlStorageType != null ||
      environment.transformerElements[property.typeKey] != null ||
      declaration == null
    ) {
      error(
        message = "@Embedded requires a non-scalar value type: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (
      declaration.isUncheckedAnnotationPresent<Table>() ||
      declaration.isUncheckedAnnotationPresent<View>()
    ) {
      error(
        message = "A @Table or @View model cannot be used as an embedded value: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (property.typeKey in embeddingTypes) {
      error(
        message = "Cyclic embedded property graph: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (!declaration.isSupportedEmbeddedDeclaration()) {
      error(
        message = "Unsupported @Embedded model shape: $viewName.${path.displayName}; ${declaration.displayName()}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    val shape = shapeCollector.collect(
      declaration = declaration,
      rootPath = path,
      selection = ViewShapeSelection.EMBEDDED
    ) ?: return null
    val childPrefixes = prefixes + prefix
    val children = shape.properties.mapNotNull { child ->
      collectPropertySeed(
        viewName = viewName,
        property = child,
        path = path.child(child.name),
        prefixes = childPrefixes,
        embeddingTypes = embeddingTypes + property.typeKey
      )
    }
    if (children.isEmpty() && !reporter.hasErrors) {
      error(
        message = "Embedded view value must define at least one mapped leaf: $viewName.${path.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    return ViewEmbeddedSeed(
      roundElement = property,
      access = PropertyAccess(
        path = path,
        isMutable = property.isMutable
      ),
      prefix = prefix,
      cumulativePrefix = childPrefixes.joinToString(separator = ""),
      construction = shape.construction,
      properties = children
    )
  }

  private fun validateArtifactStems() {
    val accepted = ArtifactStemRegistry(
      owners = buildList {
        environment.tableElements.values.forEach { table ->
          add(
            ArtifactStemOwner(
              typeKey = table.typeKey,
              qualifiedName = table.qualifiedName,
              artifactStem = table.artifactStem
            )
          )
        }
        environment.viewElements.values.forEach { view ->
          add(
            ArtifactStemOwner(
              typeKey = view.typeKey,
              qualifiedName = view.qualifiedName,
              artifactStem = view.artifactStem
            )
          )
        }
      }
    )
    seeds.values.forEach { seed ->
      val previous = accepted.lookup(seed.artifactStem)
      when {
        previous == null -> accepted.register(
          ArtifactStemOwner(
            typeKey = seed.typeKey,
            qualifiedName = seed.qualifiedName,
            artifactStem = seed.artifactStem
          )
        )
        previous.typeKey != seed.typeKey -> error(
          message = artifactStemCollisionMessage(
            artifactStem = seed.artifactStem,
            qualifiedNames = listOf(previous.qualifiedName, seed.qualifiedName),
            includeRenameSuggestion = false
          ),
          symbol = seed.declaration
        )
      }
    }
  }

  private fun validateSchemaIdentities() {
    val accepted = SchemaIdentityRegistry(
      owners = buildList {
        environment.tableElements.values.forEach { table ->
          add(
            SchemaIdentityOwner(
              kind = "table",
              rawName = table.tableName,
              identity = table.identity,
              typeKey = table.typeKey
            )
          )
        }
        environment.viewElements.values.forEach { view ->
          add(
            SchemaIdentityOwner(
              kind = "view",
              rawName = view.viewName,
              identity = view.identity,
              typeKey = view.typeKey
            )
          )
        }
        environment.indexElements.values.forEach { index ->
          add(
            SchemaIdentityOwner(
              kind = "index",
              rawName = index.name,
              identity = index.identity
            )
          )
        }
      }
    )
    seeds.values.forEach { seed ->
      val name = seed.rawName
      when (seed.sqliteIdentifierProblem()) {
        LINE_BREAK -> error(
          message = "View name must not contain line breaks: $name",
          symbol = seed.declaration
        )
        NUL -> error(
          message = "View name must not contain NUL characters",
          symbol = seed.declaration
        )
        RESERVED_PREFIX -> error(
          message = "View name '$name' is reserved",
          symbol = seed.declaration
        )
        null -> {
          val previous = accepted.lookup(seed.normalizedKey)
          when {
            previous == null -> accepted.register(
              SchemaIdentityOwner(
                kind = "view",
                rawName = name,
                identity = seed.identity,
                typeKey = seed.typeKey
              )
            )
            else -> error(
              message = "View name '$name' conflicts with ${previous.kind} name '${previous.rawName}': " +
                  seed.declaration.displayName(),
              symbol = seed.declaration
            )
          }
        }
      }
    }
  }

  private fun errorQuery(
    message: String,
    symbol: KSAnnotated
  ): ViewQueryRoundElement? {
    error(
      message = message,
      symbol = symbol
    )
    return null
  }

  private fun error(
    message: String,
    symbol: KSAnnotated?
  ) = reporter.error(
    message = message,
    symbol = symbol
  )
}

private fun KSClassDeclaration.directQueryProperties(): List<KSPropertyDeclaration> = buildList {
  declarations
    .filterIsInstance<KSPropertyDeclaration>()
    .filter(KSPropertyDeclaration::isViewQuery)
    .toCollection(this)
  declarations
    .filterIsInstance<KSClassDeclaration>()
    .filter(KSClassDeclaration::isCompanionObject)
    .flatMap(KSClassDeclaration::declarations)
    .filterIsInstance<KSPropertyDeclaration>()
    .filter(KSPropertyDeclaration::isViewQuery)
    .toCollection(this)
}

private fun KSPropertyDeclaration.isViewQuery() = findAnnotationWithType<ViewQuery>() != null
