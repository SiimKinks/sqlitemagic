package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.validate
import com.siimkinks.sqlitemagic.AnnotationNames.VIEW_ANNOTATION
import com.siimkinks.sqlitemagic.AnnotationNames.VIEW_QUERY_ANNOTATION
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.findAnnotationWithType

class ViewCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    val querySymbols = resolver
      .getSymbolsWithAnnotation(VIEW_QUERY_ANNOTATION)
      .toList()
    val orphanQueries = querySymbols.filterNot(KSAnnotated::isDirectViewQueryOwner)
    orphanQueries.forEach { symbol ->
      val name = (symbol as? KSDeclaration)?.simpleName?.asString().orEmpty()
      environment.logger.error(
        message = "@ViewQuery must be declared directly inside a @View: $name",
        symbol = symbol
      )
    }

    val (valid, deferred) = resolver
      .getSymbolsWithAnnotation(VIEW_ANNOTATION)
      .partition(KSAnnotated::validate)
    val declarations = valid.filterIsInstance<KSClassDeclaration>()
    environment.setDeferredViewSourceKeys(
      deferred.filterIsInstance<KSClassDeclaration>()
    )
    val compiledSelectType = checkNotNull(
      resolver.getClassDeclarationByName(
        resolver.getKSNameFromString(
          checkNotNull(CompiledSelect::class.qualifiedName)
        )
      )
    ).asStarProjectedType()
    val isCollectionSuccessful = when {
      orphanQueries.isNotEmpty() -> false
      else -> ViewCollector(
        environment = environment,
        compiledSelectType = compiledSelectType
      ).collect(declarations)
    }
    val distinctDeferred = deferred.distinct()
    return when {
      !isCollectionSuccessful -> Failed
      distinctDeferred.isNotEmpty() -> Deferred(distinctDeferred)
      else -> Continue
    }
  }
}

private fun KSAnnotated.isDirectViewQueryOwner(): Boolean {
  val declaration = this as? KSDeclaration ?: return false
  val owner = declaration.parentDeclaration as? KSClassDeclaration ?: return false
  return when {
    owner.findAnnotationWithType<View>() != null -> true
    !owner.isCompanionObject -> false
    else -> (owner.parentDeclaration as? KSClassDeclaration)
      ?.findAnnotationWithType<View>() != null
  }
}
