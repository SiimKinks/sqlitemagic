package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR

data class ModelGenerationNames(
  val packageName: String,
  val artifactStem: String
) {
  val daoClassName = ClassName(packageName, "SqliteMagic_${artifactStem}_Dao")
  val adapterClassName = ClassName(PACKAGE_ROOT, "SqliteMagic_${artifactStem}_Adapter")
  val tableClassName = ClassName(PACKAGE_ROOT, "${artifactStem}Table")
  val extensionsFileName = "_$artifactStem"
  val bulkOperationsObjectName = "${artifactStem}s"
}

data class TableElement(
  val parsedType: ParsedType,
  val tableName: String,
  val artifactStem: String,
  val declarationOrder: Int,
  val options: Set<TableOption>,
  val construction: ModelConstruction,
  val properties: List<PropertyElement>,
  val isPublic: Boolean = true
) : ParsedType by parsedType {
  val modelClassName = checkNotNull(typeName as? ClassName) {
    "Table type [$typeName] is not a class name"
  }
  val modelName = modelClassName.simpleName
  val packageName = modelClassName.packageName
  val structureFieldName = artifactStem.camelCaseToSnakeCase().uppercase()
  val generationNames = ModelGenerationNames(
    packageName = packageName,
    artifactStem = artifactStem
  )

  val allColumns = properties.flatMap(PropertyElement::flattenedColumns)
  val idColumn = allColumns.singleOrNull(ColumnElement::isId)
  val relationshipColumns = allColumns.filter(ColumnElement::isRelationship)
  val eligibleUniqueColumns = allColumns.filter(ColumnElement::isEligibleEntityKey)
  val identityColumns = listOfNotNull(idColumn) + eligibleUniqueColumns.filterNot { it === idColumn }
  val recursiveRelationshipColumns = relationshipColumns.filter(ColumnElement::isHandledRecursively)
  val columnsForInsert = allColumns.filterNot { it.id?.isAutoIncrement == true }
  val generatedIdAssignmentColumn = idColumn?.takeIf { column ->
    column.id?.run { isAutoIncrement && canAssignGeneratedId } == true
  }

  val supportsDefaultIdentity = idColumn != null
  val supportsIdentityOperations = identityColumns.isNotEmpty()
  val requiresByColumnTerminal = supportsIdentityOperations && !supportsDefaultIdentity
  val needsShallowQueryParts = recursiveRelationshipColumns.any { column ->
    column.relationship?.canConstructWithOnlyId == false
  }
  val hasRecursiveRelationships = recursiveRelationshipColumns.isNotEmpty()

  val byColumnType = COLUMN.parameterizedBy(STAR, STAR, STAR, modelClassName, NOT_NULLABLE)

  fun relationshipColumnClassName(column: ColumnElement) = ClassName(
    PACKAGE_ROOT,
    "SqliteMagic_${artifactStem}_${column.relationshipColumnTypeSegment}Column"
  )

  fun needsGeneratedRelationshipIds(environment: Environment) =
    columnsForInsert.any {
      it.needsGeneratedRelationshipId(environment)
    }
}
