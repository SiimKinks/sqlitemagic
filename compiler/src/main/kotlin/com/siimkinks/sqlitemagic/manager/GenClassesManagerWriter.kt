package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_GENERATED_VIEW
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CLEAR_DATA
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CLEAR_TABLES
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_COLLECT_GENERATED_VIEWS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CONFIGURE_DATABASE
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_SCHEMA_INDEXES
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_SCHEMA_TABLES
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_TEMPORARY_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_GET_NR_OF_TABLES
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_MIGRATE_VIEWS
import com.siimkinks.sqlitemagic.GeneratedNames.PROPERTY_DB_NAME
import com.siimkinks.sqlitemagic.GeneratedNames.PROPERTY_DB_VERSION
import com.siimkinks.sqlitemagic.GeneratedNames.PROPERTY_IS_DEBUG
import com.siimkinks.sqlitemagic.GeneratedNames.PROPERTY_SUBMODULE_NAMES
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_DATABASE
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_SCHEMA
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_VIEW
import com.siimkinks.sqlitemagic.WriterTypes.OPT_IN_INTERNAL_API
import com.siimkinks.sqlitemagic.WriterTypes.SQLITE_DATABASE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_UTIL
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY
import com.siimkinks.sqlitemagic.WriterTypes.STRING_SCATTER_SET
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.CONST
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.MUTABLE_LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.writeTo

internal class GenClassesManagerWriter(
  private val codeGenerator: CodeGenerator
) {
  fun write(
    database: GeneratedDatabaseElement,
    orderedTables: CreationOrderedTables
  ) {
    val genClassesManager = with(database) {
      when {
        isSubmodule -> TypeSpec.objectBuilder(className)
        else -> TypeSpec
          .classBuilder(className)
          .addSuperinterface(GENERATED_DATABASE)
      }
        .addSuperinterface(GENERATED_SCHEMA)
        .addModifiers(PUBLIC)
        .addFunction(configureDatabase())
        .addFunction(createSchemaTables(orderedTables))
        .addFunction(collectGeneratedViews())
        .addFunction(createSchemaIndexes(orderedTables.sortedIndexes(indices)))
        .addFunction(clearTables())
        .apply {
          if (!isSubmodule) {
            addFunction(
              createSchema(
                functionName = METHOD_CREATE_SCHEMA,
                temporary = false
              )
            )
            if (hasTemporarySchema(orderedTables)) {
              addFunction(
                createSchema(
                  functionName = METHOD_CREATE_TEMPORARY_SCHEMA,
                  temporary = true
                )
              )
            }
            addFunction(clearData())
            addFunction(sqlUtilDelegation(METHOD_MIGRATE_VIEWS).build())
          }
        }
        .addFunction(getNrOfTables())
        .addTablePositions(database)
        .apply {
          if (!isSubmodule) {
            addProperty(submoduleNames())
            addProperty(dbVersion())
            addProperty(dbName())
            addProperty(debug())
          }
        }
    }
    FileSpec
      .builder(database.className)
      .addFileComment("%L", GENERATION_COMMENT)
      .addAnnotation(OPT_IN_INTERNAL_API)
      .addType(genClassesManager.build())
      .build()
      .writeTo(
        codeGenerator = codeGenerator,
        aggregating = true
      )
  }

  private fun TypeSpec.Builder.addTablePositions(
    database: GeneratedDatabaseElement
  ) = apply {
    if (database.tables.isEmpty()) return@apply
    val positionOwner = when {
      database.isSubmodule -> this
      else -> TypeSpec.companionObjectBuilder()
    }
    database.tables.forEach { table ->
      positionOwner.addProperty(
        PropertySpec
          .builder(name = table.tablePositionPropertyName, type = INT)
          .addModifiers(CONST)
          .initializer("%L", table.declarationOrder)
          .build()
      )
    }
    if (!database.isSubmodule) addType(positionOwner.build())
  }

  private fun GeneratedDatabaseElement.configureDatabase() =
    databaseFunction(METHOD_CONFIGURE_DATABASE)
      .addParameter(DB_PARAMETER)
      .apply {
        if (tables.any(TableElement::hasCascadeDelete)) {
          addStatement("db.setForeignKeyConstraintsEnabled(true)")
        }
        submodules.forEach { addStatement("%T.%N(db)", it.managerClassName, METHOD_CONFIGURE_DATABASE) }
      }
      .build()

  private fun GeneratedDatabaseElement.hasTemporarySchema(orderedTables: CreationOrderedTables) =
    submodules.isNotEmpty() ||
        orderedTables.temporary.isNotEmpty() ||
        views.any { it.schema == TEMPORARY } ||
        indices.any { it.schema == TEMPORARY }

  private fun createSchema(
    functionName: String,
    temporary: Boolean
  ) = sqlUtilDelegation(
    functionName = functionName,
    utilFunctionName = METHOD_CREATE_SCHEMA,
    arguments = listOf(namedArgument("temporary", "%L", temporary))
  ).build()

  private fun clearData() = sqlUtilDelegation(
    functionName = METHOD_CLEAR_DATA,
    arguments = listOf(namedArgument("tableCount", "%N(null)", METHOD_GET_NR_OF_TABLES))
  )
    .returns(STRING_SCATTER_SET)
    .build()

  private fun sqlUtilDelegation(
    functionName: String,
    utilFunctionName: String = functionName,
    arguments: List<CodeBlock> = emptyList()
  ) = FunSpec
    .builder(functionName)
    .addModifiers(OVERRIDE)
    .addParameter(DB_PARAMETER)
    .addStatement(
      "return %L",
      callCode(
        callee = CodeBlock.of("%T.%N", SQL_UTIL, utilFunctionName),
        arguments = listOf(
          namedArgument("db", "db"),
          namedArgument("schema", "this")
        ) + arguments
      )
    )

  private fun GeneratedDatabaseElement.createSchemaTables(
    orderedTables: CreationOrderedTables
  ) = schemaPartFunction(
    name = METHOD_CREATE_SCHEMA_TABLES,
    parameters = listOf(DB_PARAMETER, TEMPORARY_PARAMETER)
  )
    .addSchemaBranches(
      temporary = orderedTables.temporary.map(::createTable),
      persistent = orderedTables.persistent.map(::createTable)
    )
    .build()

  private fun createTable(table: TableElement) =
    CodeBlock.of("db.execSQL(%T.%N)", table.generationNames.adapterClassName, FIELD_TABLE_SCHEMA)

  private fun GeneratedDatabaseElement.collectGeneratedViews() = schemaPartFunction(
    name = METHOD_COLLECT_GENERATED_VIEWS,
    parameters = listOf(ParameterSpec(name = "views", type = MUTABLE_LIST.parameterizedBy(GENERATED_VIEW)))
  )
    .apply {
      views.forEach { addStatement("views.add(%T.%N)", it.generationNames.daoClassName, FIELD_GENERATED_VIEW) }
    }
    .build()

  private fun GeneratedDatabaseElement.createSchemaIndexes(indexes: List<IndexElement>) = indexes
    .partition { it.schema == TEMPORARY }
    .let { (temporaryIndexes, persistentIndexes) ->
      schemaPartFunction(
        name = METHOD_CREATE_SCHEMA_INDEXES,
        parameters = listOf(DB_PARAMETER, TEMPORARY_PARAMETER)
      )
        .addSchemaBranches(
          temporary = temporaryIndexes.map(::createIndex),
          persistent = persistentIndexes.map(::createIndex)
        )
        .build()
    }

  private fun createIndex(index: IndexElement) = CodeBlock.of("db.execSQL(%S)", index.createSql())

  private fun GeneratedDatabaseElement.clearTables() = schemaPartFunction(
    name = METHOD_CLEAR_TABLES,
    parameters = listOf(DB_PARAMETER, ParameterSpec(name = "changedTables", type = STRING_SCATTER_SET))
  )
    .apply {
      tables.forEach { table ->
        addStatement("db.execSQL(%S)", "DELETE FROM ${table.tableName}")
        addStatement("changedTables.add(%S)", table.tableName)
      }
    }
    .build()

  private fun GeneratedDatabaseElement.getNrOfTables() = databaseFunction(METHOD_GET_NR_OF_TABLES)
    .addParameter(name = "moduleName", type = STRING.copy(nullable = true))
    .returns(INT)
    .apply {
      when {
        submodules.isEmpty() -> addStatement("return %L", tables.size)
        else -> {
          val total = buildCodeBlock {
            add("%L", tables.size)
            submodules.forEach { add(" + %T.%N(null)", it.managerClassName, METHOD_GET_NR_OF_TABLES) }
          }
          beginControlFlow("return when (moduleName)")
          addStatement("null -> %L", total)
          submodules.forEach {
            addStatement(
              "%S -> %T.%N(moduleName)",
              it.moduleName,
              it.managerClassName,
              METHOD_GET_NR_OF_TABLES
            )
          }
          addStatement("else -> %L", tables.size)
          endControlFlow()
        }
      }
    }
    .build()

  private fun GeneratedDatabaseElement.submoduleNames() = PropertySpec
    .builder(name = PROPERTY_SUBMODULE_NAMES, type = STRING_ARRAY.copy(nullable = true))
    .addModifiers(OVERRIDE)
    .getter(
      FunSpec
        .getterBuilder()
        .apply {
          when {
            submodules.isEmpty() -> addStatement("return null")
            else -> addStatement(
              "return %L",
              callCode(
                callee = CodeBlock.of("arrayOf"),
                arguments = submodules.map { CodeBlock.of("%S", it.moduleName) }
              )
            )
          }
        }
        .build()
    )
    .build()

  private fun GeneratedDatabaseElement.dbVersion() = PropertySpec
    .builder(name = PROPERTY_DB_VERSION, type = INT)
    .addModifiers(OVERRIDE)
    .initializer("%L", databaseMetadata.dbVersion ?: 1)
    .build()

  private fun GeneratedDatabaseElement.dbName() = PropertySpec
    .builder(name = PROPERTY_DB_NAME, type = STRING.copy(nullable = true))
    .addModifiers(OVERRIDE)
    .initializer("%S", databaseMetadata.dbName)
    .build()

  private fun GeneratedDatabaseElement.debug() = PropertySpec
    .builder(name = PROPERTY_IS_DEBUG, type = BOOLEAN)
    .addModifiers(OVERRIDE)
    .initializer("%L", isDebug)
    .build()

  private fun GeneratedDatabaseElement.databaseFunction(name: String) = FunSpec
    .builder(name)
    .addModifiers(
      when {
        isSubmodule -> PUBLIC
        else -> OVERRIDE
      }
    )

  private fun GeneratedDatabaseElement.schemaPartFunction(
    name: String,
    parameters: List<ParameterSpec>
  ) = FunSpec
    .builder(name)
    .addModifiers(OVERRIDE)
    .addParameters(parameters)
    .apply {
      submodules.forEach { submodule ->
        addCode(
          "%L\n",
          callCode(
            callee = CodeBlock.of("%T.%N", submodule.managerClassName, name),
            arguments = parameters.map { parameter -> namedArgument(parameter.name, "%N", parameter) }
          )
        )
      }
    }
}

private val DB_PARAMETER = ParameterSpec(name = "db", type = SQLITE_DATABASE)
private val TEMPORARY_PARAMETER = ParameterSpec(name = "temporary", type = BOOLEAN)

private fun FunSpec.Builder.addSchemaBranches(
  temporary: List<CodeBlock>,
  persistent: List<CodeBlock>
) = apply {
  when {
    temporary.isEmpty() && persistent.isEmpty() -> Unit
    temporary.isEmpty() -> addBranch(
      condition = "!temporary",
      statements = persistent
    )
    persistent.isEmpty() -> addBranch(
      condition = "temporary",
      statements = temporary
    )
    else -> {
      beginControlFlow("if (temporary)")
      temporary.forEach { addStatement("%L", it) }
      nextControlFlow("else")
      persistent.forEach { addStatement("%L", it) }
      endControlFlow()
    }
  }
}

private fun FunSpec.Builder.addBranch(
  condition: String,
  statements: List<CodeBlock>
) = apply {
  beginControlFlow("if (%L)", condition)
  statements.forEach { addStatement("%L", it) }
  endControlFlow()
}
