package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.Embedded
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.element.toParsedType
import com.siimkinks.sqlitemagic.model.ModelKind.EMBEDDED
import com.siimkinks.sqlitemagic.model.ModelKind.TABLE
import com.siimkinks.sqlitemagic.schema.CollectionObjectValidationRegistry
import com.siimkinks.sqlitemagic.schema.SqliteIdentifier
import com.siimkinks.sqlitemagic.schema.SqliteSchema
import com.siimkinks.sqlitemagic.schema.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.schema.artifactStemCollisionMessage
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType
import com.siimkinks.sqlitemagic.utils.isEffectivelyPublic
import com.siimkinks.sqlitemagic.utils.isUncheckedAnnotationPresent
import com.siimkinks.sqlitemagic.utils.typeParameterResolver

internal class ModelCollector(
  private val environment: Environment
) {
  private val tableSeeds = linkedMapOf<TypeKey, TableSeed>()
  private val reporter = ModelCollectionReporter(environment)
  private val shapeCollector = ModelShapeCollector(reporter)

  fun collect(declarations: List<KSClassDeclaration>): Boolean {
    declarations.forEachIndexed { declarationOrder, declaration ->
      collectTableSeed(
        declaration = declaration,
        declarationOrder = environment.tableElements.size + declarationOrder
      )?.let { seed ->
        tableSeeds[seed.typeKey] = seed
      }
    }
    val validation = environment.collectionObjectValidationRegistry
    validateTableNames(validation)
    validateArtifactStems(validation)
    if (reporter.hasErrors) return false

    val tables = TableSeedResolver(
      environment = environment,
      tableSeeds = tableSeeds,
      reporter = reporter
    ).resolve()
    validateRelationshipCycles(tables)
    if (reporter.hasErrors) return false
    tables.forEach { table ->
      val seed = tableSeeds.getValue(table.typeKey)
      environment.addTableElement(
        TableRoundElement(
          table = table,
          originatingFiles = OriginatingFilesCollector(
            environment = environment,
            tableSeeds = tableSeeds
          ).collect(seed),
          sourceDeclaration = seed.classDeclaration,
          sourceProperties = sourceProperties(
            seed = seed,
            table = table
          )
        )
      )
    }
    return true
  }

  private fun collectTableSeed(
    declaration: KSClassDeclaration,
    declarationOrder: Int
  ): TableSeed? {
    val tableAnnotation = declaration.findAnnotationWithType<Table>() ?: return null
    val isValidDeclaration = validateRootModelDeclaration(
      declaration = declaration,
      modelKind = TABLE,
      reporter = reporter
    )
    if (!isValidDeclaration) return null
    val parsedType = declaration
      .asStarProjectedType()
      .toParsedType(declaration.typeParameterResolver())
    val tableName = tableAnnotation
      .value
      .takeIf(String::isNotEmpty)
      ?: declaration.defaultSqlName()
    val options = tableAnnotation.options.toSet()
    val identity = SqliteSchemaIdentity(
      schema = when {
        TEMPORARY in options -> SqliteSchema.TEMPORARY
        else -> SqliteSchema.MAIN
      },
      identifier = SqliteIdentifier.from(tableName)
    )
    val rootPath = PropertyPath(listOf(declaration.simpleName.asString()))
    val shape = shapeCollector.collect(
      declaration = declaration,
      persistAll = tableAnnotation.persistAll,
      rootPath = rootPath,
      modelKind = TABLE
    ) ?: return null
    val logicalProperties = shape.properties.mapNotNull { property ->
      collectPropertySeed(
        tableName = tableName,
        tableDisplayName = declaration.displayName(),
        property = property,
        propertyPath = PropertyPath(listOf(property.sourceDeclaration.simpleName.asString())),
        inheritedNullable = false,
        embeddedPrefixes = emptyList(),
        embeddingTypes = emptySet()
      )
    }
    val idSeeds = logicalProperties
      .filterIsInstance<ColumnSeed>()
      .filter { it.idAnnotation != null }
    if (idSeeds.size > 1) {
      error(
        message = "Table must declare at most one @Id: ${idSeeds.joinToString(transform = ColumnSeed::diagnosticPath)}",
        symbol = declaration
      )
    }
    return TableSeed(
      classDeclaration = declaration,
      parsedType = parsedType,
      identity = identity,
      artifactStem = declaration.generatedArtifactStem(),
      declarationOrder = declarationOrder,
      options = options,
      construction = shape.construction,
      propertySeeds = logicalProperties,
      isPublic = declaration.isEffectivelyPublic()
    )
  }

  private fun collectPropertySeed(
    tableName: String,
    tableDisplayName: String,
    property: PropertyRoundElement,
    propertyPath: PropertyPath,
    inheritedNullable: Boolean,
    embeddedPrefixes: List<String>,
    embeddingTypes: Set<TypeKey>
  ): PropertySeed? {
    val annotations = property.annotations
    val embeddedAnnotation = annotations.embedded
    if (embeddedAnnotation != null) {
      return collectEmbeddedPropertySeed(
        tableName = tableName,
        tableDisplayName = tableDisplayName,
        property = property,
        propertyPath = propertyPath,
        inheritedNullable = inheritedNullable,
        embeddedPrefixes = embeddedPrefixes,
        embeddingTypes = embeddingTypes,
        embeddedAnnotation = embeddedAnnotation
      )
    }
    val columnAnnotation = annotations.column
    val transformer = environment.transformerElements[property.typeKey]
    val relationshipTypeKey = property.typeKey.takeIf { typeKey ->
      transformer == null && (
          typeKey in tableSeeds ||
              typeKey in environment.tableElements ||
              property.declaration?.isUncheckedAnnotationPresent<Table>() == true
          )
    }
    if (property.sqlStorageType == null && transformer == null && relationshipTypeKey == null) {
      error(
        message = "Persisted property type must be a supported SQLite type, transformed type, or @Table relationship: $tableDisplayName.${propertyPath.displayName}; ${property.qualifiedName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    val rawColumnName = columnAnnotation
      ?.value
      ?.takeIf(String::isNotEmpty)
      ?: property.name.camelCaseToSnakeCase()
    val columnName = embeddedPrefixes.joinToString(separator = "") + rawColumnName
    val isNullable = property.canBeNull
    val isSchemaNullable = inheritedNullable || isNullable
    val indexAnnotation = annotations.index
    val index = indexAnnotation?.let { annotation ->
      FieldIndexElement(
        name = annotation.value
          .takeIf(String::isNotEmpty)
          ?: "index_${tableName}_$columnName",
        isUnique = annotation.unique
      )
    }
    return ColumnSeed(
      roundElement = property,
      diagnosticPath = "$tableDisplayName.${propertyPath.displayName}",
      access = PropertyAccess(
        path = propertyPath,
        isMutable = property.isMutable
      ),
      columnName = columnName,
      isSchemaNullable = isSchemaNullable,
      explicitDefaultValue = columnAnnotation
        ?.defaultValue
        ?.takeIf(String::isNotEmpty),
      transformer = transformer,
      relationshipTypeKey = relationshipTypeKey,
      isHandledRecursively = columnAnnotation?.handleRecursively ?: true,
      onDeleteCascade = columnAnnotation?.onDeleteCascade ?: false,
      idAnnotation = annotations.id,
      isUnique = annotations.unique != null,
      index = index,
      belongsToIndex = columnAnnotation
        ?.belongsToIndex
        ?.takeIf(String::isNotEmpty),
      embeddedPrefixes = embeddedPrefixes
    )
  }

  private fun collectEmbeddedPropertySeed(
    tableName: String,
    tableDisplayName: String,
    property: PropertyRoundElement,
    propertyPath: PropertyPath,
    inheritedNullable: Boolean,
    embeddedPrefixes: List<String>,
    embeddingTypes: Set<TypeKey>,
    embeddedAnnotation: Embedded
  ): PropertySeed? {
    val conflictingAnnotation = property.annotations.run {
      column != null || id != null || ignoreColumn != null || index != null || unique != null
    }
    if (conflictingAnnotation) {
      error(
        message = "@Embedded cannot be combined with physical-column annotations: $tableDisplayName.${propertyPath.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (property.sqlStorageType != null || environment.transformerElements[property.typeKey] != null) {
      error(
        message = "@Embedded requires a non-scalar value type: $tableDisplayName.${propertyPath.displayName}; ${property.qualifiedName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    val declaration = property.declaration
    if (declaration == null) {
      error(
        message = "Unsupported @Embedded model shape: $tableDisplayName.${propertyPath.displayName}; ${property.qualifiedName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (declaration.isUncheckedAnnotationPresent<Table>() || property.typeKey in tableSeeds) {
      error(
        message = "A @Table model cannot be used as an embedded value: $tableDisplayName.${propertyPath.displayName}; ${declaration.displayName()}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (property.typeKey in embeddingTypes) {
      error(
        message = "Cyclic embedded property graph: $tableDisplayName.${propertyPath.displayName}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    if (!declaration.isSupportedEmbeddedDeclaration()) {
      error(
        message = "Unsupported @Embedded model shape: $tableDisplayName.${propertyPath.displayName}; ${declaration.displayName()}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    val shape = shapeCollector.collect(
      declaration = declaration,
      persistAll = true,
      rootPath = propertyPath,
      modelKind = EMBEDDED
    ) ?: return null
    val prefix = embeddedAnnotation.prefix
    val childPrefixes = embeddedPrefixes + prefix
    val errorsBeforeChildren = reporter.errorCount
    val childSeeds = shape.properties.mapNotNull { child ->
      val childPath = propertyPath.child(child.name)
      if (child.annotations.id != null) {
        error(
          message = "@Id is not allowed inside an embedded value: $tableDisplayName.${childPath.displayName}",
          symbol = child.sourceDeclaration
        )
        return@mapNotNull null
      }
      collectPropertySeed(
        tableName = tableName,
        tableDisplayName = tableDisplayName,
        property = child,
        propertyPath = childPath,
        inheritedNullable = inheritedNullable || property.canBeNull,
        embeddedPrefixes = childPrefixes,
        embeddingTypes = embeddingTypes + property.typeKey
      )
    }
    if (childSeeds.isEmpty() && reporter.errorCount == errorsBeforeChildren) {
      error(
        message = "Embedded value must define at least one persisted leaf column: $tableDisplayName.${propertyPath.displayName}; ${declaration.displayName()}",
        symbol = property.sourceDeclaration
      )
      return null
    }
    return EmbeddedSeed(
      roundElement = property,
      access = PropertyAccess(
        path = propertyPath,
        isMutable = property.isMutable
      ),
      prefix = prefix,
      cumulativePrefix = childPrefixes.joinToString(separator = ""),
      construction = shape.construction,
      properties = childSeeds
    )
  }

  private fun validateArtifactStems(validation: CollectionObjectValidationRegistry) {
    val accepted = validation.artifactStemRegistry
    for (seed in tableSeeds.values) {
      val existing = accepted.lookup(seed.artifactStem)
      if (existing != null && existing.typeKey != seed.typeKey) {
        error(
          message = artifactStemCollisionMessage(
            artifactStem = seed.artifactStem,
            qualifiedNames = listOf(existing.qualifiedName, seed.qualifiedName),
            includeRenameSuggestion = true
          ),
          symbol = seed.classDeclaration
        )
      }
    }
    tableSeeds.values
      .groupBy(TableSeed::artifactStem)
      .values
      .filter { it.size > 1 }
      .forEach { seeds ->
        val first = seeds.first()
        error(
          message = artifactStemCollisionMessage(
            artifactStem = first.artifactStem,
            qualifiedNames = seeds.map(TableSeed::qualifiedName),
            includeRenameSuggestion = true
          ),
          symbol = first.classDeclaration
        )
      }
  }

  private fun validateTableNames(validation: CollectionObjectValidationRegistry) {
    val accepted = validation.schemaIdentityRegistry
    for (seed in tableSeeds.values) {
      val existing = accepted.lookup(seed.normalizedKey)
      if (existing != null && existing.typeKey != seed.typeKey) {
        when (existing.kind) {
          "table" -> {
            val existingQualifiedName = existing.typeKey
              ?.let(environment.tableElements::get)
              ?.qualifiedName
              ?: existing.rawName
            error(
              message = "SQL table name '${seed.tableName}' is ambiguous: $existingQualifiedName, ${seed.qualifiedName}",
              symbol = seed.classDeclaration
            )
          }
          else -> error(
            message = "SQL table name '${seed.tableName}' conflicts with ${existing.kind} name '${existing.rawName}': ${seed.qualifiedName}",
            symbol = seed.classDeclaration
          )
        }
      }
    }
    tableSeeds.values
      .groupBy(TableSeed::normalizedKey)
      .values
      .filter { it.size > 1 }
      .forEach { seeds ->
        val first = seeds.first()
        error(
          message = "SQL table name '${first.tableName}' is ambiguous: ${seeds.joinToString(transform = TableSeed::qualifiedName)}",
          symbol = first.classDeclaration
        )
      }
  }

  private fun validateRelationshipCycles(tables: List<TableElement>) {
    val finder = StrongComponentsFinder(environment.tableElements.values + tables)
    if (finder.hasStrongComponents) {
      error(
        message = finder.diagnostic,
        symbol = null
      )
    }
  }

  private fun sourceProperties(
    seed: TableSeed,
    table: TableElement
  ): Map<PropertyPath, KSPropertyDeclaration> {
    val materializedPaths = table.allColumns
      .map(ColumnElement::access)
      .map(PropertyAccess::path)
      .toSet()
    return seed.propertySeeds
      .asSequence()
      .flatMap(PropertySeed::sourceProperties)
      .filter { (path, _) -> path in materializedPaths }
      .toMap()
  }

  private fun error(
    message: String,
    symbol: KSAnnotated?
  ) = reporter.error(
    message = message,
    symbol = symbol
  )
}

private fun PropertySeed.sourceProperties(): Sequence<Pair<PropertyPath, KSPropertyDeclaration>> = when (this) {
  is ColumnSeed -> sequenceOf(roundElement.sourceDeclaration)
    .filterIsInstance<KSPropertyDeclaration>()
    .map { sourceDeclaration -> access.path to sourceDeclaration }
  is EmbeddedSeed -> properties
    .asSequence()
    .flatMap(PropertySeed::sourceProperties)
}
