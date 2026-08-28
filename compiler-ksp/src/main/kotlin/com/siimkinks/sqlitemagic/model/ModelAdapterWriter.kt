package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_DEFAULT_IDENTITY_COLUMN
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_INSERT_SQL
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_MAX_COLUMNS
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_MODULE_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TRIGGER_TABLE_NAMES
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_WITHOUT_ROW_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ASSIGN_GENERATED_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_BY_COLUMN
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_HAS_IDENTITY_VALUE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_IDENTITY
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_INSERT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_INSERT_RELATIONSHIPS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_PERSIST
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_PERSIST_RELATIONSHIPS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_REMEMBER_GENERATED_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE_RELATIONSHIPS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_UPDATE_STATEMENT_SQL
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_OPERATIONS
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_DEFAULT_IDENTITY_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_GENERATED_ID_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_IDENTITY_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_INSERT_RESULT
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_PERSIST_RESULT
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_RECURSIVE_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.ENTITY_RELATIONSHIP_OPERATIONS
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_ENTITY_IDENTITY
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY
import com.siimkinks.sqlitemagic.WriterTypes.UNCHECKED_CAST
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.CONST
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode

internal class ModelAdapterWriter(
  private val environment: Environment
) : ModelWriter {
  override fun write(tableRoundElement: TableRoundElement) = with(tableRoundElement) {
    val adapterClassName = table.generationNames.adapterClassName
    val adapter = TypeSpec
      .objectBuilder(adapterClassName)
      .addModifiers(INTERNAL)
      .addProperty(schemaSqlConstant(table))
      .addSuperinterface(adapterType(table))
      .addSuperinterface(
        superinterface = table.statementBinderType(),
        delegate = CodeBlock.of("%T", table.generationNames.daoClassName)
      )
      .addProperty(moduleName())
      .addProperty(
        metadataStringProperty(
          name = FIELD_TABLE_NAME,
          value = table.tableName
        )
      )
      .addProperty(
        metadataStringProperty(
          name = FIELD_INSERT_SQL,
          value = table.insertSql()
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_TABLE_POSITION,
          type = INT,
          initializer = table.declarationOrder.toString()
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_WITHOUT_ROW_ID,
          type = BOOLEAN,
          initializer = (TableOption.WITHOUT_ROWID in table.options).toString()
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_MAX_COLUMNS,
          type = INT,
          initializer = table.allColumns.size.toString()
        )
      )
      .apply {
        val modelClassName = table.modelClassName
        if (table.hasRecursiveRelationships) {
          addSuperinterface(ENTITY_RECURSIVE_ADAPTER.parameterizedBy(modelClassName))
          addProperty(triggerTableNames(table))
        }
        if (table.generatedIdAssignmentColumn != null) {
          addSuperinterface(ENTITY_GENERATED_ID_ADAPTER.parameterizedBy(modelClassName))
        }
        if (table.supportsIdentityOperations) {
          addFunction(identity(table))
            .addFunction(hasIdentityValue(table))
            .addFunction(updateStatementSql(table))
          if (table.supportsDefaultIdentity) {
            addProperty(defaultIdentityColumn(table))
          }
        }
        table.generatedIdAssignmentColumn?.let { idColumn ->
          addFunction(
            assignGeneratedId(
              table = table,
              idColumn = idColumn
            )
          )
        }
        if (table.hasRecursiveRelationships) {
          addFunction(
            relationshipOperation(
              table = table,
              functionName = METHOD_INSERT_RELATIONSHIPS,
              resultType = ENTITY_INSERT_RESULT
            )
          )
          addFunction(
            relationshipOperation(
              table = table,
              functionName = METHOD_UPDATE_RELATIONSHIPS
            )
          )
          addFunction(
            relationshipOperation(
              table = table,
              functionName = METHOD_PERSIST_RELATIONSHIPS,
              resultType = ENTITY_PERSIST_RESULT
            )
          )
        }
      }
      .build()
    FileSpec
      .builder(adapterClassName)
      .addType(adapter)
      .build()
      .writeModelSource(
        codeGenerator = environment.codeGenerator,
        originatingFiles = originatingFiles
      )
  }

  private fun adapterType(table: TableElement) = when {
    table.supportsDefaultIdentity -> ENTITY_DEFAULT_IDENTITY_ADAPTER
    table.supportsIdentityOperations -> ENTITY_IDENTITY_ADAPTER
    else -> ENTITY_ADAPTER
  }.parameterizedBy(table.modelClassName)

  private fun schemaSqlConstant(table: TableElement) = PropertySpec
    .builder(name = FIELD_TABLE_SCHEMA, type = STRING)
    .addModifiers(CONST)
    .initializer("%S", table.schemaSql())
    .build()

  private fun metadataProperty(
    name: String,
    type: TypeName,
    initializer: String
  ) = PropertySpec
    .builder(name = name, type = type)
    .addModifiers(OVERRIDE)
    .initializer(initializer)
    .build()

  private fun metadataStringProperty(
    name: String,
    value: String
  ) = PropertySpec
    .builder(name = name, type = STRING)
    .addModifiers(OVERRIDE)
    .initializer("%S", value)
    .build()

  private fun moduleName() = PropertySpec
    .builder(name = FIELD_MODULE_NAME, type = STRING.copy(nullable = true))
    .addModifiers(OVERRIDE)
    .apply {
      when (val moduleName = environment.submoduleName) {
        null -> initializer("null")
        else -> initializer("%S", moduleName)
      }
    }
    .build()

  private fun triggerTableNames(table: TableElement): PropertySpec {
    val tableNames = linkedSetOf<String>()

    fun collect(typeKey: String) {
      val current = environment.tableElements[typeKey] ?: return
      if (!tableNames.add(current.tableName)) {
        return
      }
      current.recursiveRelationshipColumns
        .mapNotNull(ColumnElement::referencedTableTypeKey)
        .forEach(::collect)
    }

    collect(table.typeKey)
    val values = tableNames
      .map { CodeBlock.of("%S", it) }
      .joinToCode(separator = ", ")
    return PropertySpec
      .builder(name = FIELD_TRIGGER_TABLE_NAMES, type = STRING_ARRAY)
      .addModifiers(OVERRIDE)
      .initializer("arrayOf(%L)", values)
      .build()
  }

  private fun relationshipOperation(
    table: TableElement,
    functionName: String,
    resultType: TypeName? = null
  ): FunSpec {
    val operationName = when (functionName) {
      METHOD_INSERT_RELATIONSHIPS -> METHOD_INSERT
      METHOD_UPDATE_RELATIONSHIPS -> METHOD_UPDATE
      METHOD_PERSIST_RELATIONSHIPS -> METHOD_PERSIST
      else -> error("Unsupported relationship operation: $functionName")
    }
    val function = FunSpec
      .builder(functionName)
      .addModifiers(OVERRIDE)
      .addParameter(name = "entity", type = table.modelClassName)
      .addParameter(name = VARIABLE_OPERATIONS, type = ENTITY_RELATIONSHIP_OPERATIONS)
      .returns(BOOLEAN)
      .addStatement("var successful = true")
    table.recursiveRelationshipColumns.forEach { column ->
      val referencedTable = checkNotNull(
        environment.tableElements[column.relationship?.referencedTableTypeKey]
      )
      val access = table.readExpression(column)
      if (column.isModelPathNullable) {
        function.beginControlFlow("%L?.let", access)
      }
      val relationshipEntity = when {
        column.isModelPathNullable -> CodeBlock.of("it")
        else -> access
      }
      val operation = CodeBlock.of(
        "%N.%N(\n  adapter = %T,\n  entity = %L\n)",
        VARIABLE_OPERATIONS,
        operationName,
        referencedTable.generationNames.adapterClassName,
        relationshipEntity
      )
      when (functionName) {
        METHOD_UPDATE_RELATIONSHIPS -> function
          .beginControlFlow("if (!%L)", operation)
          .addStatement("successful = false")
          .endControlFlow()
        METHOD_INSERT_RELATIONSHIPS,
        METHOD_PERSIST_RELATIONSHIPS -> {
          val relationshipResultType = checkNotNull(resultType)
          when {
            !column.needsGeneratedRelationshipId(environment) -> function
              .beginControlFlow("if (%L is %T.Ignored)", operation, relationshipResultType)
              .addStatement("successful = false")
              .endControlFlow()
            else -> function
              .beginControlFlow("when (val result = %L)", operation)
              .addStatement("is %T.Ignored -> successful = false", relationshipResultType)
              .apply {
                if (functionName == METHOD_PERSIST_RELATIONSHIPS) {
                  addStatement("is %T.Updated -> Unit", relationshipResultType)
                }
              }
              .beginControlFlow("is %T.Inserted ->", relationshipResultType)
              .addStatement(
                "result.rowId?.let { %N.%N(columnName = %S, rowId = it) }",
                VARIABLE_OPERATIONS,
                METHOD_REMEMBER_GENERATED_ID,
                column.columnName
              )
              .endControlFlow()
              .endControlFlow()
          }
        }
      }
      if (column.isModelPathNullable) {
        function.endControlFlow()
      }
    }
    return function
      .addStatement("return successful")
      .build()
  }

  private fun identity(table: TableElement) = FunSpec
    .builder(METHOD_IDENTITY)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = METHOD_BY_COLUMN, type = table.byColumnType)
    .returns(GENERATED_ENTITY_IDENTITY)
    .apply {
      table.identityColumns.forEach { column ->
        beginControlFlow(
          "if (byColumn == %T.%N.%N)",
          table.generationNames.tableClassName,
          table.structureFieldName,
          column.fieldName
        )
          .addCode(identityResult(table, column))
          .endControlFlow()
      }
    }
    .addStatement("throw %T(%S)", IllegalArgumentException::class, "Column does not identify an entity property")
    .build()

  private fun identityResult(
    table: TableElement,
    column: ColumnElement
  ) = CodeBlock.of(
    "return %T(columnName = %S, serializedValue = %L)",
    GENERATED_ENTITY_IDENTITY,
    column.columnName,
    identityValue(table, column)
  )

  private fun hasIdentityValue(table: TableElement) = FunSpec
    .builder(METHOD_HAS_IDENTITY_VALUE)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = METHOD_BY_COLUMN, type = table.byColumnType)
    .returns(BOOLEAN)
    .apply {
      table.identityColumns.forEach { column ->
        beginControlFlow(
          "if (byColumn == %T.%N.%N)",
          table.generationNames.tableClassName,
          table.structureFieldName,
          column.fieldName
        )
        when {
          column.isSchemaNullable || column.serializedValueCanBeNull -> addStatement(
            "return %L != null",
            table.serializedReadExpression(column)
          )
          else -> addStatement("return true")
        }
        endControlFlow()
      }
    }
    .addStatement("throw %T(%S)", IllegalArgumentException::class, "Column does not identify an entity property")
    .build()

  private fun updateStatementSql(table: TableElement) = FunSpec
    .builder(METHOD_UPDATE_STATEMENT_SQL)
    .addModifiers(OVERRIDE)
    .addParameter(name = METHOD_BY_COLUMN, type = table.byColumnType)
    .returns(STRING)
    .apply {
      table.identityColumns.forEach { identityColumn ->
        beginControlFlow(
          "if (byColumn == %T.%N.%N)",
          table.generationNames.tableClassName,
          table.structureFieldName,
          identityColumn.fieldName
        )
          .addStatement("return %S", table.updateSql(identityColumn))
          .endControlFlow()
      }
    }
    .addStatement("throw %T(%S)", IllegalArgumentException::class, "Column does not identify an entity property")
    .build()

  private fun defaultIdentityColumn(table: TableElement) = PropertySpec
    .builder(name = FIELD_DEFAULT_IDENTITY_COLUMN, type = table.byColumnType)
    .addAnnotation(UNCHECKED_CAST)
    .addModifiers(OVERRIDE)
    .initializer(
      "%T.%N.%N as %T",
      table.generationNames.tableClassName,
      table.structureFieldName,
      checkNotNull(table.idColumn).fieldName,
      table.byColumnType
    )
    .build()

  private fun assignGeneratedId(
    table: TableElement,
    idColumn: ColumnElement
  ) = FunSpec
    .builder(METHOD_ASSIGN_GENERATED_ID)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = "rowId", type = LONG)
    .addStatement(
      "%L = %L",
      table.readExpression(idColumn),
      generatedIdValue(idColumn)
    )
    .build()

  private fun identityValue(
    table: TableElement,
    column: ColumnElement
  ) = CodeBlock.of(
    when (column.sqlStorageType) {
      SqlStorageType.STRING -> "requireNotNull(%L) { %S }"
      else -> "requireNotNull(%L) { %S }.toString()"
    },
    table.serializedReadExpression(column),
    "Entity identity value cannot be null: ${column.columnName}"
  )

  private fun generatedIdValue(idColumn: ColumnElement): CodeBlock {
    val serializedValue = when (idColumn.sqlStorageType) {
      SqlStorageType.INT -> CodeBlock.of("rowId.toInt()")
      SqlStorageType.SHORT -> CodeBlock.of("rowId.toShort()")
      SqlStorageType.LONG -> CodeBlock.of("rowId")
      else -> error("Auto-increment ID must use INTEGER storage")
    }
    return idColumn.transformer
      ?.deserializedValueGetter(serializedValue)
      ?: serializedValue
  }
}
