package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.Origin.SYNTHETIC
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqlAffinity.BLOB
import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.model.AutoIncrementMode.AUTOMATIC
import com.siimkinks.sqlitemagic.model.AutoIncrementMode.DISABLED
import com.siimkinks.sqlitemagic.model.AutoIncrementMode.ENABLED
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.getArgument

internal class TableSeedResolver(
  private val environment: Environment,
  private val tableSeeds: Map<TypeKey, TableSeed>,
  private val reporter: ModelCollectionReporter
) {
  private val resolvedIdColumns = mutableMapOf<TypeKey, ColumnElement?>()

  fun resolve() = tableSeeds
    .values
    .mapNotNull(::materializeTableElement)

  private fun materializeTableElement(seed: TableSeed): TableElement? = reporter
    .takeIfNoNewErrors {
      val properties = seed.propertySeeds.mapNotNull { property ->
        materializePropertyElement(
          tableSeed = seed,
          property = property,
          resolvingIds = emptySet()
        )
      }
      TableElement(
        parsedType = seed.parsedType,
        tableName = seed.tableName,
        artifactStem = seed.artifactStem,
        declarationOrder = seed.declarationOrder,
        options = seed.options,
        construction = seed.construction,
        properties = properties
      ).also { table ->
        validateTableColumns(
          table = table,
          seed = seed
        )
      }
    }

  private fun materializePropertyElement(
    tableSeed: TableSeed,
    property: PropertySeed,
    resolvingIds: Set<TypeKey>
  ): PropertyElement? = when (property) {
    is ColumnSeed -> resolveColumnElement(
      tableSeed = tableSeed,
      seed = property,
      resolvingIds = resolvingIds
    )?.let(::ColumnPropertyElement)
    is EmbeddedSeed -> EmbeddedPropertyElement(
      access = property.access,
      deserializedType = property.roundElement.parsedType,
      isNullable = property.roundElement.canBeNull,
      prefix = property.prefix,
      cumulativePrefix = property.cumulativePrefix,
      construction = property.construction,
      properties = property.properties.mapNotNull { child ->
        materializePropertyElement(
          tableSeed = tableSeed,
          property = child,
          resolvingIds = resolvingIds
        )
      }
    )
  }

  private fun resolveColumnElement(
    tableSeed: TableSeed,
    seed: ColumnSeed,
    resolvingIds: Set<TypeKey>
  ): ColumnElement? = when {
    seed === tableSeed.idSeed -> resolveIdColumn(
      tableSeed = tableSeed,
      resolvingIds = resolvingIds + tableSeed.typeKey
    )
    else -> materializeColumnElement(
      tableSeed = tableSeed,
      seed = seed,
      resolvingIds = resolvingIds
    )
  }

  private fun resolveIdColumn(
    tableSeed: TableSeed,
    resolvingIds: Set<TypeKey>
  ): ColumnElement? {
    if (tableSeed.typeKey in resolvedIdColumns) {
      return resolvedIdColumns[tableSeed.typeKey]
    }
    val idSeed = tableSeed.idSeed ?: return null
    val idColumn = materializeColumnElement(
      tableSeed = tableSeed,
      seed = idSeed,
      resolvingIds = resolvingIds
    )
    resolvedIdColumns[tableSeed.typeKey] = idColumn
    return idColumn
  }

  private fun materializeColumnElement(
    tableSeed: TableSeed,
    seed: ColumnSeed,
    resolvingIds: Set<TypeKey>
  ): ColumnElement? {
    val relationship = seed.relationshipTypeKey?.let { typeKey ->
      resolveRelationshipElement(
        source = seed,
        targetTypeKey = typeKey,
        resolvingIds = resolvingIds
      )
    }
    if (seed.relationshipTypeKey != null && relationship == null) return null
    val serializedType = relationship?.referencedIdSerializedType
      ?: seed.transformer?.serializedType
      ?: seed.roundElement.parsedType
    val storageType = serializedType.sqlStorageType
    if (storageType == null) {
      reporter.error(
        message = "Persisted property type must be a supported SQLite type, transformed type, or @Table relationship: ${seed.diagnosticPath}; ${seed.roundElement.qualifiedName}",
        symbol = seed.roundElement.sourceDeclaration
      )
      return null
    }
    if (seed.idAnnotation != null && storageType.affinity == BLOB) {
      reporter.error(
        message = "BLOB storage types cannot be used as explicit @Id columns: ${seed.diagnosticPath}",
        symbol = seed.roundElement.sourceDeclaration
      )
      return null
    }
    val autoIncrementMode = seed.idAnnotation?.autoIncrementMode()
    val id = autoIncrementMode?.let { mode ->
      val withoutRowId = WITHOUT_ROWID in tableSeed.options
      val compatible = storageType.affinity == INTEGER
      when {
        withoutRowId && mode == ENABLED -> reporter.error(
          message = "WITHOUT_ROWID does not support explicit auto-increment: ${seed.diagnosticPath}",
          symbol = seed.roundElement.sourceDeclaration
        )
        mode == ENABLED && !compatible -> reporter.error(
          message = "Explicit auto-increment requires an INTEGER-compatible ID: ${seed.diagnosticPath}",
          symbol = seed.roundElement.sourceDeclaration
        )
      }
      IdElement(
        autoIncrementMode = mode,
        isAutoIncrement = !withoutRowId && compatible && mode != DISABLED,
        canAssignGeneratedId = seed.access.isMutable
      )
    }
    return ColumnElement(
      access = seed.access,
      deserializedType = seed.roundElement.parsedType,
      isNullable = seed.roundElement.canBeNull,
      columnName = seed.columnName,
      isSchemaNullable = seed.isSchemaNullable,
      defaultValue = seed.explicitDefaultValue ?: when {
        seed.isSchemaNullable -> "NULL"
        else -> storageType.affinity.defaultValue
      },
      transformer = seed.transformer,
      relationship = relationship,
      id = id,
      isUnique = seed.isUnique,
      index = seed.index,
      belongsToIndex = seed.belongsToIndex,
      embeddedPrefixes = seed.embeddedPrefixes
    )
  }

  private fun resolveRelationshipElement(
    source: ColumnSeed,
    targetTypeKey: TypeKey,
    resolvingIds: Set<TypeKey>
  ): RelationshipElement? {
    val durableTarget = environment.tableElements[targetTypeKey]
    if (durableTarget != null) {
      val targetId = durableTarget.idColumn
      if (targetId == null) {
        reporter.error(
          message = "Relationship target must declare an explicit @Id: ${source.diagnosticPath}; ${durableTarget.modelName}",
          symbol = source.roundElement.sourceDeclaration
        )
        return null
      }
      return createRelationshipElement(
        source = source,
        targetType = durableTarget.parsedType,
        targetTableName = durableTarget.tableName,
        targetConstruction = durableTarget.construction,
        targetId = targetId
      )
    }
    val targetSeed = tableSeeds[targetTypeKey]
    if (targetSeed == null) {
      reporter.error(
        message = "Persisted property type must be a supported SQLite type, transformed type, or @Table relationship: ${source.diagnosticPath}; ${source.roundElement.qualifiedName}",
        symbol = source.roundElement.sourceDeclaration
      )
      return null
    }
    if (targetTypeKey in resolvingIds) {
      reporter.error(
        message = "Recursive relationship cycle: ${source.diagnosticPath}",
        symbol = source.roundElement.sourceDeclaration
      )
      return null
    }
    if (targetSeed.idSeed == null) {
      reporter.error(
        message = "Relationship target must declare an explicit @Id: ${source.diagnosticPath}; ${targetSeed.classDeclaration.displayName()}",
        symbol = source.roundElement.sourceDeclaration
      )
      return null
    }
    val targetId = resolveIdColumn(
      tableSeed = targetSeed,
      resolvingIds = resolvingIds + targetTypeKey
    ) ?: return null
    return createRelationshipElement(
      source = source,
      targetType = targetSeed.parsedType,
      targetTableName = targetSeed.tableName,
      targetConstruction = targetSeed.construction,
      targetId = targetId
    )
  }

  private fun createRelationshipElement(
    source: ColumnSeed,
    targetType: ParsedType,
    targetTableName: String,
    targetConstruction: ModelConstruction,
    targetId: ColumnElement
  ): RelationshipElement? {
    val canConstructWithOnlyId = targetConstruction.canConstructWithOnly(targetId.access.path)
    if (!source.isHandledRecursively && !canConstructWithOnlyId) {
      val targetTypeName = targetType.qualifiedName.substringAfterLast('.')
      reporter.error(
        message = "A non-recursive relationship target must be constructible from only its @Id: ${source.diagnosticPath}; $targetTypeName",
        symbol = source.roundElement.sourceDeclaration
      )
      return null
    }
    return RelationshipElement(
      referencedTableType = targetType,
      referencedTableName = targetTableName,
      referencedIdProperty = targetId.access.path,
      referencedIdColumnName = targetId.columnName,
      referencedIdType = targetId.deserializedType,
      referencedIdSerializedType = targetId.serializedType,
      referencedIdTransformer = targetId.transformer,
      isHandledRecursively = source.isHandledRecursively,
      onDeleteCascade = source.onDeleteCascade,
      canConstructWithOnlyId = canConstructWithOnlyId
    )
  }

  private fun validateTableColumns(
    table: TableElement,
    seed: TableSeed
  ) {
    if (table.allColumns.isEmpty()) {
      reporter.error(
        message = "Table must define at least one persisted column: ${seed.classDeclaration.displayName()}",
        symbol = seed.classDeclaration
      )
      return
    }
    val duplicateColumn = table.allColumns
      .groupBy(ColumnElement::columnName)
      .values
      .firstOrNull { it.size > 1 }
    if (duplicateColumn != null) {
      val modelName = seed.classDeclaration.displayName()
      val duplicateColumnsDescription = duplicateColumn.joinToString {
        "$modelName.${it.access.path.displayName}"
      }
      reporter.error(
        message = "Duplicate column name '${duplicateColumn.first().columnName}': $duplicateColumnsDescription",
        symbol = seed.classDeclaration
      )
    }
    if (WITHOUT_ROWID in table.options) {
      val id = table.idColumn
      if (id == null || id.isSchemaNullable) {
        val modelName = seed.classDeclaration.displayName()
        val idDescription = id?.let { "$modelName.${it.access.path.displayName}" } ?: modelName
        reporter.error(
          message = "WITHOUT_ROWID requires an explicit non-null @Id: $idDescription",
          symbol = seed.classDeclaration
        )
      }
    }
  }
}

private fun KSAnnotation.autoIncrementMode(): AutoIncrementMode {
  val autoIncrementArgument = getArgument(Id::autoIncrement)
  return when {
    autoIncrementArgument.origin == SYNTHETIC -> AUTOMATIC
    autoIncrementArgument.value == true -> ENABLED
    else -> DISABLED
  }
}
