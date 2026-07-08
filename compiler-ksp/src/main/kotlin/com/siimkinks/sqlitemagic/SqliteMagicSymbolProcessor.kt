package com.siimkinks.sqlitemagic

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.Types.COLUMN_ANNOTATION
import com.siimkinks.sqlitemagic.Types.DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.DB_VALUE_TO_OBJECT_ANNOTATION
import com.siimkinks.sqlitemagic.Types.INDEX_ANNOTATION
import com.siimkinks.sqlitemagic.Types.OBJECT_TO_DB_VALUE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.SUBMODULE_DATABASE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.TABLE_ANNOTATION
import com.siimkinks.sqlitemagic.Types.VIEW_ANNOTATION
import com.siimkinks.sqlitemagic.processing.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed

class SqliteMagicSymbolProcessor(
  symbolProcessorEnvironment: SymbolProcessorEnvironment,
  processingStepsProvider: (Environment) -> List<ProcessingStep> = { env ->
    listOf(
      DatabaseConfigurationCollectionStep(env)
    )
  }
) : SymbolProcessor {
  val environment = Environment(symbolProcessorEnvironment)
  private val processingSteps = processingStepsProvider(environment)
  private var debugMessageEmitted = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (environment.isProcessingFailed) {
      return emptyList()
    }
    environment.incrementRound()

    if (!debugMessageEmitted && environment.options.debug) {
      environment.logger.warn("SqliteMagic KSP debug mode enabled")
      debugMessageEmitted = true
    }

    val deferredSymbols = mutableListOf<KSAnnotated>()
    for (processingStep in processingSteps) {
      when (val result = processingStep.process(resolver)) {
        Continue -> continue
        is Deferred -> deferredSymbols += result.symbols
        Failed -> {
          environment.isProcessingFailed = true
          return emptyList()
        }
      }
    }
    return deferredSymbols
  }

  companion object {
    const val OPTION_DEBUG = "sqlitemagic.ksp.debug"
    const val OPTION_VARIANT_DEBUG = "sqlitemagic.variant.debug"
    const val OPTION_DB_NAME = "sqlitemagic.db.name"
    const val OPTION_DB_VERSION = "sqlitemagic.db.version"

    val SUPPORTED_ANNOTATIONS = setOf(
      DATABASE_ANNOTATION,
      SUBMODULE_DATABASE_ANNOTATION,
      TABLE_ANNOTATION,
      COLUMN_ANNOTATION,
      OBJECT_TO_DB_VALUE_ANNOTATION,
      DB_VALUE_TO_OBJECT_ANNOTATION,
      VIEW_ANNOTATION,
      INDEX_ANNOTATION
    )
  }
}
