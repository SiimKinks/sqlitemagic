package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.Types.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed

class ModelCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val symbols = resolver.getSymbolsWithAnnotation(TABLE_ANNOTATION)
    val (valid, deferred) = symbols.partition(KSAnnotated::validate)
    val declarations = valid.filterIsInstance<KSClassDeclaration>()
    val isCollectionSuccessful = ModelCollector(environment).collect(declarations)
    return when {
      declarations.isNotEmpty() && !isCollectionSuccessful -> Failed
      deferred.isNotEmpty() -> Deferred(deferred)
      else -> Continue
    }
  }
}
