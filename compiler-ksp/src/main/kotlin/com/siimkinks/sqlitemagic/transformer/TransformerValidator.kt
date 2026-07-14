package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.ClassKind.OBJECT
import com.google.devtools.ksp.symbol.FunctionKind.STATIC
import com.google.devtools.ksp.symbol.FunctionKind.TOP_LEVEL
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier.PRIVATE
import com.google.devtools.ksp.symbol.Modifier.SUSPEND
import com.siimkinks.sqlitemagic.Const.SUPPORTED_TRANSFORMER_DESERIALIZED_TYPES
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

class TransformerValidator(
  private val environment: Environment
) {
  fun isTransformerValid(transformer: TransformerRoundElement): Boolean {
    val blameSymbol = transformer.randomBlameSymbol
    if (transformer.isMissingMethods) {
      environment.logger.error(
        message = buildString {
          append("There must be 2 annotated transform methods callable without an instance -- ")
          append("@${ObjectToDbValue::class.simpleName} annotated method with single parameter which transforms objects to SQLite compatible objects and ")
          append("@${DbValueToObject::class.simpleName} annotated method with single parameter which transforms database values to objects.\n")
          append(transformer.missingMethodsErrorInfo())
        },
        symbol = blameSymbol
      )
      return false
    }

    val objectToDbValueMethod = checkNotNull(transformer.objectToDbValueMethod)
    val dbValueToObjectMethod = checkNotNull(transformer.dbValueToObjectMethod)
    if (objectToDbValueMethod.declaration == dbValueToObjectMethod.declaration) {
      environment.logger.error(
        message = "There must be 2 annotated transform methods; one method cannot serve both transformation directions",
        symbol = blameSymbol
      )
      return false
    }
    if (!isTransformerMethodValid(method = objectToDbValueMethod)) {
      return false
    }
    if (!isTransformerMethodValid(method = dbValueToObjectMethod)) {
      return false
    }
    if (!assertReturnTypeSameAsFirstParamType(firstMethod = objectToDbValueMethod, secondMethod = dbValueToObjectMethod)) {
      return false
    }
    if (!assertReturnTypeSameAsFirstParamType(firstMethod = dbValueToObjectMethod, secondMethod = objectToDbValueMethod)) {
      return false
    }

    val serializedType = transformer.serializedType
    if (serializedType?.declaration == null) {
      environment.logger.error(
        message = "Unsupported serialization type ${transformer.serializedType?.typeKey}",
        symbol = blameSymbol
      )
      return false
    }
    val deserializedType = transformer.deserializedType
    if (deserializedType?.declaration == null) {
      environment.logger.error(
        message = "Unsupported deserialization type ${transformer.deserializedType?.typeKey}",
        symbol = blameSymbol
      )
      return false
    }
    if (deserializedType.sqlStorageType != null) {
      environment.logger.error(
        message = "SQL types [${SUPPORTED_TRANSFORMER_DESERIALIZED_TYPES.joinToString()}] can't have transformers",
        symbol = blameSymbol
      )
      return false
    }
    if (serializedType.sqlStorageType == null) {
      environment.logger.error(
        message = "Provided serialization type was ${serializedType.typeName}, but serialized type must be one of supported SQLite types - " +
            SUPPORTED_TRANSFORMER_DESERIALIZED_TYPES.joinToString(),
        symbol = blameSymbol
      )
      return false
    }
    val existingTransformer = environment.getTransformerFor(deserializedType)
    if (existingTransformer != null && existingTransformer != transformer.toTransformerElement()) {
      environment.logger.error(
        message = "Multiple transformers defined for ${deserializedType.qualifiedName}",
        symbol = blameSymbol
      )
      return false
    }
    if (deserializedType.declaration.isAnnotationPresent(Table::class)) {
      environment.logger.error(
        message = "Cannot transform object ${deserializedType.qualifiedName} which is also annotated with @Table. Delete transformer or remove annotation",
        symbol = blameSymbol
      )
      return false
    }
    return true
  }

  private fun isTransformerMethodValid(method: TransformerRoundMethodElement): Boolean {
    if (!method.hasSingleParameter) {
      environment.logger.error(
        message = "Transformer methods must have one parameter",
        symbol = method.declaration
      )
      return false
    }
    if (method.declaration.extensionReceiver != null) {
      environment.logger.error(
        message = "Transformer methods must not be extension functions",
        symbol = method.declaration
      )
      return false
    }
    if (SUSPEND in method.declaration.modifiers) {
      environment.logger.error(
        message = "Transformer methods must not be suspend functions",
        symbol = method.declaration
      )
      return false
    }
    if (PRIVATE in method.declaration.modifiers) {
      environment.logger.error(
        message = "Transformer methods must not be private",
        symbol = method.declaration
      )
      return false
    }
    if (!method.declaration.isTransformerCallableWithoutInstance()) {
      environment.logger.error(
        message = "Transformer methods must be top-level, declared in an object or companion object, or Java static",
        symbol = method.declaration
      )
      return false
    }
    return true
  }

  private fun assertReturnTypeSameAsFirstParamType(
    firstMethod: TransformerRoundMethodElement,
    secondMethod: TransformerRoundMethodElement
  ): Boolean {
    val firstParamType = firstMethod.firstParameterType
    val returnType = secondMethod.returnType
    if (returnType == null || firstParamType?.type != returnType.type) {
      environment.logger.error(
        message = "One transformer method's return type must be the same as other method's first parameter and vice versa",
        symbol = firstMethod.declaration
      )
      return false
    }
    return true
  }
}

private fun KSFunctionDeclaration.isTransformerCallableWithoutInstance(): Boolean {
  if (functionKind == TOP_LEVEL || functionKind == STATIC) {
    return true
  }
  val parentClass = parentDeclaration as? KSClassDeclaration ?: return false
  return parentClass.classKind == OBJECT || parentClass.isCompanionObject
}
