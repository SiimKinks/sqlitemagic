package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.utils.getLocalAndInheritedFunctions
import com.siimkinks.sqlitemagic.utils.isUncheckedAnnotationPresent

class DefaultTransformerCollectionStep(
  private val environment: Environment
) : ProcessingStep {
  private var isCollected = false

  override fun process(resolver: Resolver) = when {
    isCollected -> Continue
    else -> collectDefaultTransformers(resolver)
  }

  private fun collectDefaultTransformers(resolver: Resolver): ProcessingStepResult {
    val defaultTransformerMethods = resolver
      .getDefaultTransformerDeclarations()
      .flatMap(KSClassDeclaration::getLocalAndInheritedFunctions)
      .toSet()
    val collector = TransformerCollector(environment)
    collector.collect(
      objectToDbValueElements = defaultTransformerMethods.filter {
        it.isUncheckedAnnotationPresent<ObjectToDbValue>()
      },
      dbValueToObjectElements = defaultTransformerMethods.filter {
        it.isUncheckedAnnotationPresent<DbValueToObject>()
      }
    )
    return when {
      collector.addToEnvironment() -> {
        isCollected = true
        Continue
      }
      else -> Failed
    }
  }

  private fun Resolver.getDefaultTransformerDeclarations() =
    DEFAULT_TRANSFORMERS.map { transformerName ->
      checkNotNull(getClassDeclarationByName(transformerName)) {
        "Default transformer $transformerName could not be resolved"
      }
    }
}
