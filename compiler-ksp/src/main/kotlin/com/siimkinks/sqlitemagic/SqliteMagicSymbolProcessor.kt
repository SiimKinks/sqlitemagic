package com.siimkinks.sqlitemagic

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.ModelCodeGenerationStep
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Deferred
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.transformer.DefaultTransformerCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCodeGenerationStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionStep

class SqliteMagicSymbolProcessor(
  symbolProcessorEnvironment: SymbolProcessorEnvironment,
  processingStepsProvider: (Environment) -> List<ProcessingStep> = { env ->
    listOf(
      DefaultTransformerCollectionStep(env),
      DatabaseConfigurationCollectionStep(env),
      TransformerCollectionStep(env),
      TransformerCodeGenerationStep(env),
      ModelCollectionStep(env),
      ModelCodeGenerationStep(env),
      GenClassesManagerStep(env),
    )
  }
) : SymbolProcessor {
  val environment = Environment(symbolProcessorEnvironment)
  private val processingSteps = processingStepsProvider(environment)

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (environment.isProcessingFailed) {
      return emptyList()
    }
    environment.incrementRound()

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

  override fun finish() {
    if (environment.isProcessingFailed) return
    for (processingStep in processingSteps) {
      when (processingStep.finish()) {
        Continue -> continue
        Failed, is Deferred -> {
          environment.isProcessingFailed = true
          return
        }
      }
    }
  }

  companion object {
    const val OPTION_DEBUG = "sqlitemagic.ksp.debug"
    const val OPTION_VARIANT_DEBUG = "sqlitemagic.variant.debug"
    const val OPTION_DB_NAME = "sqlitemagic.db.name"
    const val OPTION_DB_VERSION = "sqlitemagic.db.version"
    const val OPTION_MAIN_MODULE_PATH = "sqlitemagic.main.module.path"
    const val OPTION_MIGRATE_DEBUG = "sqlitemagic.migrate.debug"
    const val OPTION_PROJECT_DIR = "sqlitemagic.project.dir"
    const val OPTION_PUBLIC_EXTENSIONS = "sqlitemagic.kotlin.public.extensions"
    const val OPTION_VARIANT_NAME = "sqlitemagic.variant.name"
  }
}
