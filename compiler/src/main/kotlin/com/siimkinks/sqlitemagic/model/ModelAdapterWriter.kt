package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_DEFAULT_IDENTITY_COLUMN_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_INSERT_SQL
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_MAX_COLUMNS
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_MODULE_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_POSITION
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TRIGGER_TABLE_NAMES
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_WITHOUT_ROW_ID
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_ASSIGN_GENERATED_ID
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
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_IDENTITY_COLUMN_NAME
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
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
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
        metadataProperty(
          name = FIELD_TABLE_NAME,
          type = STRING,
          initializer = CodeBlock.of("%S", table.tableName)
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_INSERT_SQL,
          type = STRING,
          initializer = CodeBlock.of("%S", table.insertSql())
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_TABLE_POSITION,
          type = INT,
          initializer = CodeBlock.of(
            "%T.%N",
            environment.getGenClassesManagerClassName(),
            table.tablePositionPropertyName
          )
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_WITHOUT_ROW_ID,
          type = BOOLEAN,
          initializer = CodeBlock.of("%L", WITHOUT_ROWID in table.options)
        )
      )
      .addProperty(
        metadataProperty(
          name = FIELD_MAX_COLUMNS,
          type = INT,
          initializer = CodeBlock.of("%L", table.allColumns.size)
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
        table.generatedIdAssignmentColumn?.let {
          addFunction(
            assignGeneratedId(
              table = table,
              idColumn = it
            )
          )
        }
        if (table.hasRecursiveRelationships) {
          addFunctions(
            RelationshipOperation.entries.map {
              relationshipOperation(
                table = table,
                operation = it
              )
            }
          )
        }
      }
      .build()
    FileSpec
      .builder(adapterClassName)
      .addType(adapter)
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
    initializer: CodeBlock
  ) = PropertySpec
    .builder(name = name, type = type)
    .addModifiers(OVERRIDE)
    .initializer(initializer)
    .build()

  private fun moduleName() = PropertySpec
    .builder(name = FIELD_MODULE_NAME, type = STRING.copy(nullable = true))
    .addModifiers(OVERRIDE)
    .initializer("%S", environment.submoduleName)
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
    return PropertySpec
      .builder(name = FIELD_TRIGGER_TABLE_NAMES, type = STRING_ARRAY)
      .addModifiers(OVERRIDE)
      .initializer(
        callCode(
          callee = CodeBlock.of("arrayOf"),
          arguments = tableNames.map { CodeBlock.of("%S", it) }
        )
      )
      .build()
  }

  private fun relationshipOperation(
    table: TableElement,
    operation: RelationshipOperation
  ) = FunSpec
    .builder(operation.functionName)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_OPERATIONS, type = ENTITY_RELATIONSHIP_OPERATIONS)
    .returns(BOOLEAN)
    .addStatement("var successful = true")
    .apply {
      table.recursiveRelationshipColumns.forEach { column ->
        val access = table.readExpression(column)
        if (column.isModelPathNullable) {
          beginControlFlow("%L?.let", access)
        }
        val relationshipEntity = when {
          column.isModelPathNullable -> CodeBlock.of("it")
          else -> access
        }
        val operationCall = CodeBlock.of(
          "%N.%N(adapter = %T, entity = %L)",
          VARIABLE_OPERATIONS,
          operation.operationName,
          checkNotNull(column.relationship).referencedTableNames.adapterClassName,
          relationshipEntity
        )
        when (val resultType = operation.resultType) {
          null -> beginControlFlow("if (!%L)", operationCall)
            .addStatement("successful = false")
            .endControlFlow()
          else -> when {
            !column.needsGeneratedRelationshipId -> beginControlFlow(
              "if (%L is %T.Ignored)",
              operationCall,
              resultType
            )
              .addStatement("successful = false")
              .endControlFlow()
            else -> beginControlFlow("when (val result = %L)", operationCall)
              .addStatement("is %T.Ignored -> successful = false", resultType)
              .apply {
                if (operation == RelationshipOperation.PERSIST) {
                  addStatement("is %T.Updated -> Unit", resultType)
                }
              }
              .beginControlFlow("is %T.Inserted ->", resultType)
              .beginControlFlow("result.rowId?.let")
              .addStatement(
                "%L",
                callCode(
                  callee = CodeBlock.of("%N.%N", VARIABLE_OPERATIONS, METHOD_REMEMBER_GENERATED_ID),
                  arguments = listOf(
                    namedArgument("columnName", "%S", column.columnName),
                    namedArgument("rowId", "it")
                  )
                )
              )
              .endControlFlow()
              .endControlFlow()
              .endControlFlow()
          }
        }
        if (column.isModelPathNullable) {
          endControlFlow()
        }
      }
    }
    .addStatement("return successful")
    .build()

  private fun identity(table: TableElement) = FunSpec
    .builder(METHOD_IDENTITY)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_IDENTITY_COLUMN_NAME, type = STRING)
    .returns(ANY)
    .addIdentityDispatch(table = table) { column ->
      when {
        column.identityValueCanBeNull -> CodeBlock.of(
          "requireNotNull(%L) { %S }",
          table.serializedReadExpression(column),
          "Entity identity value cannot be null: ${column.columnName}"
        )
        else -> table.serializedReadExpression(column)
      }
    }
    .build()

  private fun hasIdentityValue(table: TableElement) = FunSpec
    .builder(METHOD_HAS_IDENTITY_VALUE)
    .addModifiers(OVERRIDE)
    .addParameter(name = "entity", type = table.modelClassName)
    .addParameter(name = VARIABLE_IDENTITY_COLUMN_NAME, type = STRING)
    .returns(BOOLEAN)
    .addIdentityDispatch(table = table) { column ->
      when {
        column.identityValueCanBeNull -> CodeBlock.of(
          "%L != null",
          table.serializedReadExpression(column)
        )
        else -> CodeBlock.of("true")
      }
    }
    .build()

  private fun updateStatementSql(table: TableElement) = FunSpec
    .builder(METHOD_UPDATE_STATEMENT_SQL)
    .addModifiers(OVERRIDE)
    .addParameter(name = VARIABLE_IDENTITY_COLUMN_NAME, type = STRING)
    .returns(STRING)
    .addIdentityDispatch(table = table) { CodeBlock.of("%S", table.updateSql(it)) }
    .build()

  private fun FunSpec.Builder.addIdentityDispatch(
    table: TableElement,
    value: (ColumnElement) -> CodeBlock
  ): FunSpec.Builder {
    val values = table.identityColumns.map(value)
    return when {
      values.distinct().size == 1 -> addStatement("return %L", values.first())
      else -> beginControlFlow("return when (%N)", VARIABLE_IDENTITY_COLUMN_NAME)
        .apply {
          values.forEachIndexed { index, columnValue ->
            addStatement(
              "%L -> %L",
              identityBranch(
                columns = table.identityColumns,
                index = index
              ),
              columnValue
            )
          }
        }
        .endControlFlow()
    }
  }

  private fun defaultIdentityColumn(table: TableElement) = PropertySpec
    .builder(name = FIELD_DEFAULT_IDENTITY_COLUMN_NAME, type = STRING)
    .addModifiers(OVERRIDE)
    .initializer("%S", checkNotNull(table.idColumn).columnName)
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

private enum class RelationshipOperation(
  val functionName: String,
  val operationName: String,
  val resultType: ClassName?
) {
  INSERT(
    functionName = METHOD_INSERT_RELATIONSHIPS,
    operationName = METHOD_INSERT,
    resultType = ENTITY_INSERT_RESULT
  ),
  UPDATE(
    functionName = METHOD_UPDATE_RELATIONSHIPS,
    operationName = METHOD_UPDATE,
    resultType = null
  ),
  PERSIST(
    functionName = METHOD_PERSIST_RELATIONSHIPS,
    operationName = METHOD_PERSIST,
    resultType = ENTITY_PERSIST_RESULT
  )
}

private val ColumnElement.identityValueCanBeNull get() = isValueNullable || serializedValueCanBeNull

private fun TableElement.insertSql() = when {
  columnsForInsert.isEmpty() -> "INSERT%s INTO $tableName DEFAULT VALUES"
  else -> {
    val names = columnsForInsert.joinToString(separator = ", ", transform = ColumnElement::columnName)
    val values = columnsForInsert.joinToString(separator = ", ") { "?" }
    "INSERT%s INTO $tableName ($names) VALUES ($values)"
  }
}

private fun TableElement.updateSql(identityColumn: ColumnElement): String {
  val columns = allColumns.filterNot { it === identityColumn || it.isId }
  val setters = when {
    columns.isEmpty() -> "${identityColumn.columnName}=${identityColumn.columnName}"
    else -> columns.joinToString(separator = ", ") { "${it.columnName}=?" }
  }
  return "UPDATE%s $tableName SET $setters WHERE ${identityColumn.columnName}=?"
}
