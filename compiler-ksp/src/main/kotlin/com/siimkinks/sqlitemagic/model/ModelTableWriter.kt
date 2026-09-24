package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_DEEP_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_SHALLOW_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_MAPPER
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_MAPPER
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.WriterTypes.BOOLEAN_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_GRAPH_SCOPE
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_MAPPER
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.UTILS
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal class ModelTableWriter(
  private val environment: Environment
) : ModelWriter {
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    val tableType = table.generationNames.tableClassName
    FileSpec
      .builder(tableType)
      .addType(
        TypeSpec
          .classBuilder(tableType)
          .primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addModifiers(PRIVATE)
              .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
              .build()
          )
          .superclass(TABLE.parameterizedBy(table.modelClassName))
          .addSuperclassConstructorParameter("name = %S", table.tableName)
          .addSuperclassConstructorParameter("alias = %N", VARIABLE_ALIAS)
          .addSuperclassConstructorParameter("nrOfColumns = %L", table.allColumns.size)
          .addSuperclassConstructorParameter("%N = ::%N", METHOD_MAPPER, METHOD_CREATE_MAPPER)
          .apply {
            if (!table.isPublic) {
              addModifiers(INTERNAL)
            }
            if (table.hasRecursiveRelationships) {
              addSuperclassConstructorParameter(
                "%N = %T::%N",
                METHOD_ADD_DEEP_QUERY_PARTS,
                QUERY_GRAPH_SCOPE,
                METHOD_ADD_DEEP_QUERY_PARTS
              )
            }
            if (table.needsShallowQueryParts) {
              addSuperclassConstructorParameter(
                "%N = %T::%N",
                METHOD_ADD_SHALLOW_QUERY_PARTS,
                QUERY_GRAPH_SCOPE,
                METHOD_ADD_SHALLOW_QUERY_PARTS
              )
            }
            table.allColumns.forEach { column ->
              addProperty(
                columnProperty(
                  table = table,
                  column = column
                )
              )
            }
          }
          .addFunction(aliasFunction(table))
          .addType(companionObject(table))
          .build()
      )
      .addFunction(mapperFunction(table))
      .apply {
        if (table.hasRecursiveRelationships) {
          addFunction(queryGraphContributor(table = table))
        }
        if (table.needsShallowQueryParts) {
          addFunction(
            queryGraphContributor(
              table = table,
              shallow = true
            )
          )
        }
      }
      .build()
      .writeModelSource(
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
    if (column.hasGeneratedColumnClass) {
      return generatedColumnClass(table = table, column = column)
        .parameterizedBy(table.modelClassName, nullability)
    }
    if (transformer?.isDefaultTransformer == true) {
      return BOOLEAN_COLUMN
        .parameterizedBy(table.modelClassName, nullability)
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

  private fun aliasFunction(table: TableElement): FunSpec {
    val tableClassName = table.generationNames.tableClassName
    return FunSpec
      .builder(METHOD_AS)
      .addModifiers(OVERRIDE)
      .addParameter(name = VARIABLE_ALIAS, type = STRING)
      .returns(tableClassName)
      .addStatement("return %T(%N)", tableClassName, VARIABLE_ALIAS)
      .build()
  }

  private fun mapperFunction(table: TableElement): FunSpec {
    val columnPositions = ParameterSpec
      .builder(
        name = "columnPositions",
        type = SIMPLE_ARRAY_MAP
          .parameterizedBy(STRING, INT)
          .copy(nullable = true)
      )
      .build()
    val tableGraphNodeNames = ParameterSpec
      .builder(
        name = "tableGraphNodeNames",
        type = SIMPLE_ARRAY_MAP
          .parameterizedBy(STRING, STRING)
          .copy(nullable = true)
      )
      .build()
    val queryDeep = ParameterSpec
      .builder(name = "queryDeep", type = BOOLEAN)
      .build()
    val mapperType = QUERY_MAPPER.parameterizedBy(table.modelClassName)
    val daoClassName = table.generationNames.daoClassName
    val function = FunSpec
      .builder(METHOD_CREATE_MAPPER)
      .addModifiers(PRIVATE)
      .addParameter(columnPositions)
      .addParameter(tableGraphNodeNames)
      .addParameter(queryDeep)
      .returns(mapperType)
      .beginControlFlow("return when")
    when {
      table.hasRecursiveRelationships -> function
        .beginControlFlow("%N == null || %N.isEmpty() -> when", columnPositions, columnPositions)
        .addStatement("%N -> %T(%T::%N)", queryDeep, mapperType, daoClassName, METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .addStatement("else -> %T(%T::%N)", mapperType, daoClassName, METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .endControlFlow()
        .beginControlFlow("%N -> %T", queryDeep, mapperType)
        .addStatement(
          "checkNotNull(%T.%N(it, %N, %N, %S))",
          daoClassName,
          METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
          columnPositions,
          tableGraphNodeNames,
          ""
        )
        .endControlFlow()
        .beginControlFlow("else -> %T", mapperType)
        .addStatement(
          "checkNotNull(%T.%N(it, %N, %N, %S))",
          daoClassName,
          METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
          columnPositions,
          tableGraphNodeNames,
          ""
        )
        .endControlFlow()
      else -> function
        .addStatement(
          "%N == null || %N.isEmpty() -> %T(%T::%N)",
          columnPositions,
          columnPositions,
          mapperType,
          daoClassName,
          METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
        )
        .beginControlFlow("else -> %T", mapperType)
        .addStatement(
          "checkNotNull(%T.%N(it, %N, %N, %S))",
          daoClassName,
          METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
          columnPositions,
          tableGraphNodeNames,
          ""
        )
        .endControlFlow()
    }
    return function
      .endControlFlow()
      .build()
  }

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

  private fun companionObject(table: TableElement): TypeSpec {
    val tableClassName = table.generationNames.tableClassName
    return TypeSpec
      .companionObjectBuilder()
      .addProperty(
        PropertySpec
          .builder(name = table.structureFieldName, type = tableClassName)
          .initializer("%T(null)", tableClassName)
          .build()
      )
      .build()
  }
}
