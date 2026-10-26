package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.GENERATION_COMMENT
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.WriterTypes.ENCODED_SQL_EXPRESSION
import com.siimkinks.sqlitemagic.WriterTypes.ENCODING_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.NULLABLE_READ_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.OPT_IN_INTERNAL_API
import com.siimkinks.sqlitemagic.WriterTypes.REQUIRED_READ_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_STORAGE
import com.siimkinks.sqlitemagic.WriterTypes.SQL_TYPE
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.siimkinks.sqlitemagic.utils.callCode
import com.siimkinks.sqlitemagic.utils.namedArgument
import com.siimkinks.sqlitemagic.writer.parameterEncodingPolicy
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ksp.writeTo

internal class TransformerSqlTypeWriter(
  private val transformer: TransformerElement,
  private val codeGenerator: CodeGenerator
) {
  private val hostType = transformer.deserializedType.typeName.copy(nullable = false)
  private val encoderInputType = hostType.copy(nullable = transformer.encoderInputNullable)
  private val resultType = hostType.copy(nullable = transformer.decoderResultNullable)
  private val descriptorType = ENCODING_TYPE
    .parameterizedBy(
      encoderInputType,
      resultType,
      nullnessType(transformer.serializedTypeCanBeNull),
      nullnessType(transformer.decoderInputNullable)
    )
  private val selector = transformer.sqlTypeSelectorName
  private val backingName = "${transformer.transformerName}SqlType"

  fun write(originatingFiles: Set<KSFile>) {
    FileSpec
      .builder(packageName = PACKAGE_ROOT, fileName = backingName)
      .addFileComment("%L", GENERATION_COMMENT)
      .addAnnotation(OPT_IN_INTERNAL_API)
      .addProperty(descriptorBackingProperty())
      .addProperty(selectorProperty())
      .addFunction(asValueConversion(nullable = false))
      .addFunction(asValueConversion(nullable = true))
      .build()
      .writeTo(
        codeGenerator = codeGenerator,
        aggregating = false,
        originatingKSFiles = originatingFiles
      )
  }

  private fun descriptorBackingProperty() = PropertySpec
    .builder(name = backingName, type = descriptorType)
    .addModifiers(PRIVATE)
    .initializer(descriptorInitializer())
    .build()

  private fun selectorProperty() = PropertySpec
    .builder(name = selector, type = descriptorType)
    .receiver(SQL_TYPE)
    .getter(
      FunSpec.getterBuilder()
        .addStatement("return %N", backingName)
        .build()
    )
    .build()

  private fun descriptorInitializer() = callCode(
    callee = CodeBlock.of(
      "%M",
      when {
        transformer.decoderInputNullable -> NULLABLE_READ_TYPE
        else -> REQUIRED_READ_TYPE
      }
    ),
    arguments = listOf(
      namedArgument("storage", "%T.%N", SQL_STORAGE, checkNotNull(transformer.serializedType.sqlStorageType).name),
      namedArgument("encode", "%L", transformer.objectToDbValueMethod.callableReference()),
      namedArgument("decode", "%L", transformer.dbValueToObjectMethod.callableReference()),
      namedArgument("encodingNullness", "%T", nullnessType(transformer.serializedTypeCanBeNull))
    )
  )

  private fun asValueConversion(nullable: Boolean): FunSpec {
    val encoding = parameterEncodingPolicy(
      inputNullable = nullable,
      encoderInputNullable = transformer.encoderInputNullable,
      encodingNullable = transformer.serializedTypeCanBeNull
    )
    val receiverType = hostType.copy(nullable = nullable)
    val parameterResultType = encoding.parameterResultType(resultType)
    return FunSpec.builder("asValue")
      .receiver(receiverType)
      .returns(
        ENCODED_SQL_EXPRESSION
          .parameterizedBy(
            receiverType,
            resultType,
            encoding.nullness,
            parameterResultType
          )
      )
      .addAnnotation(
        AnnotationSpec.builder(JvmName::class)
          .addMember(
            "%S",
            when {
              nullable -> "nullable${transformer.transformerName}AsValue"
              else -> "${transformer.transformerName}AsValue"
            }
          )
          .build()
      )
      .addStatement("return asValue(%T.%N.%M())", SQL_TYPE, selector, encoding.factory)
      .build()
  }
}
