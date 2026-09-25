package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_DEEP_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_SHALLOW_QUERY_PARTS
import com.siimkinks.sqlitemagic.WriterTypes.BOOLEAN_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_GRAPH_SCOPE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.UTILS
import com.siimkinks.sqlitemagic.writer.ReadTableStructureWriter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

internal class ModelTableWriter(
  private val environment: Environment
) : ModelWriter {
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    ReadTableStructureWriter
      .from(
        table = table,
        columns = table.allColumns.map { column ->
          columnProperty(
            table = table,
            column = column
          )
        },
        otherFunctions = buildList {
          if (table.hasRecursiveRelationships) {
            add(queryGraphContributor(table = table))
          }
          if (table.needsShallowQueryParts) {
            add(queryGraphContributor(table = table, shallow = true))
          }
        }
      )
      .write(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }

  private fun columnProperty(
    table: TableElement,
    column: ColumnElement
  ): PropertySpec = PropertySpec
    .builder(
      name = column.fieldName,
      type = columnType(table = table, column = column)
    )
    .initializer(
      when {
        column.hasGeneratedColumnClass -> CodeBlock.of(
          "%T(this, %S, %T.%N, %L, null)",
          generatedColumnClass(table = table, column = column),
          column.columnName,
          UTILS,
          parserName(column),
          column.isSchemaNullable
        )
        else -> CodeBlock.of(
          "%T(table = this, name = %S, valueParser = %T.%N, nullable = %L, alias = null)",
          factoryColumnClass(column),
          column.columnName,
          UTILS,
          parserName(column),
          column.isSchemaNullable
        )
      }
    )
    .build()

  private fun factoryColumnClass(column: ColumnElement) = when {
    column.transformer?.isDefaultTransformer == true -> BOOLEAN_COLUMN
    column.relationship != null && column.sqlStorageType.isNumeric -> COMPLEX_NUMERIC_COLUMN
    column.relationship != null -> COMPLEX_COLUMN
    column.isUnique || column.isId -> when {
      column.sqlStorageType.isNumeric -> UNIQUE_NUMERIC_COLUMN
      else -> UNIQUE_COLUMN
    }
    column.sqlStorageType.isNumeric -> NUMERIC_COLUMN
    else -> COLUMN
  }

  private fun columnType(
    table: TableElement,
    column: ColumnElement
  ): TypeName {
    val nullability = when {
      column.isSchemaNullable -> NULLABLE
      else -> NOT_NULLABLE
    }
    val transformer = column.transformer
    when {
      column.hasGeneratedColumnClass -> return generatedColumnClass(table, column)
        .parameterizedBy(table.modelClassName, nullability)
      transformer?.isDefaultTransformer == true -> return BOOLEAN_COLUMN.parameterizedBy(
        table.modelClassName,
        nullability
      )
    }
    val valueType = when {
      column.relationship != null -> column.relationship.referencedIdType.typeName
      else -> column.deserializedType.typeName
    }.copy(nullable = false)
    val returnType = valueType.copy(
      nullable = column.isSchemaNullable
    )
    val equivalentType = column.equivalentType(declaredType = valueType)
    return columnClass(column).parameterizedBy(
      valueType,
      returnType,
      equivalentType,
      table.modelClassName,
      nullability
    )
  }

  private fun columnClass(column: ColumnElement) = when {
    column.relationship != null && column.hasGeneratedColumnClass -> error(
      "Transformed relationship columns use a generated column class"
    )
    column.transformer != null && !column.transformer.isDefaultTransformer -> generatedTransformerColumnClass(column)
    column.transformer?.isDefaultTransformer == true -> BOOLEAN_COLUMN
    column.relationship != null && column.sqlStorageType.isNumeric -> COMPLEX_NUMERIC_COLUMN
    column.relationship != null -> COMPLEX_COLUMN
    column.isUnique || column.isId -> when {
      column.sqlStorageType.isNumeric -> UNIQUE_NUMERIC_COLUMN
      else -> UNIQUE_COLUMN
    }
    column.sqlStorageType.isNumeric -> NUMERIC_COLUMN
    else -> COLUMN
  }

  private fun generatedColumnClass(
    table: TableElement,
    column: ColumnElement
  ) = when {
    column.relationship != null -> table.relationshipColumnClassName(column)
    else -> generatedTransformerColumnClass(column)
  }

  private fun generatedTransformerColumnClass(column: ColumnElement): ClassName {
    val transformer = checkNotNull(column.transformer)
    return transformer.generatedColumnClassName(unique = column.isUnique || column.isId)
  }

  private fun parserName(column: ColumnElement) = column
    .sqlStorageType
    .parserName(column.isSchemaNullable)

  private fun queryGraphContributor(
    table: TableElement,
    shallow: Boolean = false
  ): FunSpec {
    val function = FunSpec
      .builder(if (shallow) METHOD_ADD_SHALLOW_QUERY_PARTS else METHOD_ADD_DEEP_QUERY_PARTS)
      .receiver(QUERY_GRAPH_SCOPE)
      .addModifiers(PRIVATE)
      .addParameter(name = "table", type = TABLE.parameterizedBy(STAR))
      .addParameter(name = "tableAlias", type = TABLE.parameterizedBy(STAR))
      .addParameter(name = "nodeName", type = STRING)
      .addStatement("val sourceTable = table as %T", table.generationNames.tableClassName)
    table.recursiveRelationshipColumns
      .filter { !shallow || it.relationship?.canConstructWithOnlyId == false }
      .forEachIndexed { index, column ->
        val referencedTable = checkNotNull(
          environment.tableElements[column.relationship?.referencedTableTypeKey]
        )
        val referencedTableClassName = referencedTable.generationNames.tableClassName
        val referencedId = checkNotNull(referencedTable.idColumn)
        val referencedTableName = "referencedTable$index"
        val joinedTableName = "joinedTable$index"
        val relationshipNodeName = "relationshipNodeName$index"
        val parentColumnName = "parentColumn$index"
        val referencedIdName = "referencedId$index"
        function
          .addStatement(
            "val %N = %T.%N",
            referencedTableName,
            referencedTableClassName,
            referencedTable.structureFieldName
          )
          .beginControlFlow("if (includes(%S))", referencedTable.tableName)
          .addStatement("val %N = nodeName + %S", relationshipNodeName, column.columnName)
          .addStatement(
            "val %N = rebindColumn(newTable = tableAlias, column = sourceTable.%N)",
            parentColumnName,
            column.fieldName
          )
          .addStatement(
            "val userJoin = findJoin(table = %N, joinedOnColumn = %N)",
            referencedTableName,
            parentColumnName
          )
          .beginControlFlow("if (userJoin != null)")
          .addStatement(
            "visit(table = %N, tableAlias = userJoin, nodeName = %N)",
            referencedTableName,
            relationshipNodeName
          )
          .nextControlFlow("else")
          .addStatement(
            "val %N = tableForAutomaticJoin(%N)",
            joinedTableName,
            referencedTableName
          )
          .addStatement("recordAutomaticTableOccurrence(%N)", joinedTableName)
          .addStatement(
            "val %N = rebindColumn(newTable = %N, column = %T.%N.%N)",
            referencedIdName,
            joinedTableName,
            referencedTableClassName,
            referencedTable.structureFieldName,
            referencedId.fieldName
          )
          .addStatement(
            "addLeftJoin(table = %N, on = %N.%N(%N))",
            joinedTableName,
            parentColumnName,
            "is",
            referencedIdName
          )
          .addStatement(
            "visit(table = %N, tableAlias = %N, nodeName = %N)",
            referencedTableName,
            joinedTableName,
            relationshipNodeName
          )
          .endControlFlow()
        when {
          column.isModelPathNullable -> function.endControlFlow()
          else -> function
            .nextControlFlow("else if (!select1)")
            .addStatement(
              "throw %T(%S)",
              SQL_EXCEPTION,
              "Column ${column.columnName} is not nullable and was not part of selected columns"
            )
            .endControlFlow()
        }
      }
    return function.build()
  }

}
