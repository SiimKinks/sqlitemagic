package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.CLASS_MEMBER
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.TOP_LEVEL
import com.siimkinks.sqlitemagic.transformer.TransformerCallableKind.UNKNOWN
import com.siimkinks.sqlitemagic.utils.typeParameterResolver
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toClassName

enum class TransformerCallableKind {
  TOP_LEVEL,
  CLASS_MEMBER,
  UNKNOWN
}

interface TransformerMethodElement {
  val methodName: String
  val packageName: String
  val ownerClassName: ClassName?
  val callableKind: TransformerCallableKind

  val ownerQualifiedName get() = ownerClassName?.canonicalName

  fun callWithArgument(argument: CodeBlock) = when (callableKind) {
    TOP_LEVEL -> CodeBlock.of(
      "%M(%L)",
      MemberName(
        packageName = packageName,
        simpleName = methodName
      ),
      argument
    )
    CLASS_MEMBER -> CodeBlock.of(
      "%T.%N(%L)",
      checkNotNull(ownerClassName),
      methodName,
      argument
    )
    else -> error("Unsupported transformer callable kind $callableKind")
  }
}

data class TransformerMethodElementImpl(
  override val methodName: String,
  override val packageName: String,
  override val ownerClassName: ClassName?,
  override val callableKind: TransformerCallableKind
) : TransformerMethodElement

@ConsistentCopyVisibility
data class TransformerRoundMethodElement private constructor(
  val declaration: KSFunctionDeclaration,
  val transformerMethodElement: TransformerMethodElement,
  val firstParameterType: TransformerRoundTypeElement?,
  val returnType: TransformerRoundTypeElement?
) : TransformerMethodElement by transformerMethodElement {
  val hasSingleParameter get() = declaration.parameters.size == 1

  companion object {
    fun from(method: KSFunctionDeclaration): TransformerRoundMethodElement {
      val typeParameterResolver = method.typeParameterResolver()
      return TransformerRoundMethodElement(
        declaration = method,
        transformerMethodElement = TransformerMethodElementImpl(
          methodName = method.simpleName.asString(),
          packageName = method.packageName.asString(),
          ownerClassName = (method.parentDeclaration as? KSClassDeclaration)
            ?.toClassName(),
          callableKind = when (method.parentDeclaration) {
            null -> TOP_LEVEL
            is KSClassDeclaration -> CLASS_MEMBER
            else -> UNKNOWN
          }
        ),
        firstParameterType = method.parameters
          .firstOrNull()
          ?.type
          ?.toTransformerRoundTypeElement(typeParameterResolver),
        returnType = method.returnType
          ?.toTransformerRoundTypeElement(typeParameterResolver)
      )
    }
  }
}
