package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_COLUMN_FOR_VALUE_OR_NULL
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_CLEAR_DATA
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_COLUMN_FOR_VALUE
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_CONFIGURE_DATABASE
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_CREATE_SCHEMA
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_CREATE_TEMPORARY_SCHEMA
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_NAME
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_DB_VERSION
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_NR_OF_TABLES
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_GET_SUBMODULE_NAMES
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_IS_DEBUG
import com.siimkinks.sqlitemagic.GlobalConst.METHOD_MIGRATE_VIEWS
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_DATABASE
import com.siimkinks.sqlitemagic.WriterTypes.LOG_UTIL
import com.siimkinks.sqlitemagic.WriterTypes.NOT_NULLABLE
import com.siimkinks.sqlitemagic.WriterTypes.SQLITE_DATABASE
import com.siimkinks.sqlitemagic.WriterTypes.SQLITE_MAGIC
import com.siimkinks.sqlitemagic.WriterTypes.SQL_UTIL
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY
import com.siimkinks.sqlitemagic.WriterTypes.STRING_ARRAY_SET
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.WriterTypes.UNCHECKED_CAST
import com.siimkinks.sqlitemagic.WriterTypes.UTILS
import com.siimkinks.sqlitemagic.dbconfig.SubmoduleDatabaseMetadata
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.parserName
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.joinToCode
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
        else -> TypeSpec.classBuilder(className)
          .addSuperinterface(GENERATED_DATABASE)
      }
        .addModifiers(PUBLIC)
        .addFunction(configureDatabase())
        .addFunction(createSchema(tables = orderedTables.persistent))
        .addFunction(createTemporarySchema(tables = orderedTables.temporary))
        .addFunction(clearData())
        .addFunction(migrateViews())
        .addFunction(getNrOfTables())
        .apply {
          when {
            isSubmodule -> addFunction(columnForValueOrNull())
            else -> {
              addFunction(getSubmoduleNames())
              addFunction(getDbVersion())
              addFunction(getDbName())
              addFunction(columnForValue())
              addFunction(isDebug())
            }
          }
        }
    }
    FileSpec
      .builder(database.className)
      .addFileComment("%L", GENERATION_COMMENT)
      .addType(genClassesManager.build())
      .build()
      .writeTo(
        codeGenerator = codeGenerator,
        aggregating = true
      )
  }

  private fun GeneratedDatabaseElement.configureDatabase() =
    databaseFunction(METHOD_CONFIGURE_DATABASE)
      .addParameter(name = "db", type = SQLITE_DATABASE)
      .apply {
        if (tables.any(TableElement::hasCascadeDelete)) {
          addStatement("db.setForeignKeyConstraintsEnabled(true)")
        }
        submodules.forEach { submodule ->
          addStatement("%T.%N(db)", submodule.managerClassName, METHOD_CONFIGURE_DATABASE)
        }
      }
      .build()

  private fun GeneratedDatabaseElement.createSchema(tables: List<TableElement>) =
    schemaCreationFunction(
      functionName = METHOD_CREATE_SCHEMA,
      tables = tables,
      submoduleMethod = METHOD_CREATE_SCHEMA,
      logMessage = "Creating tables"
    )

  private fun GeneratedDatabaseElement.createTemporarySchema(tables: List<TableElement>) =
    schemaCreationFunction(
      functionName = METHOD_CREATE_TEMPORARY_SCHEMA,
      tables = tables,
      submoduleMethod = METHOD_CREATE_TEMPORARY_SCHEMA,
      logMessage = "Creating temporary tables"
    )

  private fun GeneratedDatabaseElement.schemaCreationFunction(
    functionName: String,
    tables: List<TableElement>,
    submoduleMethod: String,
    logMessage: String
  ): FunSpec {
    val builder = databaseFunction(functionName)
      .addParameter(name = "db", type = SQLITE_DATABASE)
    if (tables.isEmpty() && submodules.isEmpty()) {
      return builder.addStatement("return Unit").build()
    }
    builder
      .addStatement("db.beginTransaction()")
      .beginControlFlow("try")
    submodules.forEach { submodule ->
      builder.addStatement("%T.%N(db)", submodule.managerClassName, submoduleMethod)
    }
    if (tables.isNotEmpty()) {
      builder.addRuntimeDebugLog(logMessage)
      tables.forEach { table ->
        builder.addStatement("db.execSQL(%T.TABLE_SCHEMA)", table.generationNames.adapterClassName)
      }
    }
    return builder
      .addStatement("db.setTransactionSuccessful()")
      .nextControlFlow("catch (exception: %T)", Exception::class)
      .addRuntimeErrorLog()
      .nextControlFlow("finally")
      .addStatement("db.endTransaction()")
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.clearData() =
    databaseFunction(METHOD_CLEAR_DATA)
      .addParameter(name = "db", type = SQLITE_DATABASE)
      .returns(STRING_ARRAY_SET)
      .addStatement("val allChangedTables = %T(%N(null))", STRING_ARRAY_SET, METHOD_GET_NR_OF_TABLES)
      .addStatement("db.beginTransaction()")
      .beginControlFlow("try")
      .apply {
        submodules.forEach { submodule ->
          addStatement(
            "allChangedTables.addAll(%T.%N(db))",
            submodule.managerClassName,
            METHOD_CLEAR_DATA
          )
        }
        if (tables.isNotEmpty()) {
          addRuntimeDebugLog("Clearing data")
          tables.forEach { table ->
            addStatement("db.execSQL(%S)", "DELETE FROM ${table.tableName}")
              .addStatement("allChangedTables.add(%S)", table.tableName)
          }
        }
      }
      .addStatement("db.setTransactionSuccessful()")
      .addStatement("return allChangedTables")
      .nextControlFlow("catch (exception: %T)", Exception::class)
      .addRuntimeErrorLog()
      .addStatement("throw exception")
      .nextControlFlow("finally")
      .addStatement("db.endTransaction()")
      .endControlFlow()
      .build()

  private fun GeneratedDatabaseElement.migrateViews() =
    databaseFunction(METHOD_MIGRATE_VIEWS)
      .addParameter(name = "db", type = SQLITE_DATABASE)
      .apply {
        submodules.forEach { submodule ->
          addStatement("%T.%N(db)", submodule.managerClassName, METHOD_MIGRATE_VIEWS)
        }
        if (submodules.isEmpty()) {
          addStatement("return Unit")
        }
      }
      .build()

  private fun GeneratedDatabaseElement.getNrOfTables(): FunSpec {
    val builder = databaseFunction(METHOD_GET_NR_OF_TABLES)
      .addParameter(name = "moduleName", type = STRING.copy(nullable = true))
      .returns(INT)
    if (submodules.isEmpty()) {
      return builder.addStatement("return %L", tables.size).build()
    }
    val total = CodeBlock
      .builder()
      .add("%L", tables.size)
      .apply {
        submodules.forEach { submodule ->
          add(" + %T.%N(null)", submodule.managerClassName, METHOD_GET_NR_OF_TABLES)
        }
      }
      .build()
    builder
      .beginControlFlow("return when (moduleName)")
      .addStatement("null -> %L", total)
    submodules.forEach { submodule ->
      builder.addStatement(
        "%S -> %T.%N(moduleName)",
        submodule.moduleName,
        submodule.managerClassName,
        METHOD_GET_NR_OF_TABLES
      )
    }
    return builder
      .addStatement("else -> %L", tables.size)
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.getSubmoduleNames() =
    databaseFunction(METHOD_GET_SUBMODULE_NAMES)
      .returns(STRING_ARRAY.copy(nullable = true))
      .apply {
        when {
          submodules.isEmpty() -> addStatement("return null")
          else -> addStatement(
            "return arrayOf(%L)",
            submodules
              .map { CodeBlock.of("%S", it.moduleName) }
              .joinToCode(separator = ", ")
          )
        }
      }
      .build()

  private fun GeneratedDatabaseElement.getDbVersion() =
    databaseFunction(METHOD_GET_DB_VERSION)
      .returns(INT)
      .addStatement("return %L", databaseMetadata.dbVersion ?: 1)
      .build()

  private fun GeneratedDatabaseElement.getDbName() =
    databaseFunction(METHOD_GET_DB_NAME)
      .returns(STRING.copy(nullable = true))
      .apply {
        when (val dbName = databaseMetadata.dbName) {
          null -> addStatement("return null")
          else -> addStatement("return %S", dbName)
        }
      }
      .build()

  private fun GeneratedDatabaseElement.isDebug() =
    databaseFunction(METHOD_IS_DEBUG)
      .returns(BOOLEAN)
      .addStatement("return %L", isDebug)
      .build()

  private fun GeneratedDatabaseElement.columnForValue(): FunSpec {
    val valueType = TypeVariableName("V", ANY)
    val returnType = columnReturnType(valueType)
    val builder = databaseFunction(METHOD_COLUMN_FOR_VALUE)
      .addAnnotation(UNCHECKED_CAST)
      .addTypeVariable(valueType)
      .addParameter(name = "input", type = valueType)
      .returns(returnType)
      .addStatement("val className = input::class.qualifiedName")
      .beginControlFlow("return when (className)")
    addTransformerBranches(
      builder = builder,
      returnType = returnType,
      includeDefaults = true
    )
    val fallback = submodules
      .asReversed()
      .fold(
        initial = fallbackColumn(valueType),
        operation = ::submoduleFallback
      )
    return builder
      .addCode("else -> %L\n", fallback)
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.columnForValueOrNull(): FunSpec {
    val valueType = TypeVariableName("V", ANY)
    val returnType = columnReturnType(valueType)
    val builder = databaseFunction(METHOD_COLUMN_FOR_VALUE_OR_NULL)
      .addAnnotation(UNCHECKED_CAST)
      .addTypeVariable(valueType)
      .addParameter(name = "className", type = STRING.copy(nullable = true))
      .addParameter(name = "input", type = valueType)
      .returns(returnType.copy(nullable = true))
      .beginControlFlow("return when (className)")
    addTransformerBranches(
      builder = builder,
      returnType = returnType,
      includeDefaults = false
    )
    return builder
      .addStatement("else -> null")
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.addTransformerBranches(
    builder: FunSpec.Builder,
    returnType: TypeName,
    includeDefaults: Boolean
  ) {
    transformers
      .asSequence()
      .filter { includeDefaults || !it.isDefaultTransformer }
      .groupBy { it.deserializedType.qualifiedName }
      .forEach { (qualifiedName, matchingTransformers) ->
        builder.addCode("%S -> ", qualifiedName)
        when {
          matchingTransformers.size > 1 -> builder.addCode(
            CodeBlock
              .builder()
              .add("throw %T(\n", UnsupportedOperationException::class)
              .indent()
              .add("%S\n", "Unable to disambiguate transformer for $qualifiedName")
              .unindent()
              .add(")\n")
              .build()
          )
          else -> builder.addCode(
            matchingTransformers
              .single()
              .columnForValue(returnType)
          )
        }
      }
  }

  private fun TransformerElement.columnForValue(returnType: TypeName): CodeBlock {
    val deserializedType = deserializedType.typeName.copy(nullable = false)
    val serializedValue = serializedValueGetter(CodeBlock.of("input as %T", deserializedType))
    val columnClass = generatedColumnClassName()
    val storageType = checkNotNull(serializedType.sqlStorageType)
    val parser = storageType.parserName(nullable = false)
    return CodeBlock
      .builder()
      .add("run {\n")
      .indent()
      .addStatement("val sqlValue = %L", serializedValue)
      .apply {
        when {
          storageType == SqlStorageType.STRING -> addStatement(
            "val stringValue = %T.quoteSqlStringLiteral(%L)",
            SQL_UTIL,
            CodeBlock
              .builder()
              .add("sqlValue")
              .apply {
                if (serializedTypeCanBeNull) {
                  add(" ?: throw %T(%S)", NullPointerException::class, "SQL argument cannot be null")
                }
              }
              .build()
          )
          serializedTypeCanBeNull -> add("val stringValue = sqlValue?.toString()\n")
            .indent()
            .addStatement("?: throw %T(%S)", NullPointerException::class, "SQL argument cannot be null")
            .unindent()
          else -> addStatement("val stringValue = sqlValue.toString()")
        }
      }
      .add(
        columnConstructor(
          returnType = returnType,
          columnClass = columnClass,
          parser = parser
        )
      )
      .unindent()
      .add("}\n")
      .build()
  }

  private fun TransformerElement.columnConstructor(
    returnType: TypeName,
    columnClass: ClassName,
    parser: String
  ) = CodeBlock
    .builder()
    .add("%T<%T, %T>(\n", columnClass, ANY, NOT_NULLABLE)
    .indent()
    .apply {
      when {
        isDefaultTransformer -> add("%T.ANONYMOUS_TABLE as %T<%T>,\n", TABLE, TABLE, ANY)
          .add("stringValue,\n")
          .add("%T.%N,\n", UTILS, parser)
          .add("false,\n")
          .add("null\n")
        else -> add("table = %T.ANONYMOUS_TABLE as %T<%T>,\n", TABLE, TABLE, ANY)
          .add("name = stringValue,\n")
          .add("valueParser = %T.%N,\n", UTILS, parser)
          .add("nullable = false,\n")
          .add("alias = null\n")
      }
    }
    .unindent()
    .add(") as %T\n", returnType)
    .build()

  private fun fallbackColumn(valueType: TypeName) = CodeBlock
    .builder()
    .add("%T<%T, %T, %T, %T, %T>(\n", COLUMN, valueType, valueType, valueType, ANY, NOT_NULLABLE)
    .indent()
    .add("%T.ANONYMOUS_TABLE as %T<%T>,\n", TABLE, TABLE, ANY)
    .addStatement("%T.quoteSqlStringLiteral(input.toString()),", SQL_UTIL)
    .add("false,\n")
    .add("%T.STRING_PARSER,\n", UTILS)
    .add("false,\n")
    .add("null\n")
    .unindent()
    .add(")")
    .build()

  private fun GeneratedDatabaseElement.databaseFunction(name: String) = FunSpec
    .builder(name)
    .addModifiers(if (isSubmodule) PUBLIC else OVERRIDE)
}

private fun submoduleFallback(
  nextFallback: CodeBlock,
  submodule: SubmoduleDatabaseMetadata
) = CodeBlock.of(
  "%T.%N(className = className, input = input) ?: %L",
  submodule.managerClassName,
  METHOD_COLUMN_FOR_VALUE_OR_NULL,
  nextFallback
)

private fun FunSpec.Builder.addRuntimeDebugLog(message: String) = apply {
  beginControlFlow("if (%T.LOGGING_ENABLED)", SQLITE_MAGIC)
  addStatement("%T.logDebug(%S)", LOG_UTIL, message)
  endControlFlow()
}

private fun FunSpec.Builder.addRuntimeErrorLog() = apply {
  beginControlFlow("if (%T.LOGGING_ENABLED)", SQLITE_MAGIC)
  addStatement("%T.logError(exception, %S)", LOG_UTIL, "Error while executing db transaction")
  endControlFlow()
}

private fun columnReturnType(
  valueType: TypeName
) = COLUMN.parameterizedBy(valueType, valueType, valueType, STAR, NOT_NULLABLE)
