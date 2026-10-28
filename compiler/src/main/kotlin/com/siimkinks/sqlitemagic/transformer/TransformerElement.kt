package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.WriterTypes.CHECK_NOT_NULL
import com.squareup.kotlinpoet.CodeBlock

private val SELECTOR_WORD_BOUNDARY = Regex("([a-z])([A-Z])")

internal data class TransformerElement(
  val deserializedType: TransformerTypeElement,
  val serializedType: TransformerTypeElement,
  val objectToDbValueMethod: TransformerMethodElement,
  val dbValueToObjectMethod: TransformerMethodElement,
  val encoderInputNullable: Boolean,
  val serializedTypeCanBeNull: Boolean,
  val decoderResultNullable: Boolean,
  val decoderInputNullable: Boolean
) {
  val typeKey get() = deserializedType.typeKey
  val transformerName get() = deserializedType.transformerName
  val sqlTypeSelectorName = transformerName
    .replace(
      regex = SELECTOR_WORD_BOUNDARY,
      replacement = "$1_$2"
    )
    .uppercase()
  val isDefaultTransformer
    get() = objectToDbValueMethod.ownerQualifiedName in DEFAULT_TRANSFORMERS ||
        dbValueToObjectMethod.ownerQualifiedName in DEFAULT_TRANSFORMERS

  fun serializedValueGetter(valueGetter: CodeBlock) =
    objectToDbValueMethod.callWithArgument(valueGetter)

  fun deserializedValueGetter(valueGetter: CodeBlock) =
    dbValueToObjectMethod.callWithArgument(valueGetter)

  fun requiredDeserializedValue(
    value: CodeBlock,
    propertyName: String
  ) = CodeBlock.of(
    "%M(%L) { %S }",
    CHECK_NOT_NULL,
    value,
    "Transformer ${dbValueToObjectMethod.qualifiedName} returned null for required property $propertyName"
  )
}

internal class TransformerRoundElement(
  additionalOriginatingFiles: Set<KSFile>
) {
  val originatingFiles: Set<KSFile>
    field = additionalOriginatingFiles.toMutableSet()

  var deserializedType: TransformerRoundTypeElement? = null
    private set
  var serializedType: TransformerRoundTypeElement? = null
    private set
  var objectToDbValueMethod: TransformerRoundMethodElement? = null
    private set
  var dbValueToObjectMethod: TransformerRoundMethodElement? = null
    private set

  val typeKey get() = deserializedType?.typeKey
  val isMissingMethods
    get() = objectToDbValueMethod == null || dbValueToObjectMethod == null
  val randomBlameSymbol
    get() = objectToDbValueMethod?.declaration ?: dbValueToObjectMethod?.declaration

  fun addObjectToDbValueMethod(method: TransformerRoundMethodElement) {
    objectToDbValueMethod = method
    method.declaration.containingFile?.let(originatingFiles::add)
    if (dbValueToObjectMethod == null) {
      deserializedType = method.firstParameterType
      serializedType = method.returnType
    }
  }

  fun addDbValueToObjectMethod(method: TransformerRoundMethodElement) {
    dbValueToObjectMethod = method
    method.declaration.containingFile?.let(originatingFiles::add)
    if (objectToDbValueMethod == null) {
      serializedType = method.firstParameterType
      deserializedType = method.returnType
    }
  }

  fun toTransformerElement(): TransformerElement {
    val deserializedType = checkNotNull(deserializedType)
    val serializedType = checkNotNull(serializedType)
    val objectToDbValueMethod = checkNotNull(objectToDbValueMethod)
    val dbValueToObjectMethod = checkNotNull(dbValueToObjectMethod)
    return TransformerElement(
      deserializedType = deserializedType.transformerTypeElement,
      serializedType = serializedType.transformerTypeElement,
      objectToDbValueMethod = objectToDbValueMethod.transformerMethodElement,
      dbValueToObjectMethod = dbValueToObjectMethod.transformerMethodElement,
      encoderInputNullable = checkNotNull(objectToDbValueMethod.firstParameterType).canBeNull,
      serializedTypeCanBeNull = serializedType.canBeNull,
      decoderResultNullable = checkNotNull(dbValueToObjectMethod.returnType).canBeNull,
      decoderInputNullable = checkNotNull(dbValueToObjectMethod.firstParameterType).canBeNull
    )
  }

  val missingMethodsErrorInfo: String
    get() {
      check(isMissingMethods) {
        "Transformer has no missing methods"
      }
      val missingMethod = when {
        objectToDbValueMethod == null -> "object-to-database-value"
        else -> "database-value-to-object"
      }
      return "For [${deserializedType?.typeKey}] there is missing valid $missingMethod method"
    }
}

internal fun Resolver.hasGeneratedTransformerDescriptor(transformer: TransformerElement) =
  getPropertyDeclarationByName(
    name = getKSNameFromString("$PACKAGE_ROOT.${transformer.sqlTypeSelectorName}"),
    includeTopLevel = true
  ) != null
