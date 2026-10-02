package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier.ABSTRACT
import com.google.devtools.ksp.symbol.Modifier.INNER
import com.siimkinks.sqlitemagic.utils.camelCaseToSnakeCase
import com.siimkinks.sqlitemagic.utils.declarationPathNames
import com.siimkinks.sqlitemagic.utils.displayName
import com.siimkinks.sqlitemagic.utils.isEffectivelyAccessibleFromGeneratedCode

internal fun KSClassDeclaration.defaultSqlName() =
  declarationPathNames()
    .joinToString(separator = "_") { name ->
      name.camelCaseToSnakeCase().lowercase()
    }

internal fun KSClassDeclaration.generatedArtifactStem() =
  declarationPathNames()
    .joinToString(separator = "_")

internal fun KSClassDeclaration.isSupportedEmbeddedDeclaration() =
  classKind == ClassKind.CLASS &&
      ABSTRACT !in modifiers &&
      INNER !in modifiers &&
      typeParameters.isEmpty() &&
      isEffectivelyAccessibleFromGeneratedCode()

internal fun validateRootModelDeclaration(
  declaration: KSClassDeclaration,
  modelKind: ModelKind,
  reporter: ModelCollectionReporter
): Boolean {
  val name = declaration.displayName()
  val annotationName = modelKind.toString()
  return when {
    declaration.typeParameters.isNotEmpty() -> reporter.error(
      message = "Generic $annotationName models are unsupported: $name",
      symbol = declaration
    )
    INNER in declaration.modifiers -> reporter.error(
      message = "Inner $annotationName models are unsupported: $name",
      symbol = declaration
    )
    !declaration.isEffectivelyAccessibleFromGeneratedCode() -> reporter.error(
      message = "$annotationName model must be accessible to generated code: $name",
      symbol = declaration
    )
    declaration.classKind != ClassKind.CLASS || ABSTRACT in declaration.modifiers -> reporter.error(
      message = "Unsupported $annotationName model shape: $name",
      symbol = declaration
    )
    else -> true
  }
}
