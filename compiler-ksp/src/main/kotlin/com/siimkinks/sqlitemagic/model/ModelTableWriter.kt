package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_DEEP_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_SHALLOW_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_MAPPER
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.BOOLEAN_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_MAPPER
import com.siimkinks.sqlitemagic.WriterTypes.SELECT_FROM
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY_SET
import com.siimkinks.sqlitemagic.WriterTypes.SYSTEM_RENAMED_TABLES
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
import com.squareup.kotlinpoet.KModifier.PROTECTED
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
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
          .addSuperclassConstructorParameter("%S", table.tableName)
          .addSuperclassConstructorParameter("%N", VARIABLE_ALIAS)
          .addSuperclassConstructorParameter("%L", table.allColumns.size)
          .apply {
            if (!table.isPublic) {
              addModifiers(INTERNAL)
            }
            table.allColumns.forEach { column ->
              addProperty(
                columnProperty(
                  table = table,
                  column = column
                )
              )
            }
            if (table.hasRecursiveRelationships) {
              addFunction(queryPartsFunction(table))
            }
            if (table.needsShallowQueryParts) {
              addFunction(
                queryPartsFunction(
                  table = table,
                  shallow = true
                )
              )
            }
          }
          .addFunction(aliasFunction(table))
          .addFunction(mapperFunction(table))
          .addType(companionObject(table))
          .build()
      )
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
        column.transformer?.isDefaultTransformer == true -> CodeBlock.of(
          "%T(this, %S, %T.%N, %L, null)",
          columnClass(column),
          column.columnName,
          UTILS,
          parserName(column),
          column.isSchemaNullable
        )
        else -> CodeBlock.of(
          "%T(this, %S, false, %T.%N, %L, null)",
          columnClass(column),
          column.columnName,
          UTILS,
          parserName(column),
          column.isSchemaNullable
        )
      }
    )
    .build()

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
    val equivalentType = when {
      column.sqlStorageType.isNumeric -> NUMBER
      else -> valueType
    }
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
    val prefix = when {
      column.isUnique || column.isId -> "Unique"
      else -> ""
    }
    return ClassName(PACKAGE_ROOT, "$prefix${transformer.transformerName}Column")
  }

  private fun parserName(column: ColumnElement) = when (column.sqlStorageType) {
    SqlStorageType.BYTE_ARRAY -> "UNBOXED_BYTE_ARRAY_PARSER"
    SqlStorageType.BOXED_BYTE_ARRAY -> "BOXED_BYTE_ARRAY_PARSER"
    SqlStorageType.BYTE -> when {
      column.isSchemaNullable -> "NULLABLE_BYTE_PARSER"
      else -> "BYTE_PARSER"
    }
    SqlStorageType.DOUBLE -> "DOUBLE_PARSER"
    SqlStorageType.FLOAT -> "FLOAT_PARSER"
    SqlStorageType.INT -> when {
      column.isSchemaNullable -> "NULLABLE_INTEGER_PARSER"
      else -> "INTEGER_PARSER"
    }
    SqlStorageType.LONG -> when {
      column.isSchemaNullable -> "NULLABLE_LONG_PARSER"
      else -> "LONG_PARSER"
    }
    SqlStorageType.SHORT -> when {
      column.isSchemaNullable -> "NULLABLE_SHORT_PARSER"
      else -> "SHORT_PARSER"
    }
    SqlStorageType.STRING -> "STRING_PARSER"
  }

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
      .builder(METHOD_MAPPER)
      .addModifiers(PROTECTED, OVERRIDE)
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

  private fun queryPartsFunction(
    table: TableElement,
    shallow: Boolean = false
  ) = FunSpec
    .builder(
      when {
        shallow -> METHOD_ADD_SHALLOW_QUERY_PARTS
        else -> METHOD_ADD_DEEP_QUERY_PARTS
      }
    )
    .addModifiers(OVERRIDE)
    .addParameter(name = "from", type = SELECT_FROM)
    .addParameter(name = "selectFromTables", type = STRING_ARRAY_SET.copy(nullable = true))
    .addParameter(
      name = "tableGraphNodeNames",
      type = SIMPLE_ARRAY_MAP
        .parameterizedBy(STRING, STRING)
        .copy(nullable = true)
    )
    .addParameter(name = "select1", type = BOOLEAN)
    .returns(SYSTEM_RENAMED_TABLES.copy(nullable = true))
    .addStatement(
      "return %T.%N(from, selectFromTables, tableGraphNodeNames, select1)",
      table.generationNames.handlerClassName,
      when {
        shallow -> METHOD_ADD_SHALLOW_QUERY_PARTS
        else -> METHOD_ADD_DEEP_QUERY_PARTS
      }
    )
    .build()

  private fun companionObject(table: TableElement): TypeSpec {
    val tableClassName = table.generationNames.tableClassName
    return TypeSpec
      .companionObjectBuilder()
      .addProperty(
        PropertySpec
          .builder(name = table.structureFieldName, type = tableClassName)
          .addAnnotation(JvmField::class)
          .initializer("%T(null)", tableClassName)
          .build()
      )
      .build()
  }

}
