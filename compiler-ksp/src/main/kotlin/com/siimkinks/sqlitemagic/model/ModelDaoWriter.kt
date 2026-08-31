package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_NOT_NULL_FOR_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_NOT_NULL_FOR_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_TO_INSERT_STATEMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_TO_UPDATE_STATEMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BY_COLUMN
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_GENERATED_RELATIONSHIP_IDS
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.BIND_VALUES_MAP
import com.siimkinks.sqlitemagic.WriterTypes.OPERATION_FAILED_EXCEPTION
import com.siimkinks.sqlitemagic.WriterTypes.SUPPORT_SQLITE_STATEMENT
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.MUTABLE_PROPERTIES
import com.siimkinks.sqlitemagic.model.ModelConstructionStrategy.PRIMARY_CONSTRUCTOR
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec

internal class ModelDaoWriter(
  private val environment: Environment
) : ModelWriter {
  private val cursorWriter = ModelDaoCursorWriter(environment)

  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    val needsGeneratedRelationshipIds = table.needsGeneratedRelationshipIds(environment)
    val dao = TypeSpec
      .objectBuilder(table.generationNames.daoClassName)
      .addModifiers(INTERNAL)
      .addSuperinterface(table.statementBinderType())
      .addFunction(
        bindToInsertStatement(
          table = table,
          columns = table.columnsForInsert,
          usesGeneratedRelationshipIds = needsGeneratedRelationshipIds
        )
      )
      .apply {
        table.idColumn?.let { idColumn ->
          if (table.construction.canConstructWithOnly(idColumn.access.path)) {
            addFunction(
              idOnlyConstructor(
                table = table,
                idColumn = idColumn
              )
            )
          }
        }
        if (table.supportsIdentityOperations) {
          addFunction(
            notNullValues(
              functionName = METHOD_BIND_NOT_NULL_FOR_INSERT,
              table = table,
              columns = table.columnsForInsert,
              excludesIdentityColumn = false,
              usesGeneratedRelationshipIds = needsGeneratedRelationshipIds
            )
          )
            .addFunction(bindToUpdateStatement(table))
            .addFunction(
              notNullValues(
                functionName = METHOD_BIND_NOT_NULL_FOR_UPDATE,
                table = table,
                columns = table.allColumns,
                excludesIdentityColumn = true
              )
            )
        }
        cursorWriter.write(
          table = table,
          daoBuilder = this
        )
      }
    FileSpec
      .builder(
        packageName = table.packageName,
        fileName = table.generationNames.daoClassName.simpleName
      )
      .addType(dao.build())
      .build()
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }

  private fun idOnlyConstructor(
    table: TableElement,
    idColumn: ColumnElement
  ): FunSpec {
    val idType = idColumn.deserializedType.typeName.copy(nullable = idColumn.isNullable)
    val construction = when (table.construction.strategy) {
      PRIMARY_CONSTRUCTOR -> CodeBlock.of(
        "%T(%N = id)",
        table.modelClassName,
        idColumn.access.path.propertyName
      )
      MUTABLE_PROPERTIES -> CodeBlock.of(
        "%T().apply { this.%N = id }",
        table.modelClassName,
        idColumn.access.path.propertyName
      )
    }
    return FunSpec
      .builder(METHOD_NEW_INSTANCE_WITH_ONLY_ID)
      .addParameter(name = "id", type = idType)
      .returns(table.modelClassName)
      .addStatement("return %L", construction)
      .build()
  }

  private fun bindToInsertStatement(
    table: TableElement,
    columns: List<ColumnElement>,
    usesGeneratedRelationshipIds: Boolean = false
  ) = FunSpec
    .builder(METHOD_BIND_TO_INSERT_STATEMENT)
    .addModifiers(OVERRIDE)
    .addParameter(name = "statement", type = SUPPORT_SQLITE_STATEMENT)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_GENERATED_RELATIONSHIP_IDS, type = MAP.parameterizedBy(STRING, LONG))
    .addStatement("statement.clearBindings()")
    .addBindings(
      table = table,
      columns = columns,
      usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
    )
    .build()

  private fun bindToUpdateStatement(table: TableElement) = FunSpec
    .builder(METHOD_BIND_TO_UPDATE_STATEMENT)
    .addModifiers(OVERRIDE)
    .addParameter(name = "statement", type = SUPPORT_SQLITE_STATEMENT)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = METHOD_BY_COLUMN, type = table.byColumnType)
    .addStatement("statement.clearBindings()")
    .beginControlFlow("when (%N)", METHOD_BY_COLUMN)
    .apply {
      table.identityColumns.forEach { identityColumn ->
        beginControlFlow(
          "%T.%N.%N ->",
          table.generationNames.tableClassName,
          table.structureFieldName,
          identityColumn.fieldName
        )
          .addBindings(
            table = table,
            columns = table
              .allColumns
              .filterNot { column ->
                column === identityColumn || column.isId
              } + identityColumn
          )
          .endControlFlow()
      }
    }
    .addStatement(
      "else -> throw %T(%S)",
      IllegalArgumentException::class,
      "Column does not identify an entity property"
    )
    .endControlFlow()
    .build()

  private fun notNullValues(
    functionName: String,
    table: TableElement,
    columns: List<ColumnElement>,
    excludesIdentityColumn: Boolean,
    usesGeneratedRelationshipIds: Boolean = false
  ): FunSpec {
    val function = FunSpec
      .builder(functionName)
      .addModifiers(OVERRIDE)
      .addParameter(name = "entity", type = table.modelClassName)
      .addParameter(name = "values", type = BIND_VALUES_MAP)
      .addStatement("values.clear()")
      .apply {
        when {
          excludesIdentityColumn -> addParameter(name = METHOD_BY_COLUMN, type = table.byColumnType)
          else -> addParameter(name = VARIABLE_GENERATED_RELATIONSHIP_IDS, type = MAP.parameterizedBy(STRING, LONG))
        }
      }
    columns
      .filterNot { column -> excludesIdentityColumn && column.isId }
      .forEach { column ->
        val nullableValue = column.bindingValueCanBeNull()
        val value = serializedInsertAccessCode(
          table = table,
          column = column,
          usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
        )
        val nullableReceiver = when {
          usesGeneratedRelationshipIds &&
              column.needsGeneratedRelationshipId(environment) -> CodeBlock.of("(%L)", value)
          else -> value
        }
        val excludesSelectedIdentity = when {
          excludesIdentityColumn && table.identityColumns.any { it === column } -> CodeBlock.of(
            "byColumn != %T.%N.%N",
            table.generationNames.tableClassName,
            table.structureFieldName,
            column.fieldName
          )
          else -> null
        }
        when {
          nullableValue && excludesSelectedIdentity != null -> function
            .beginControlFlow(
              "%L?.takeIf { %L }?.let",
              nullableReceiver,
              excludesSelectedIdentity
            )
            .addStatement(
              "values.put(%S, %L)",
              column.columnName,
              column.databaseWriteValue(CodeBlock.of("it"))
            )
            .endControlFlow()
          nullableValue -> function
            .beginControlFlow("%L?.let", nullableReceiver)
            .addStatement(
              "values.put(%S, %L)",
              column.columnName,
              column.databaseWriteValue(CodeBlock.of("it"))
            )
            .endControlFlow()
          excludesSelectedIdentity != null -> function
            .beginControlFlow("if (%L)", excludesSelectedIdentity)
            .addStatement(
              "values.put(%S, %L)",
              column.columnName,
              column.databaseWriteValue(value)
            )
            .endControlFlow()
          else -> function.addStatement(
            "values.put(%S, %L)",
            column.columnName,
            column.databaseWriteValue(value)
          )
        }
      }
    return function.build()
  }

  private fun FunSpec.Builder.addBindings(
    table: TableElement,
    columns: List<ColumnElement>,
    usesGeneratedRelationshipIds: Boolean = false
  ) = apply {
    columns.forEachIndexed { index, column ->
      val parameterIndex = index + 1
      val valueName = "value$parameterIndex"
      val value = serializedInsertAccessCode(
        table = table,
        column = column,
        usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
      )
      when {
        column.bindingValueCanBeNull() ->
          addStatement("val %N = %L", valueName, value)
            .beginControlFlow("if (%N == null)", valueName)
            .addStatement("statement.bindNull(%L)", parameterIndex)
            .nextControlFlow("else")
            .addCode(
              bindCode(
                column = column,
                parameterIndex = parameterIndex,
                value = CodeBlock.of("%N", valueName)
              )
            )
            .endControlFlow()
        else -> addCode(
          bindCode(
            column = column,
            parameterIndex = parameterIndex,
            value = value
          )
        )
      }
    }
  }

  private fun bindCode(
    column: ColumnElement,
    parameterIndex: Int,
    value: CodeBlock
  ) = when (column.sqlStorageType) {
    SqlStorageType.BYTE_ARRAY,
    SqlStorageType.BOXED_BYTE_ARRAY,
    SqlStorageType.BYTE -> CodeBlock.of(
      "statement.bindBlob(%L, %L)\n",
      parameterIndex,
      column.databaseWriteValue(value)
    )
    SqlStorageType.DOUBLE -> CodeBlock.of("statement.bindDouble(%L, %L)\n", parameterIndex, value)
    SqlStorageType.FLOAT -> CodeBlock.of("statement.bindDouble(%L, %L.toDouble())\n", parameterIndex, value)
    SqlStorageType.INT,
    SqlStorageType.SHORT -> CodeBlock.of("statement.bindLong(%L, %L.toLong())\n", parameterIndex, value)
    SqlStorageType.LONG -> CodeBlock.of("statement.bindLong(%L, %L)\n", parameterIndex, value)
    SqlStorageType.STRING -> CodeBlock.of("statement.bindString(%L, %L)\n", parameterIndex, value)
  }

  private fun serializedInsertAccessCode(
    table: TableElement,
    column: ColumnElement,
    usesGeneratedRelationshipIds: Boolean
  ): CodeBlock {
    val relationship = column.relationship ?: return table.serializedReadExpression(column)
    val relationshipValue = table.readExpression(column)
    val relationshipVariable = "relationship"
    val relationshipReceiver = when {
      column.isModelPathNullable -> CodeBlock.of("%N", relationshipVariable)
      else -> relationshipValue
    }
    val serializedId = relationship.serializedDeclaredIdValue(
      value = relationshipReceiver.appendPropertyPath(
        path = relationship.referencedIdProperty,
        nullableReceiver = false
      ),
      valueCanBeNull = relationship.referencedIdValueCanBeNull
    )
    val resolvedId = when {
      usesGeneratedRelationshipIds && column.needsGeneratedRelationshipId(environment) -> CodeBlock.of(
        "generatedRelationshipIds[%S] ?: %L",
        column.columnName,
        serializedId
      )
      else -> serializedId
    }
    val requiredId = when {
      relationship.referencedIdIsNullable -> CodeBlock.of(
        "(%L ?: throw %T(%S))",
        resolvedId,
        OPERATION_FAILED_EXCEPTION,
        "Relationship \"${column.columnName}\" resolved to a NULL ID"
      )
      else -> resolvedId
    }
    return when {
      column.isModelPathNullable -> CodeBlock.of(
        "%L?.let { %N -> %L }",
        relationshipValue,
        relationshipVariable,
        requiredId
      )
      else -> requiredId
    }
  }
}
