package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.NameConst.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.ParsedType
import com.squareup.kotlinpoet.ClassName

data class ModelGenerationNames(
  val packageName: String,
  val artifactStem: String
) {
  val daoClassName = ClassName(packageName, "SqliteMagic_${artifactStem}_Dao")
  val handlerClassName = ClassName(PACKAGE_ROOT, "SqliteMagic_${artifactStem}_Handler")
  val tableClassName = ClassName(PACKAGE_ROOT, "${artifactStem}Table")
  val extensionsFileName = "_$artifactStem"
  val operationsObjectName = "${artifactStem}s"
}

data class TableElement(
  val parsedType: ParsedType,
  val tableName: String,
  val artifactStem: String,
  val declarationOrder: Int,
  val options: Set<TableOption>,
  val construction: ModelConstruction,
  val properties: List<PropertyElement>
) : ParsedType by parsedType {
  val modelClassName = checkNotNull(typeName as? ClassName) {
    "Table type [$typeName] is not a class name"
  }
  val modelName get() = modelClassName.simpleName
  val packageName get() = modelClassName.packageName
  val allColumns = properties.flatMap(PropertyElement::flattenedColumns)
  val generationNames = ModelGenerationNames(
    packageName = packageName,
    artifactStem = artifactStem
  )
  val idColumn get() = allColumns.singleOrNull(ColumnElement::isId)
  val columnsExceptId get() = allColumns.filterNot(ColumnElement::isId)
  val eligibleUniqueColumns get() = allColumns.filter(ColumnElement::isEligibleEntityKey)
  val relationshipColumns get() = allColumns.filter(ColumnElement::isRelationship)
}

private fun PropertyElement.flattenedColumns(): List<ColumnElement> = when (this) {
  is ColumnPropertyElement -> listOf(column)
  is EmbeddedPropertyElement -> properties.flatMap(PropertyElement::flattenedColumns)
}
