package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_NOT_NULL_FOR_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_NOT_NULL_FOR_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_TO_INSERT_STATEMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BIND_TO_UPDATE_STATEMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_NEW_INSTANCE_WITH_ONLY_ID
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_GENERATED_RELATIONSHIP_IDS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_IDENTITY_COLUMN_NAME
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
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    FileSpec
      .builder(table.generationNames.daoClassName)
      .addType(TableDaoWriter(table).dao())
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }
}

private class TableDaoWriter(
  private val table: TableElement
) {
  private val propertyReadExpressions = hashMapOf<PropertyPath, CodeBlock>()

  fun dao() = TypeSpec
    .objectBuilder(table.generationNames.daoClassName)
    .addModifiers(INTERNAL)
    .addSuperinterface(table.statementBinderType())
    .addFunction(bindToInsertStatement())
    .apply {
      table.idColumn?.let { idColumn ->
        if (table.construction.canConstructWithOnly(idColumn.access.path)) {
          addFunction(idOnlyConstructor(idColumn))
        }
      }
      if (table.supportsIdentityOperations) {
        addFunction(
          notNullValues(
            functionName = METHOD_BIND_NOT_NULL_FOR_INSERT,
            columns = table.columnsForInsert,
            excludesIdentityColumn = false,
            usesGeneratedRelationshipIds = table.needsGeneratedRelationshipIds
          )
        )
          .addFunction(bindToUpdateStatement())
          .addFunction(
            notNullValues(
              functionName = METHOD_BIND_NOT_NULL_FOR_UPDATE,
              columns = table.allColumns,
              excludesIdentityColumn = true
            )
          )
      }
    }
    .addFunctions(cursorReaders(table))
    .build()

  private fun readExpression(column: ColumnElement) = propertyReadExpressions.getOrPut(column.access.path) {
    table.readExpression(column)
  }

  private fun idOnlyConstructor(idColumn: ColumnElement): FunSpec {
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

  private fun bindToInsertStatement() = FunSpec
    .builder(METHOD_BIND_TO_INSERT_STATEMENT)
    .addModifiers(OVERRIDE)
    .addParameter(name = "statement", type = SUPPORT_SQLITE_STATEMENT)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_GENERATED_RELATIONSHIP_IDS, type = MAP.parameterizedBy(STRING, LONG))
    .addStatement("statement.clearBindings()")
    .addBindings(
      columns = table.columnsForInsert,
      usesGeneratedRelationshipIds = table.needsGeneratedRelationshipIds
    )
    .build()

  private fun bindToUpdateStatement() = FunSpec
    .builder(METHOD_BIND_TO_UPDATE_STATEMENT)
    .addModifiers(OVERRIDE)
    .addParameter(name = "statement", type = SUPPORT_SQLITE_STATEMENT)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_IDENTITY_COLUMN_NAME, type = STRING)
    .addStatement("statement.clearBindings()")
    .apply {
      val uniqueColumns = table.identityColumns.filterNot(ColumnElement::isId)
      when {
        uniqueColumns.size < 2 -> addIdentityUpdateBindings()
        else -> addCompactUpdateBindings(uniqueColumns)
      }
    }
    .build()

  private fun FunSpec.Builder.addIdentityUpdateBindings(): FunSpec.Builder {
    val identityColumns = table.identityColumns
    fun FunSpec.Builder.addBindingsBy(identityColumn: ColumnElement) = addBindings(
      columns = table
        .allColumns
        .filterNot { column -> column === identityColumn || column.isId } + identityColumn
    )
    return when (identityColumns.size) {
      1 -> addBindingsBy(identityColumns.single())
      else -> beginControlFlow("when (%N)", VARIABLE_IDENTITY_COLUMN_NAME)
        .apply {
          identityColumns.forEachIndexed { index, identityColumn ->
            beginControlFlow(
              "%L ->",
              identityBranch(
                columns = identityColumns,
                index = index
              )
            )
              .addBindingsBy(identityColumn)
              .endControlFlow()
          }
        }
        .endControlFlow()
    }
  }

  private fun FunSpec.Builder.addCompactUpdateBindings(
    uniqueColumns: List<ColumnElement>
  ) = when (val idColumn = table.idColumn) {
    null -> addUniqueUpdateBindings(uniqueColumns)
    else -> beginControlFlow("when (%N)", VARIABLE_IDENTITY_COLUMN_NAME)
      .beginControlFlow(
        "%S ->",
        idColumn.columnName
      )
      .addBindings(
        columns = table
          .allColumns
          .filterNot(ColumnElement::isId) + idColumn
      )
      .endControlFlow()
      .beginControlFlow("else ->")
      .addUniqueUpdateBindings(uniqueColumns)
      .endControlFlow()
      .endControlFlow()
  }

  private fun FunSpec.Builder.addUniqueUpdateBindings(uniqueColumns: List<ColumnElement>) = apply {
    val nextParameterIndex = CodeBlock.of("parameterIndex++")
    beginControlFlow("val identityOrdinal = when (%N)", VARIABLE_IDENTITY_COLUMN_NAME)
    uniqueColumns.indices.forEach { ordinal ->
      addStatement(
        "%L -> %L",
        identityBranch(
          columns = uniqueColumns,
          index = ordinal
        ),
        ordinal
      )
    }
    endControlFlow()
    addStatement("var parameterIndex = 1")
    table.allColumns.forEachIndexed { index, column ->
      if (column.isId) return@forEachIndexed
      val identityOrdinal = uniqueColumns.indexOfFirst { it === column }
      when {
        identityOrdinal >= 0 -> beginControlFlow("if (identityOrdinal != %L)", identityOrdinal)
          .addBinding(
            column = column,
            parameterIndex = nextParameterIndex,
            valueName = "value${index + 1}"
          )
          .endControlFlow()
        else -> addBinding(
          column = column,
          parameterIndex = nextParameterIndex,
          valueName = "value${index + 1}"
        )
      }
    }
    beginControlFlow("when (identityOrdinal)")
    uniqueColumns.forEachIndexed { ordinal, identityColumn ->
      beginControlFlow(
        "%L ->",
        when (ordinal) {
          uniqueColumns.lastIndex -> CodeBlock.of("else")
          else -> CodeBlock.of("%L", ordinal)
        }
      )
      addBinding(
        column = identityColumn,
        parameterIndex = CodeBlock.of("parameterIndex"),
        valueName = "selectedKeyValue"
      )
      endControlFlow()
    }
    endControlFlow()
  }

  private fun notNullValues(
    functionName: String,
    columns: List<ColumnElement>,
    excludesIdentityColumn: Boolean,
    usesGeneratedRelationshipIds: Boolean = false
  ) = FunSpec
    .builder(functionName)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = "values", type = BIND_VALUES_MAP)
    .addStatement("values.clear()")
    .apply {
      when {
        excludesIdentityColumn -> addParameter(name = VARIABLE_IDENTITY_COLUMN_NAME, type = STRING)
        else -> addParameter(name = VARIABLE_GENERATED_RELATIONSHIP_IDS, type = MAP.parameterizedBy(STRING, LONG))
      }
      columns
        .filterNot { excludesIdentityColumn && it.isId }
        .forEach { column ->
          val nullableValue = column.bindingValueCanBeNull
          val value = serializedInsertAccessCode(
            column = column,
            usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
          )
          val nullableReceiver = when {
            usesGeneratedRelationshipIds && column.needsGeneratedRelationshipId -> CodeBlock.of("(%L)", value)
            else -> value
          }
          val excludesSelectedIdentity = when {
            excludesIdentityColumn && table.identityColumns.any { it === column } -> CodeBlock.of(
              "%N != %S",
              VARIABLE_IDENTITY_COLUMN_NAME,
              column.columnName
            )
            else -> null
          }
          when {
            nullableValue && excludesSelectedIdentity != null -> beginControlFlow(
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
            nullableValue -> beginControlFlow("%L?.let", nullableReceiver)
              .addStatement(
                "values.put(%S, %L)",
                column.columnName,
                column.databaseWriteValue(CodeBlock.of("it"))
              )
              .endControlFlow()
            excludesSelectedIdentity != null -> beginControlFlow("if (%L)", excludesSelectedIdentity)
              .addStatement(
                "values.put(%S, %L)",
                column.columnName,
                column.databaseWriteValue(value)
              )
              .endControlFlow()
            else -> addStatement(
              "values.put(%S, %L)",
              column.columnName,
              column.databaseWriteValue(value)
            )
          }
        }
    }
    .build()

  private fun FunSpec.Builder.addBindings(
    columns: List<ColumnElement>,
    usesGeneratedRelationshipIds: Boolean = false
  ) = apply {
    columns.forEachIndexed { index, column ->
      val parameterIndex = index + 1
      addBinding(
        column = column,
        parameterIndex = CodeBlock.of("%L", parameterIndex),
        valueName = "value$parameterIndex",
        usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
      )
    }
  }

  private fun FunSpec.Builder.addBinding(
    column: ColumnElement,
    parameterIndex: CodeBlock,
    valueName: String,
    usesGeneratedRelationshipIds: Boolean = false
  ) = apply {
    val value = serializedInsertAccessCode(
      column = column,
      usesGeneratedRelationshipIds = usesGeneratedRelationshipIds
    )
    when {
      column.bindingValueCanBeNull -> addStatement("val %N = %L", valueName, value)
        .beginControlFlow("if (%N == null)", valueName)
        .addStatement("statement.bindNull(%L)", parameterIndex)
        .nextControlFlow("else")
        .addValueBinding(
          column = column,
          parameterIndex = parameterIndex,
          value = CodeBlock.of("%N", valueName)
        )
        .endControlFlow()
      else -> addValueBinding(
        column = column,
        parameterIndex = parameterIndex,
        value = value
      )
    }
  }

  private fun serializedInsertAccessCode(
    column: ColumnElement,
    usesGeneratedRelationshipIds: Boolean
  ): CodeBlock {
    val propertyReadExpression = readExpression(column)
    val relationship = column.relationship ?: return table.serializedReadExpression(
      column = column,
      propertyReadExpression = propertyReadExpression
    )
    val relationshipVariable = "relationship"
    val relationshipReceiver = when {
      column.isModelPathNullable -> CodeBlock.of("%N", relationshipVariable)
      else -> propertyReadExpression
    }
    val serializedId = relationship.serializedDeclaredIdValue(
      value = relationshipReceiver.appendPropertyPath(
        path = relationship.referencedIdProperty,
        nullableReceiver = false
      ),
      valueCanBeNull = relationship.referencedIdValueCanBeNull
    )
    val resolvedId = when {
      usesGeneratedRelationshipIds && column.needsGeneratedRelationshipId -> CodeBlock.of(
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
        """Relationship "${column.columnName}" resolved to a NULL ID"""
      )
      else -> resolvedId
    }
    return when {
      column.isModelPathNullable -> CodeBlock.of(
        "%L?.let { %N -> %L }",
        propertyReadExpression,
        relationshipVariable,
        requiredId
      )
      else -> requiredId
    }
  }
}

private fun FunSpec.Builder.addValueBinding(
  column: ColumnElement,
  parameterIndex: CodeBlock,
  value: CodeBlock
) = when (column.sqlStorageType) {
  SqlStorageType.BYTE_ARRAY,
  SqlStorageType.BOXED_BYTE_ARRAY,
  SqlStorageType.BYTE -> addStatement(
    "statement.bindBlob(%L, %L)",
    parameterIndex,
    column.databaseWriteValue(value)
  )
  SqlStorageType.DOUBLE -> addStatement("statement.bindDouble(%L, %L)", parameterIndex, value)
  SqlStorageType.FLOAT -> addStatement("statement.bindDouble(%L, %L.toDouble())", parameterIndex, value)
  SqlStorageType.INT,
  SqlStorageType.SHORT -> addStatement("statement.bindLong(%L, %L.toLong())", parameterIndex, value)
  SqlStorageType.LONG -> addStatement("statement.bindLong(%L, %L)", parameterIndex, value)
  SqlStorageType.STRING -> addStatement("statement.bindString(%L, %L)", parameterIndex, value)
}

private fun ColumnElement.databaseWriteValue(value: CodeBlock) = when (sqlStorageType) {
  SqlStorageType.BOXED_BYTE_ARRAY -> CodeBlock.of("%L.toByteArray()", value)
  SqlStorageType.BYTE -> CodeBlock.of("byteArrayOf(%L)", value)
  else -> value
}
