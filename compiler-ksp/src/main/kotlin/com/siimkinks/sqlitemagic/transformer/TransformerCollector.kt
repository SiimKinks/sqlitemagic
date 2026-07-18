package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.element.TypeKey

internal class TransformerCollector(
  private val environment: Environment
) {
  private val validator = TransformerValidator(environment)
  private val duplicateTransformerTypeKeys = mutableSetOf<TypeKey>()
  private val transformers = linkedMapOf<TypeKey, TransformerRoundElement>()

  fun collect(
    objectToDbValueElements: List<KSFunctionDeclaration>,
    dbValueToObjectElements: List<KSFunctionDeclaration>,
    additionalOriginatingFiles: Set<KSFile> = emptySet()
  ) {
    for (objectToDbValue in objectToDbValueElements) {
      val objectToDbValueMethod = TransformerRoundMethodElement.from(objectToDbValue)
      val transformer = TransformerRoundElement.fromObjectToDbValue(
        method = objectToDbValueMethod,
        additionalOriginatingFiles = additionalOriginatingFiles
      )
      val deserializedType = transformer.deserializedType ?: continue
      val deserializedTypeKey = deserializedType.typeKey
      val existingTransformer = transformers[deserializedTypeKey]
      when {
        existingTransformer?.objectToDbValueMethod != null -> reportMultipleTransformers(
          typeKey = deserializedTypeKey,
          qualifiedName = deserializedType.qualifiedName,
          symbol = objectToDbValue
        )
        existingTransformer != null -> existingTransformer.addObjectToDbValueMethod(objectToDbValueMethod)
        else -> transformers[deserializedTypeKey] = transformer
      }
    }

    for (dbValueToObject in dbValueToObjectElements) {
      val dbValueToObjectMethod = TransformerRoundMethodElement.from(dbValueToObject)
      val collectedTransformer = TransformerRoundElement.fromDbValueToObject(
        method = dbValueToObjectMethod,
        additionalOriginatingFiles = additionalOriginatingFiles
      )
      val deserializedType = collectedTransformer.deserializedType ?: continue
      val deserializedTypeKey = deserializedType.typeKey
      val transformer = transformers[deserializedTypeKey]
      when {
        transformer?.dbValueToObjectMethod != null -> reportMultipleTransformers(
          typeKey = deserializedTypeKey,
          qualifiedName = deserializedType.qualifiedName,
          symbol = dbValueToObject
        )
        transformer != null -> transformer.addDbValueToObjectMethod(dbValueToObjectMethod)
        else -> transformers[deserializedTypeKey] = collectedTransformer
      }
    }
  }

  fun addToEnvironment(): Boolean {
    var isSuccessfulProcessing = duplicateTransformerTypeKeys.isEmpty()
    for (transformer in transformers.values) {
      when {
        !validator.isTransformerValid(transformer) -> isSuccessfulProcessing = false
        else -> environment.addTransformerElement(transformer)
      }
    }
    return isSuccessfulProcessing
  }

  private fun reportMultipleTransformers(
    typeKey: TypeKey,
    qualifiedName: String,
    symbol: KSFunctionDeclaration
  ) {
    duplicateTransformerTypeKeys += typeKey
    environment.logger.error(
      message = "Multiple transformers defined for $qualifiedName",
      symbol = symbol
    )
  }
}
