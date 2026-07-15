package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.NameConst.METHOD_AS
import com.siimkinks.sqlitemagic.NameConst.METHOD_GET_FROM_CURSOR
import com.siimkinks.sqlitemagic.NameConst.METHOD_GET_FROM_STATEMENT
import com.siimkinks.sqlitemagic.NameConst.METHOD_TO_SQL_ARG
import com.siimkinks.sqlitemagic.NameConst.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.WriterUtil.COLUMN
import com.siimkinks.sqlitemagic.WriterUtil.CURSOR
import com.siimkinks.sqlitemagic.WriterUtil.DB_VALUE_VARIABLE
import com.siimkinks.sqlitemagic.WriterUtil.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterUtil.SQL_VALUE_VARIABLE
import com.siimkinks.sqlitemagic.WriterUtil.SUPPORT_SQLITE_STATEMENT
import com.siimkinks.sqlitemagic.WriterUtil.TABLE
import com.siimkinks.sqlitemagic.WriterUtil.UNCHECKED_CAST
import com.siimkinks.sqlitemagic.WriterUtil.UNIQUE
import com.siimkinks.sqlitemagic.WriterUtil.VALUE_PARSER
import com.siimkinks.sqlitemagic.WriterUtil.VALUE_VARIABLE
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
  private val unique: Boolean
) {
  private val parentTableType = TypeVariableName("T")
  private val nullabilityType = TypeVariableName("N")

  fun write(originatingFiles: Set<KSFile>) {
    val typeBuilder = TypeSpec
      .classBuilder(className)
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
      .addSuperclassConstructorParameter("alias")
      .addFunction(toSqlArg())
      .addFunction(aliasOverride())
    parser?.let { parser ->
      typeBuilder
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
      typeBuilder.addSuperinterface(UNIQUE.parameterizedBy(nullabilityType))
    }
    originatingFiles.forEach(typeBuilder::addOriginatingKSFile)
    FileSpec
      .builder(
        packageName = className.packageName,
        fileName = className.simpleName
      )
      .addFileComment("%L", GENERATION_COMMENT)
      .addType(typeBuilder.build())
      .build()
      .writeTo(
        codeGenerator = codeGenerator,
        aggregating = false
      )
  }

  private fun constructor() = FunSpec
    .constructorBuilder()
    .addModifiers(INTERNAL)
    .addParameter(name = "table", type = TABLE.parameterizedBy(parentTableType))
    .addParameter(name = "name", type = STRING)
    .addParameter(name = "valueParser", type = VALUE_PARSER)
    .addParameter(name = "nullable", type = BOOLEAN)
    .addParameter(name = "alias", type = STRING.copy(nullable = true))
    .build()

  private fun toSqlArg(): FunSpec {
    val builder = FunSpec
      .builder(METHOD_TO_SQL_ARG)
      .addModifiers(OVERRIDE)
      .addParameter(name = VALUE_VARIABLE, type = deserializedType)
      .returns(STRING)
    initBlock?.let(builder::addCode)
    when {
      serializedValueCanBeNull -> builder
        .addStatement("val %N = %L", SQL_VALUE_VARIABLE, serializedValue)
        .beginControlFlow("if (%N == null)", SQL_VALUE_VARIABLE)
        .addStatement("throw %T(%S)", NullPointerException::class, "SQL argument cannot be null")
        .endControlFlow()
        .addSerializedValueReturn(CodeBlock.of("%N", SQL_VALUE_VARIABLE))
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
      .addParameter(name = "alias", type = STRING)
      .returns(generatedType)
      .addStatement(
        "return %T(table, name, valueParser, nullable, alias)",
        generatedType
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
        DB_VALUE_VARIABLE,
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
      val className = transformerElement.transformerName + COLUMN.simpleName
      return ColumnClassWriter(
        codeGenerator = codeGenerator,
        className = ClassName(
          PACKAGE_ROOT,
          when {
            createUniqueClass -> "Unique$className"
            else -> className
          }
        ),
        superClass = when {
          transformerElement.serializedType.sqlStorageType?.isNumeric == true -> NUMERIC_COLUMN
          else -> COLUMN
        },
        deserializedType = deserializedType,
        returnType = deserializedType,
        equivalentType = deserializedType,
        serializedType = serializedType,
        serializedValue = transformerElement.serializedValueGetter(
          CodeBlock.of("%N", VALUE_VARIABLE)
        ),
        serializedValueCanBeNull = transformerElement.serializedTypeCanBeNull,
        initBlock = null,
        parser = ParserData(
          deserializedValue = transformerElement.deserializedValueGetter(
            CodeBlock.of("%N", DB_VALUE_VARIABLE)
          ),
          acceptsNullDatabaseValue = transformerElement.serializedTypeCanBeNull
        ),
        unique = createUniqueClass
      )
    }
  }
}
