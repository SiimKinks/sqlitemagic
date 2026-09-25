package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_VIEW_QUERY
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_DEEP_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ADD_SHALLOW_QUERY_PARTS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_MAPPER
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_FULL_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_MAPPER
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.WriterTypes.CHECK_NOT_NULL
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_GRAPH_SCOPE
import com.siimkinks.sqlitemagic.WriterTypes.QUERY_MAPPER
import com.siimkinks.sqlitemagic.WriterTypes.SIMPLE_ARRAY_MAP
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.writeModelSource
import com.siimkinks.sqlitemagic.view.ViewElement
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
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.withIndent

/** Common query-facing structure for a persisted table or a view. */
internal class ReadTableStructureWriter private constructor(
  private val className: ClassName,
  private val modelClassName: ClassName,
  private val structureFieldName: String,
  private val isPublic: Boolean,
  private val constructorArguments: List<CodeBlock>,
  private val columns: List<PropertySpec>,
  private val daoClassName: ClassName,
  private val hasFullReaderBranch: Boolean,
  private val selectedNodeNameParameter: ParameterSpec?,
  private val otherFunctions: List<FunSpec> = emptyList()
) {
  fun write(
    codeGenerator: CodeGenerator,
    originatingFiles: OriginatingFiles
  ) = FileSpec
    .builder(className)
    .addType(
      TypeSpec
        .classBuilder(className)
        .primaryConstructor(
          FunSpec
            .constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
            .build()
        )
        .superclass(TABLE.parameterizedBy(modelClassName))
        .apply {
          if (!isPublic) {
            addModifiers(INTERNAL)
          }
          constructorArguments.forEach {
            addSuperclassConstructorParameter("%L", it)
          }
        }
        .addProperties(columns)
        .addFunction(
          FunSpec
            .builder(METHOD_AS)
            .addModifiers(OVERRIDE)
            .addParameter(name = VARIABLE_ALIAS, type = STRING)
            .returns(className)
            .addStatement("return %T(%N)", className, VARIABLE_ALIAS)
            .build()
        )
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addProperty(
              PropertySpec
                .builder(name = structureFieldName, type = className)
                .initializer("%T(null)", className)
                .build()
            )
            .build()
        )
        .build()
    )
    .addFunction(mapperFunction())
    .addFunctions(otherFunctions)
    .build()
    .writeModelSource(
      codeGenerator = codeGenerator,
      originatingFiles = originatingFiles
    )

  private fun mapperFunction(): FunSpec {
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
    val mapperType = QUERY_MAPPER.parameterizedBy(modelClassName)
    val function = FunSpec
      .builder(METHOD_CREATE_MAPPER)
      .addModifiers(PRIVATE)
      .addParameter(columnPositions)
      .addParameter(tableGraphNodeNames)
      .addParameter(queryDeep)
      .apply {
        selectedNodeNameParameter?.let(::addParameter)
      }
      .returns(mapperType)
      .beginControlFlow("return when")
    when {
      hasFullReaderBranch -> function
        .beginControlFlow("%N == null || %N.isEmpty() -> when", columnPositions, columnPositions)
        .addStatement("%N -> %T(%T::%N)", queryDeep, mapperType, daoClassName, METHOD_FULL_OBJECT_FROM_CURSOR_POSITION)
        .addStatement("else -> %T(%T::%N)", mapperType, daoClassName, METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION)
        .endControlFlow()
        .beginControlFlow("%N -> %T", queryDeep, mapperType)
        .addSelectedReaderStatement(
          readerName = METHOD_FULL_OBJECT_FROM_CURSOR_POSITION,
          columnPositions = columnPositions,
          tableGraphNodeNames = tableGraphNodeNames
        )
        .endControlFlow()
      else -> function.addStatement(
        "%N == null || %N.isEmpty() -> %T(%T::%N)",
        columnPositions,
        columnPositions,
        mapperType,
        daoClassName,
        METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION
      )
    }
    return function
      .beginControlFlow("else -> %T", mapperType)
      .addSelectedReaderStatement(
        readerName = METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION,
        columnPositions = columnPositions,
        tableGraphNodeNames = tableGraphNodeNames
      )
      .endControlFlow()
      .endControlFlow()
      .build()
  }

  private fun FunSpec.Builder.addSelectedReaderStatement(
    readerName: String,
    columnPositions: ParameterSpec,
    tableGraphNodeNames: ParameterSpec
  ): FunSpec.Builder {
    val nodeName = selectedNodeNameParameter
      ?.let { CodeBlock.of("%N", it) }
      ?: CodeBlock.of("%S", "")
    return addStatement(
      "%M(%T.%N(it, %N, %N, %L))",
      CHECK_NOT_NULL,
      daoClassName,
      readerName,
      columnPositions,
      tableGraphNodeNames,
      nodeName
    )
  }

  companion object {
    fun from(
      table: TableElement,
      columns: List<PropertySpec>,
      otherFunctions: List<FunSpec>
    ) = ReadTableStructureWriter(
      className = table.generationNames.tableClassName,
      modelClassName = table.modelClassName,
      structureFieldName = table.structureFieldName,
      isPublic = table.isPublic,
      constructorArguments = buildList {
        add(CodeBlock.of("name = %S", table.tableName))
        add(CodeBlock.of("alias = %N", VARIABLE_ALIAS))
        add(CodeBlock.of("nrOfColumns = %L", table.allColumns.size))
        add(CodeBlock.of("%N = ::%N", METHOD_MAPPER, METHOD_CREATE_MAPPER))
        if (table.hasRecursiveRelationships) {
          add(CodeBlock.of("%N = %T::%N", METHOD_ADD_DEEP_QUERY_PARTS, QUERY_GRAPH_SCOPE, METHOD_ADD_DEEP_QUERY_PARTS))
        }
        if (table.needsShallowQueryParts) {
          add(
            CodeBlock.of(
              "%N = %T::%N",
              METHOD_ADD_SHALLOW_QUERY_PARTS,
              QUERY_GRAPH_SCOPE,
              METHOD_ADD_SHALLOW_QUERY_PARTS
            )
          )
        }
      },
      columns = columns,
      daoClassName = table.generationNames.daoClassName,
      hasFullReaderBranch = table.hasRecursiveRelationships,
      selectedNodeNameParameter = null,
      otherFunctions = otherFunctions
    )

    fun from(
      view: ViewElement,
      columns: List<PropertySpec>
    ): ReadTableStructureWriter {
      val viewIdentifier = ParameterSpec
        .builder(name = "viewIdentifier", type = STRING)
        .build()
      return ReadTableStructureWriter(
        className = view.generationNames.tableClassName,
        modelClassName = view.modelClassName,
        structureFieldName = view.structureFieldName,
        isPublic = view.isPublic,
        constructorArguments = listOf(
          CodeBlock.of("name = %S", view.viewName),
          CodeBlock.of("alias = %N", VARIABLE_ALIAS),
          CodeBlock.of("nrOfColumns = %L", view.expandedWidth),
          buildCodeBlock {
            add("%N = { %N, %N, %N ->\n", METHOD_MAPPER, "columnPositions", "tableGraphNodeNames", "queryDeep")
            withIndent {
              add("%N(\n", METHOD_CREATE_MAPPER)
              withIndent {
                add("%N = %N,\n", "columnPositions", "columnPositions")
                add("%N = %N,\n", "tableGraphNodeNames", "tableGraphNodeNames")
                add("%N = %N,\n", "queryDeep", "queryDeep")
                add("%N = %N ?: %S\n", viewIdentifier, VARIABLE_ALIAS, view.viewName)
              }
              add(")\n")
            }
            add("}")
          },
          CodeBlock.of(
            "viewDefinition = { %T.%N }",
            view.generationNames.daoClassName,
            FIELD_VIEW_QUERY
          )
        ),
        columns = columns,
        daoClassName = view.generationNames.daoClassName,
        hasFullReaderBranch = true,
        selectedNodeNameParameter = viewIdentifier
      )
    }
  }
}
