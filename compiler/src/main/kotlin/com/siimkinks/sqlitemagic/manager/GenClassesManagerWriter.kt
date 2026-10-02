package com.siimkinks.sqlitemagic.manager

import com.google.devtools.ksp.processing.CodeGenerator
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_GENERATED_VIEW
import com.siimkinks.sqlitemagic.GeneratedNames.FIELD_TABLE_SCHEMA
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_COLLECT_GENERATED_VIEWS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_COLUMN_FOR_VALUE_OR_NULL
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_SCHEMA_INDEXES
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_CREATE_SCHEMA_TABLES
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_SQL_VALUE
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
import com.siimkinks.sqlitemagic.WriterTypes.BOOLEAN_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_DATABASE
import com.siimkinks.sqlitemagic.WriterTypes.GENERATED_VIEW
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
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.parserName
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.view.ViewElement
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.MUTABLE_LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo

internal class GenClassesManagerWriter(
  private val codeGenerator: CodeGenerator
) {
  fun write(
    database: GeneratedDatabaseElement,
    orderedTables: CreationOrderedTables
  ) {
    val orderedIndexes = orderedTables.sortedIndexes(database.indices)
    val genClassesManager = with(database) {
      when {
        isSubmodule -> TypeSpec.objectBuilder(className)
        else -> TypeSpec.classBuilder(className)
          .addSuperinterface(GENERATED_DATABASE)
      }
        .addModifiers(PUBLIC)
        .addFunction(configureDatabase())
        .addFunction(createSchemaTables(orderedTables))
        .addFunction(collectGeneratedViews())
        .addFunction(createSchemaIndexes(orderedIndexes))
        .addFunction(
          createSchema(
            tables = orderedTables.persistent,
            views = database.views.filter { it.schema == MAIN },
            indexes = orderedIndexes.filter { it.schema == MAIN }
          )
        )
        .addFunction(
          createTemporarySchema(
            tables = orderedTables.temporary,
            views = database.views.filter { it.schema == TEMPORARY },
            indexes = orderedIndexes.filter { it.schema == TEMPORARY }
          )
        )
        .addFunction(clearData())
        .addFunction(migrateViews())
        .addFunction(getNrOfTables())
        .addTablePositions(database)
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
          .initializer("%L", table.declarationOrder)
          .build()
      )
    }
    if (!database.isSubmodule) addType(positionOwner.build())
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

  private fun GeneratedDatabaseElement.createSchema(
    tables: List<TableElement>,
    views: List<ViewElement>,
    indexes: List<IndexElement>
  ) = schemaCreationFunction(
    functionName = METHOD_CREATE_SCHEMA,
    tables = tables,
    views = views,
    indexes = indexes,
    temporary = false
  )

  private fun GeneratedDatabaseElement.createTemporarySchema(
    tables: List<TableElement>,
    views: List<ViewElement>,
    indexes: List<IndexElement>
  ) = schemaCreationFunction(
    functionName = METHOD_CREATE_TEMPORARY_SCHEMA,
    tables = tables,
    views = views,
    indexes = indexes,
    temporary = true
  )

  private fun GeneratedDatabaseElement.schemaCreationFunction(
    functionName: String,
    tables: List<TableElement>,
    views: List<ViewElement>,
    indexes: List<IndexElement>,
    temporary: Boolean
  ): FunSpec {
    val builder = databaseFunction(functionName)
      .addParameter(name = "db", type = SQLITE_DATABASE)
    if (tables.isEmpty() && views.isEmpty() && indexes.isEmpty() && submodules.isEmpty()) {
      return builder
        .addStatement("return Unit")
        .build()
    }
    return builder
      .addStatement("db.beginTransaction()")
      .beginControlFlow("try")
      .addStatement("%N(db = db, temporary = %L)", METHOD_CREATE_SCHEMA_TABLES, temporary)
      .addStatement("val generatedViews = mutableListOf<%T>()", GENERATED_VIEW)
      .addStatement("%N(views = generatedViews)", METHOD_COLLECT_GENERATED_VIEWS)
      .beginControlFlow("if (generatedViews.isNotEmpty())")
      .addRuntimeDebugLog("Creating views")
      .addStatement(
        "%T.createViews(db = db, views = generatedViews, temporary = %L)",
        SQL_UTIL,
        temporary
      )
      .endControlFlow()
      .addStatement("%N(db = db, temporary = %L)", METHOD_CREATE_SCHEMA_INDEXES, temporary)
      .addStatement("db.setTransactionSuccessful()")
      .nextControlFlow("catch (exception: %T)", Exception::class)
      .addRuntimeErrorLog()
      .addStatement("throw exception")
      .nextControlFlow("finally")
      .addStatement("db.endTransaction()")
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.createSchemaTables(
    orderedTables: CreationOrderedTables
  ) = FunSpec
    .builder(METHOD_CREATE_SCHEMA_TABLES)
    .addModifiers(PUBLIC)
    .addParameter(name = "db", type = SQLITE_DATABASE)
    .addParameter(name = "temporary", type = BOOLEAN)
    .apply {
      submodules.forEach { submodule ->
        addStatement(
          "%T.%N(db = db, temporary = temporary)",
          submodule.managerClassName,
          METHOD_CREATE_SCHEMA_TABLES
        )
      }
    }
    .beginControlFlow("if (temporary)")
    .addLocalTables(
      tables = orderedTables.temporary,
      logMessage = "Creating temporary tables"
    )
    .nextControlFlow("else")
    .addLocalTables(
      tables = orderedTables.persistent,
      logMessage = "Creating tables"
    )
    .endControlFlow()
    .build()

  private fun FunSpec.Builder.addLocalTables(
    tables: List<TableElement>,
    logMessage: String
  ) = apply {
    if (tables.isNotEmpty()) {
      addRuntimeDebugLog(logMessage)
      tables.forEach { table ->
        addStatement("db.execSQL(%T.%N)", table.generationNames.adapterClassName, FIELD_TABLE_SCHEMA)
      }
    }
  }

  private fun GeneratedDatabaseElement.collectGeneratedViews() = FunSpec
    .builder(METHOD_COLLECT_GENERATED_VIEWS)
    .addModifiers(PUBLIC)
    .addParameter(name = "views", type = MUTABLE_LIST.parameterizedBy(GENERATED_VIEW))
    .apply {
      submodules.forEach { submodule ->
        addStatement(
          "%T.%N(views = views)",
          submodule.managerClassName,
          METHOD_COLLECT_GENERATED_VIEWS
        )
      }
    }
    .addLocalViews(views)
    .build()

  private fun FunSpec.Builder.addLocalViews(views: List<ViewElement>) = apply {
    views.forEach { view ->
      addStatement("views.add(%T.%N)", view.generationNames.daoClassName, FIELD_GENERATED_VIEW)
    }
  }

  private fun GeneratedDatabaseElement.createSchemaIndexes(indexes: List<IndexElement>) = indexes
    .partition { it.schema == TEMPORARY }
    .let { (temporaryIndexes, persistentIndexes) ->
      FunSpec
        .builder(METHOD_CREATE_SCHEMA_INDEXES)
        .addModifiers(PUBLIC)
        .addParameter(name = "db", type = SQLITE_DATABASE)
        .addParameter(name = "temporary", type = BOOLEAN)
        .apply {
          submodules.forEach { submodule ->
            addStatement(
              "%T.%N(db = db, temporary = temporary)",
              submodule.managerClassName,
              METHOD_CREATE_SCHEMA_INDEXES
            )
          }
        }
        .beginControlFlow("if (temporary)")
        .addLocalIndexes(temporaryIndexes)
        .nextControlFlow("else")
        .addLocalIndexes(persistentIndexes)
        .endControlFlow()
        .build()
    }

  private fun FunSpec.Builder.addLocalIndexes(indexes: List<IndexElement>) = apply {
    if (indexes.isNotEmpty()) {
      addRuntimeDebugLog("Creating indexes")
      indexes.forEach { index ->
        addStatement("db.execSQL(%S)", index.createSql())
      }
    }
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

  private fun GeneratedDatabaseElement.migrateViews(): FunSpec {
    val persistentViews = views.filter { it.schema == MAIN }
    val builder = databaseFunction(METHOD_MIGRATE_VIEWS)
      .addParameter(name = "db", type = SQLITE_DATABASE)
    if (submodules.isEmpty() && persistentViews.isEmpty()) {
      return builder
        .addStatement("return Unit")
        .build()
    }
    return builder
      .beginControlFlow("try")
      .addStatement("val generatedViews = mutableListOf<%T>()", GENERATED_VIEW)
      .addStatement("%N(views = generatedViews)", METHOD_COLLECT_GENERATED_VIEWS)
      .beginControlFlow("if (generatedViews.isNotEmpty())")
      .addRuntimeDebugLog("Migrating views")
      .addStatement("%T.recreateViews(db = db, views = generatedViews)", SQL_UTIL)
      .endControlFlow()
      .nextControlFlow("catch (exception: %T)", Exception::class)
      .addRuntimeErrorLog()
      .addStatement("throw exception")
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.getNrOfTables(): FunSpec {
    val builder = databaseFunction(METHOD_GET_NR_OF_TABLES)
      .addParameter(name = "moduleName", type = STRING.copy(nullable = true))
      .returns(INT)
    if (submodules.isEmpty()) {
      return builder.addStatement("return %L", tables.size).build()
    }
    val total = buildCodeBlock {
      add("%L", tables.size)
      submodules.forEach { submodule ->
        add(" + %T.%N(null)", submodule.managerClassName, METHOD_GET_NR_OF_TABLES)
      }
    }
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
            submodules.joinToCode { CodeBlock.of("%S", it.moduleName) }
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
    return databaseFunction(METHOD_COLUMN_FOR_VALUE)
      .addAnnotation(UNCHECKED_CAST)
      .addTypeVariable(valueType)
      .addParameter(name = "input", type = valueType)
      .returns(returnType)
      .addStatement("val className = input::class.qualifiedName")
      .beginControlFlow("return when (className)")
      .addCode(transformerBranches(returnType = returnType, includeDefaults = true))
      .addStatement(
        "else -> %L",
        submodules
          .asReversed()
          .fold(
            initial = fallbackColumn(valueType),
            operation = ::submoduleFallback
          )
      )
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.columnForValueOrNull(): FunSpec {
    val valueType = TypeVariableName("V", ANY)
    val returnType = columnReturnType(valueType)
    return databaseFunction(METHOD_COLUMN_FOR_VALUE_OR_NULL)
      .addAnnotation(UNCHECKED_CAST)
      .addTypeVariable(valueType)
      .addParameter(name = "className", type = STRING.copy(nullable = true))
      .addParameter(name = "input", type = valueType)
      .returns(returnType.copy(nullable = true))
      .beginControlFlow("return when (className)")
      .addCode(transformerBranches(returnType = returnType, includeDefaults = false))
      .addStatement("else -> null")
      .endControlFlow()
      .build()
  }

  private fun GeneratedDatabaseElement.transformerBranches(
    returnType: TypeName,
    includeDefaults: Boolean
  ) = buildCodeBlock {
    transformers
      .asSequence()
      .filter { includeDefaults || !it.isDefaultTransformer }
      .groupBy { it.deserializedType.qualifiedName }
      .forEach { (qualifiedName, matchingTransformers) ->
        add("%S -> ", qualifiedName)
        when {
          matchingTransformers.size > 1 -> addStatement(
            "throw %T(%S)",
            UnsupportedOperationException::class,
            "Unable to disambiguate transformer for $qualifiedName"
          )
          else -> add(
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
    return buildCodeBlock {
      beginControlFlow("run")
      addStatement("val %N = %L", VARIABLE_SQL_VALUE, serializedValue)
      when {
        storageType == SqlStorageType.STRING -> addStatement(
          "val stringValue = %T.quoteSqlStringLiteral(%L)",
          SQL_UTIL,
          buildCodeBlock {
            add("%N", VARIABLE_SQL_VALUE)
            if (serializedTypeCanBeNull) {
              add(" ?: throw %T(%S)", NullPointerException::class, "SQL argument cannot be null")
            }
          }
        )
        serializedTypeCanBeNull -> addStatement(
          "val stringValue = %N?.toString() ?: throw %T(%S)",
          VARIABLE_SQL_VALUE,
          NullPointerException::class,
          "SQL argument cannot be null"
        )
        else -> addStatement("val stringValue = %N.toString()", VARIABLE_SQL_VALUE)
      }
      addStatement(
        "%T(table = %T.ANONYMOUS_TABLE as %T, name = stringValue, valueParser = %T.%N, nullable = false, alias = null) as %T",
        when {
          isDefaultTransformer -> BOOLEAN_COLUMN
          else -> columnClass
        }.parameterizedBy(ANY, NOT_NULLABLE),
        TABLE,
        TABLE.parameterizedBy(ANY),
        UTILS,
        parser,
        returnType
      )
      endControlFlow()
    }
  }

  private fun fallbackColumn(valueType: TypeName): CodeBlock {
    val columnType = COLUMN.parameterizedBy(valueType, valueType, valueType, ANY, NOT_NULLABLE)
    val tableType = TABLE.parameterizedBy(ANY)
    return CodeBlock.of(
      "%T(table = %T.ANONYMOUS_TABLE as %T, name = %T.quoteSqlStringLiteral(input.toString()), valueParser = %T.STRING_PARSER, nullable = false, alias = null)",
      columnType,
      TABLE,
      tableType,
      SQL_UTIL,
      UTILS
    )
  }

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
