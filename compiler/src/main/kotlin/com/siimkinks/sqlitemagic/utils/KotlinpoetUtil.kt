package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.tag
import com.squareup.kotlinpoet.tags.TypeAliasTag
import com.squareup.kotlinpoet.withIndent

internal fun TypeName.typeKey() = canonicalTypeName(isRoot = true).toString()

internal fun KSType.typeKey(typeName: TypeName) =
  when (val declaration = declaration) {
    is KSTypeParameter -> "${declaration.parentDeclaration?.qualifiedNameOrSimpleName()}.${declaration.name.asString()}"
    else -> typeName.typeKey()
  }

internal fun TypeName.expandedTypeAlias() =
  tag<TypeAliasTag>()
    ?.abbreviatedType
    ?: this

private fun TypeName.canonicalTypeName(isRoot: Boolean): TypeName {
  val canonicalType = when (val expandedType = expandedTypeAlias()) {
    is ParameterizedTypeName -> expandedType.rawType
      .parameterizedBy(expandedType.typeArguments.map { it.canonicalTypeName(isRoot = false) })
      .copy(nullable = expandedType.isNullable)
    is WildcardTypeName -> when {
      expandedType.inTypes.isNotEmpty() -> WildcardTypeName.consumerOf(
        expandedType.inTypes.single().canonicalTypeName(isRoot = false)
      )
      else -> WildcardTypeName.producerOf(
        expandedType.outTypes.single().canonicalTypeName(isRoot = false)
      )
    }
    else -> expandedType
  }
  return when {
    isRoot -> canonicalType.copy(nullable = false)
    else -> canonicalType
  }
}

internal fun callCode(callee: CodeBlock, arguments: List<CodeBlock>) = when {
  arguments.size <= 1 -> CodeBlock.of("%L(%L)", callee, arguments.joinToCode())
  else -> buildCodeBlock {
    add("%L(\n", callee)
    withIndent {
      arguments.forEach { add("%L,\n", it) }
    }
    add(")")
  }
}

internal fun CodeBlock.Builder.endControlFlowExpression() =
  unindent()
    .add("}")

internal fun namedArgument(name: String, format: String, vararg args: Any?) = CodeBlock.of(
  "%N = %L",
  name,
  CodeBlock.of(format, *args)
)
