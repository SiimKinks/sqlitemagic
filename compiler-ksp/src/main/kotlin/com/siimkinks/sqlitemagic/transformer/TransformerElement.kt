package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.symbol.KSFile
import com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS
import com.squareup.kotlinpoet.CodeBlock

data class TransformerElement(
  val deserializedType: TransformerTypeElement,
  val serializedType: TransformerTypeElement,
  val objectToDbValueMethod: TransformerMethodElement,
  val dbValueToObjectMethod: TransformerMethodElement,
  val serializedTypeCanBeNull: Boolean
) {
  val typeKey get() = deserializedType.typeKey
  val transformerName get() = deserializedType.transformerName
  val isDefaultTransformer
    get() = objectToDbValueMethod.ownerQualifiedName in DEFAULT_TRANSFORMERS ||
        dbValueToObjectMethod.ownerQualifiedName in DEFAULT_TRANSFORMERS

  fun serializedValueGetter(valueGetter: CodeBlock) =
    objectToDbValueMethod.callWithArgument(valueGetter)

  fun deserializedValueGetter(valueGetter: CodeBlock) =
    dbValueToObjectMethod.callWithArgument(valueGetter)
}

class TransformerRoundElement private constructor(
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

  fun toTransformerElement() = TransformerElement(
    deserializedType = checkNotNull(deserializedType).transformerTypeElement,
    serializedType = checkNotNull(serializedType).transformerTypeElement,
    objectToDbValueMethod = checkNotNull(objectToDbValueMethod).transformerMethodElement,
    dbValueToObjectMethod = checkNotNull(dbValueToObjectMethod).transformerMethodElement,
    serializedTypeCanBeNull = checkNotNull(serializedType).canBeNull
  )

  fun missingMethodsErrorInfo(): String {
    check(isMissingMethods) {
      "Transformer has no missing methods"
    }
    val missingMethod = when {
      objectToDbValueMethod == null -> "object-to-database-value"
      else -> "database-value-to-object"
    }
    return "For [${deserializedType?.typeKey}] there is missing valid $missingMethod method"
  }

  companion object {
    fun fromObjectToDbValue(
      method: TransformerRoundMethodElement,
      additionalOriginatingFiles: Set<KSFile>
    ) = TransformerRoundElement(additionalOriginatingFiles).also {
      it.addObjectToDbValueMethod(method)
    }

    fun fromDbValueToObject(
      method: TransformerRoundMethodElement,
      additionalOriginatingFiles: Set<KSFile>
    ) = TransformerRoundElement(additionalOriginatingFiles).also {
      it.addDbValueToObjectMethod(method)
    }
  }
}
