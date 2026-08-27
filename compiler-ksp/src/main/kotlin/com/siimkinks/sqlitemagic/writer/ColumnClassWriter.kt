package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_GET_FROM_CURSOR
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_GET_FROM_STATEMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_TO_SQL_ARG
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_DB_VALUE
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_SQL_VALUE
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_VALUE
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.CURSOR
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.SUPPORT_SQLITE_STATEMENT
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
import com.siimkinks.sqlitemagic.WriterTypes.UNCHECKED_CAST
import com.siimkinks.sqlitemagic.WriterTypes.UNIQUE
import com.siimkinks.sqlitemagic.WriterTypes.VALUE_PARSER
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.model.deserializedDeclaredIdValue
import com.siimkinks.sqlitemagic.model.serializedDeclaredIdValue
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo

internal class ColumnClassWriter private constructor(
  private val codeGenerator: CodeGenerator,
  private val className: ClassName,
  private val superClass: ClassName,
  private val deserializedType: TypeName,
  private val returnType: TypeName,
  private val equivalentType: TypeName,
  private val serializedType: TypeName,
  private val serializedValue: CodeBlock,
  private val serializedValueCanBeNull: Boolean,
  private val initBlock: CodeBlock?,
  private val parser: ParserData?,
  private val unique: Boolean,
  private val isInternal: Boolean
) {
  private val parentTableType = TypeVariableName("T")
  private val nullabilityType = TypeVariableName("N")

  fun write(originatingFiles: Set<KSFile>) = write(
    originatingFiles = OriginatingFiles(
      files = originatingFiles,
      isComplete = true
    )
  )

  fun write(originatingFiles: OriginatingFiles) {
    val columnClass = TypeSpec
      .classBuilder(className)
      .apply {
        if (isInternal) addModifiers(INTERNAL)
      }
      .addTypeVariables(listOf(parentTableType, nullabilityType))
      .primaryConstructor(constructor())
      .superclass(
        superClass.parameterizedBy(
          deserializedType,
          returnType,
          equivalentType,
          parentTableType,
          nullabilityType
        )
      )
      .addSuperclassConstructorParameter("table")
      .addSuperclassConstructorParameter("name")
      .addSuperclassConstructorParameter("false")
      .addSuperclassConstructorParameter("valueParser")
      .addSuperclassConstructorParameter("nullable")
      .addSuperclassConstructorParameter("%N", VARIABLE_ALIAS)
      .addFunction(toSqlArg())
      .addFunction(aliasOverride())
    parser?.let { parser ->
      columnClass
        .addFunction(
          parserOverride(
            functionName = METHOD_GET_FROM_CURSOR,
            parameterName = "cursor",
            parameterType = CURSOR,
            parser = parser
          )
        )
        .addFunction(
          parserOverride(
            functionName = METHOD_GET_FROM_STATEMENT,
            parameterName = "statement",
            parameterType = SUPPORT_SQLITE_STATEMENT,
            parser = parser
          )
        )
    }
    if (unique) {
      columnClass.addSuperinterface(UNIQUE.parameterizedBy(nullabilityType))
    }
    originatingFiles.files.forEach(columnClass::addOriginatingKSFile)
    FileSpec
      .builder(className)
      .addFileComment("%L", GENERATION_COMMENT)
      .addType(columnClass.build())
      .build()
      .writeTo(
        codeGenerator = codeGenerator,
        aggregating = !originatingFiles.isComplete,
        originatingKSFiles = originatingFiles.files
      )
  }

  private fun constructor() = FunSpec
    .constructorBuilder()
    .addModifiers(INTERNAL)
    .addParameter(name = "table", type = TABLE.parameterizedBy(parentTableType))
    .addParameter(name = "name", type = STRING)
    .addParameter(name = "valueParser", type = VALUE_PARSER)
    .addParameter(name = "nullable", type = BOOLEAN)
    .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
    .build()

  private fun toSqlArg(): FunSpec {
    val builder = FunSpec
      .builder(METHOD_TO_SQL_ARG)
      .addModifiers(OVERRIDE)
      .addParameter(name = VARIABLE_VALUE, type = deserializedType)
      .returns(STRING)
    initBlock?.let(builder::addCode)
    when {
      serializedValueCanBeNull -> builder
        .addStatement("val %N = %L", VARIABLE_SQL_VALUE, serializedValue)
        .beginControlFlow("if (%N == null)", VARIABLE_SQL_VALUE)
        .addStatement("throw %T(%S)", NullPointerException::class, "SQL argument cannot be null")
        .endControlFlow()
        .addSerializedValueReturn(CodeBlock.of("%N", VARIABLE_SQL_VALUE))
      else -> builder.addSerializedValueReturn(serializedValue)
    }
    return builder.build()
  }

  private fun FunSpec.Builder.addSerializedValueReturn(value: CodeBlock) = apply {
    when {
      serializedType == STRING -> addStatement("return %L", value)
      else -> addStatement("return %L.toString()", value)
    }
  }

  private fun aliasOverride(): FunSpec {
    val generatedType = className.parameterizedBy(parentTableType, nullabilityType)
    return FunSpec
      .builder(METHOD_AS)
      .addModifiers(OVERRIDE)
      .addParameter(name = VARIABLE_ALIAS, type = STRING)
      .returns(generatedType)
      .addStatement(
        "return %T(table, name, valueParser, nullable, %N)",
        generatedType,
        VARIABLE_ALIAS
      )
      .build()
  }

  private fun parserOverride(
    functionName: String,
    parameterName: String,
    parameterType: ClassName,
    parser: ParserData
  ): FunSpec {
    val returnType = TypeVariableName("V")
    return FunSpec
      .builder(functionName)
      .addAnnotation(UNCHECKED_CAST)
      .addModifiers(OVERRIDE)
      .addTypeVariable(returnType)
      .addParameter(name = parameterName, type = parameterType)
      .returns(returnType.copy(nullable = true))
      .addStatement(
        format = when {
          parser.acceptsNullDatabaseValue -> "val %N = super.%N<%T>(%N)"
          else -> "val %N = super.%N<%T>(%N) ?: return null"
        },
        VARIABLE_DB_VALUE,
        functionName,
        serializedType,
        parameterName
      )
      .addStatement(
        format = "return %L as %T",
        parser.deserializedValue,
        returnType.copy(nullable = true)
      )
      .build()
  }

  private data class ParserData(
    val deserializedValue: CodeBlock,
    val acceptsNullDatabaseValue: Boolean
  )

  companion object {
    fun from(
      transformerElement: TransformerElement,
      codeGenerator: CodeGenerator,
      createUniqueClass: Boolean
    ): ColumnClassWriter {
      val deserializedType = transformerElement.deserializedType.typeName.copy(nullable = false)
      val serializedType = transformerElement.serializedType.typeName.copy(nullable = false)
      return ColumnClassWriter(
        codeGenerator = codeGenerator,
        className = transformerElement.generatedColumnClassName(unique = createUniqueClass),
        superClass = when {
          transformerElement.serializedType.sqlStorageType?.isNumeric == true -> NUMERIC_COLUMN
          else -> COLUMN
        },
        deserializedType = deserializedType,
        returnType = deserializedType,
        equivalentType = deserializedType,
        serializedType = serializedType,
        serializedValue = transformerElement.serializedValueGetter(
          CodeBlock.of("%N", VARIABLE_VALUE)
        ),
        serializedValueCanBeNull = transformerElement.serializedTypeCanBeNull,
        initBlock = null,
        parser = ParserData(
          deserializedValue = transformerElement.deserializedValueGetter(
            CodeBlock.of("%N", VARIABLE_DB_VALUE)
          ),
          acceptsNullDatabaseValue = transformerElement.serializedTypeCanBeNull
        ),
        unique = createUniqueClass,
        isInternal = false
      )
    }

    fun fromRelationship(
      table: TableElement,
      column: ColumnElement,
      codeGenerator: CodeGenerator
    ): ColumnClassWriter {
      val relationship = checkNotNull(column.relationship)
      val transformer = relationship.referencedIdTransformer
      val idType = relationship.referencedIdType.typeName.copy(nullable = false)
      val serializedType = relationship.referencedIdSerializedType.typeName.copy(nullable = false)
      val databaseValueCanBeNull = relationship.databaseValueCanBeNull
      val databaseValue = CodeBlock.of("%N", VARIABLE_DB_VALUE)
      val deserializedValue = when {
        column.isNullable && databaseValueCanBeNull -> CodeBlock.of(
          "%L?.let { %L }",
          databaseValue,
          relationship.deserializedDeclaredIdValue(
            databaseValue = CodeBlock.of("it")
          )
        )
        else -> relationship.deserializedDeclaredIdValue(
          databaseValue = databaseValue,
          databaseValueCanBeNull = databaseValueCanBeNull
        )
      }
      return ColumnClassWriter(
        codeGenerator = codeGenerator,
        className = table.relationshipColumnClassName(column),
        superClass = when {
          column.sqlStorageType.isNumeric -> COMPLEX_NUMERIC_COLUMN
          else -> COMPLEX_COLUMN
        },
        deserializedType = idType,
        returnType = idType,
        equivalentType = column.equivalentType(declaredType = idType),
        serializedType = serializedType,
        serializedValue = relationship.serializedDeclaredIdValue(
          CodeBlock.of("%N", VARIABLE_VALUE)
        ),
        serializedValueCanBeNull = relationship.serializedValueCanBeNull,
        initBlock = null,
        parser = when {
          transformer != null || relationship.referencedIdRelationship != null -> ParserData(
            deserializedValue = deserializedValue,
            acceptsNullDatabaseValue = databaseValueCanBeNull
          )
          else -> null
        },
        unique = column.isUnique || column.isId,
        isInternal = !table.isPublic
      )
    }
  }
}
