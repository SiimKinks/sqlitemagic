package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.METHOD_AS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_ALIAS
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_DB_VALUE
import com.siimkinks.sqlitemagic.GeneratedNames.VARIABLE_VALUE
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COLUMN_VALUE_ADAPTER
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.COMPLEX_NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.NUMERIC_COLUMN
import com.siimkinks.sqlitemagic.WriterTypes.TABLE
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
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.withIndent

internal class ColumnClassWriter private constructor(
  private val codeGenerator: CodeGenerator,
  private val className: ClassName,
  private val superClass: ClassName,
  private val deserializedType: TypeName,
  private val returnType: TypeName,
  private val equivalentType: TypeName,
  private val serializedType: TypeName,
  private val serializer: CodeBlock,
  private val parser: ParserData?,
  private val unique: Boolean,
  private val isInternal: Boolean,
  private val isConstructorInternal: Boolean
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
      .addSuperclassConstructorParameter(
        "%L",
        buildCodeBlock {
          add("\n")
          withIndent {
            add(
              superConstructorArguments()
                .joinToCode(separator = ",\n")
            )
          }
          add("\n")
        }
      )
      .addProperty(constructorProperty(name = "table", type = TABLE.parameterizedBy(parentTableType)))
      .addProperty(constructorProperty(name = "name", type = STRING))
      .addProperty(
        constructorProperty(
          name = "valueParser",
          type = VALUE_PARSER.rawType.parameterizedBy(serializedType)
        )
      )
      .addProperty(constructorProperty(name = "nullable", type = BOOLEAN))
      .addFunction(aliasOverride())
      .apply {
        if (isInternal) addModifiers(INTERNAL)
        if (unique) {
          addSuperinterface(UNIQUE.parameterizedBy(nullabilityType))
        }
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
    .apply {
      if (isConstructorInternal) addModifiers(INTERNAL)
    }
    .addParameter(name = "table", type = TABLE.parameterizedBy(parentTableType))
    .addParameter(name = "name", type = STRING)
    .addParameter(name = "valueParser", type = VALUE_PARSER.rawType.parameterizedBy(serializedType))
    .addParameter(name = "nullable", type = BOOLEAN)
    .addParameter(name = VARIABLE_ALIAS, type = STRING.copy(nullable = true))
    .build()

  private fun constructorProperty(name: String, type: TypeName) = PropertySpec
    .builder(name, type, PRIVATE)
    .initializer("%N", name)
    .build()

  private fun superConstructorArguments() = listOf(
    CodeBlock.of("table = %N", "table"),
    CodeBlock.of("name = %N", "name"),
    CodeBlock.of("valueParser = %N", "valueParser"),
    CodeBlock.of("nullable = %N", "nullable"),
    CodeBlock.of("alias = %N", VARIABLE_ALIAS),
    valueAdapterArgument()
  )

  private fun valueAdapterArgument() = buildCodeBlock {
    add(
      "valueAdapter = %T.%L(\n",
      COLUMN_VALUE_ADAPTER,
      when {
        parser == null -> "serializing"
        parser.acceptsNullDatabaseValue -> "transformedNullableInput"
        else -> "transformed"
      }
    )
    withIndent {
      add(
        listOfNotNull(
          CodeBlock.of("parser = valueParser"),
          CodeBlock.of("toDb = %L", serializer),
          when {
            parser == null -> null
            else -> CodeBlock.of("fromDb = %L", parser.deserializer)
          }
        ).joinToCode(separator = ",\n")
      )
    }
    add("\n)")
  }

  private fun aliasOverride(): FunSpec {
    val generatedType = className.parameterizedBy(parentTableType, nullabilityType)
    return FunSpec
      .builder(METHOD_AS)
      .addModifiers(OVERRIDE)
      .addParameter(name = VARIABLE_ALIAS, type = STRING)
      .returns(generatedType)
      .addCode(
        buildCodeBlock {
          add("return %T(\n", generatedType)
          withIndent {
            add("table = %N,\n", "table")
            add("name = %N,\n", "name")
            add("valueParser = %N,\n", "valueParser")
            add("nullable = %N,\n", "nullable")
            add("alias = %N\n", VARIABLE_ALIAS)
          }
          add(")\n")
        }
      )
      .build()
  }

  private data class ParserData(
    val deserializer: CodeBlock,
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
        serializer = transformerElement.objectToDbValueMethod.callableReference(),
        parser = ParserData(
          deserializer = transformerElement.dbValueToObjectMethod.callableReference(),
          acceptsNullDatabaseValue = transformerElement.serializedTypeCanBeNull
        ),
        unique = createUniqueClass,
        isInternal = false,
        isConstructorInternal = false
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
        serializer = CodeBlock.of(
          "{ %N -> %L }",
          VARIABLE_VALUE,
          relationship.serializedDeclaredIdValue(CodeBlock.of("%N", VARIABLE_VALUE))
        ),
        parser = when {
          transformer != null || relationship.referencedIdRelationship != null -> ParserData(
            deserializer = CodeBlock.of("{ %N -> %L }", VARIABLE_DB_VALUE, deserializedValue),
            acceptsNullDatabaseValue = databaseValueCanBeNull
          )
          else -> null
        },
        unique = column.isUnique || column.isId,
        isInternal = !table.isPublic,
        isConstructorInternal = true
      )
    }
  }
}
