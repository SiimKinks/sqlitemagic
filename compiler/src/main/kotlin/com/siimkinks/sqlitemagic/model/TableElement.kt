package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_POSITION
import com.siimkinks.sqlitemagic.GenerationNames
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import com.squareup.kotlinpoet.ClassName

internal data class TableElement(
  val parsedType: ParsedType,
  val identity: SqliteSchemaIdentity,
  val artifactStem: String,
  val declarationOrder: Int,
  val options: Set<TableOption>,
  val construction: ModelConstruction,
  val properties: List<PropertyElement>,
  val isPublic: Boolean
) : ParsedType by parsedType, SqliteSchemaProvider by identity {
  init {
    val expectedSchema = when {
      TableOption.TEMPORARY in options -> SqliteSchema.TEMPORARY
      else -> SqliteSchema.MAIN
    }
    require(identity.schema == expectedSchema) {
      "Table identity schema must agree with table options"
    }
  }

  val tableName get() = rawName
  val modelClassName = checkNotNull(typeName as? ClassName) {
    "Table type [$typeName] is not a class name"
  }
  val modelName = modelClassName.simpleName
  val generationNames = GenerationNames(artifactStem)

  val allColumns = properties.flatMap(PropertyElement::flattenedColumns)

  val partialRequirements by lazy {
    collectPartialRequirements(
      modelName = modelName,
      properties = properties,
      construction = construction,
      leaf = { (it as? ColumnPropertyElement)?.column }
    )
  }
  val partialRequiredColumns by lazy { partialRequirements.alwaysRequired() }
  val idColumn = allColumns.singleOrNull(ColumnElement::isId)
  val relationshipColumns = allColumns.filter(ColumnElement::isRelationship)
  private val eligibleUniqueColumns = allColumns.filter(ColumnElement::isEligibleEntityKey)
  val identityColumns = listOfNotNull(idColumn) + eligibleUniqueColumns.filterNot { it === idColumn }
  val recursiveRelationshipColumns = relationshipColumns.filter(ColumnElement::isHandledRecursively)
  val columnsForInsert = allColumns.filterNot { it.id?.isAutoIncrement == true }
  val generatedIdAssignmentColumn = idColumn?.takeIf {
    it.id?.run { isAutoIncrement && canAssignGeneratedId } == true
  }

  val supportsDefaultIdentity = idColumn != null
  val supportsIdentityOperations = identityColumns.isNotEmpty()
  val requiresByColumnTerminal = supportsIdentityOperations && !supportsDefaultIdentity
  val hasRecursiveRelationships = recursiveRelationshipColumns.isNotEmpty()
  val hasCascadeDelete get() = allColumns.any { it.relationship?.onDeleteCascade == true }
  val needsGeneratedRelationshipIds = columnsForInsert.any(ColumnElement::needsGeneratedRelationshipId)

  val tablePositionPropertyName get() = "${FIELD_TABLE_POSITION}_$artifactStem"
}
