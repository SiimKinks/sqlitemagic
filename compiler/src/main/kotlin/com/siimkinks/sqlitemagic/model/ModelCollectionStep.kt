package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.siimkinks.sqlitemagic.AnnotationNames.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.ConsumedAnnotations
import com.siimkinks.sqlitemagic.utils.validateConsumedAnnotations
import com.siimkinks.sqlitemagic.utils.validateModelMembers

class ModelCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val annotations = ConsumedAnnotations()
    val symbols = resolver.getSymbolsWithAnnotation(TABLE_ANNOTATION)
    val (valid, deferred) = symbols.partition { it.validateTable(annotations) }
    val declarations = valid.filterIsInstance<KSClassDeclaration>()
    environment.setDeferredTableSourceKeys(
      deferred.filterIsInstance<KSClassDeclaration>()
    )
    val isCollectionSuccessful = ModelCollector(
      environment = environment,
      annotations = annotations
    ).collect(declarations)
    return when {
      declarations.isNotEmpty() && !isCollectionSuccessful -> Failed
      deferred.isNotEmpty() -> Deferred(deferred)
      else -> Continue
    }
  }
}

private fun KSAnnotated.validateTable(annotations: ConsumedAnnotations): Boolean {
  val declaration = this as? KSClassDeclaration ?: return validateConsumedAnnotations()
  if (annotations.hasUnresolvedArguments(declaration)) return false
  val persistAll = annotations.table(declaration)?.persistAll ?: return validateConsumedAnnotations()
  return declaration.validateModelMembers(
    annotations = annotations,
    isRequiredProperty = { it.parentDeclaration == declaration && persistAll }
  )
}
