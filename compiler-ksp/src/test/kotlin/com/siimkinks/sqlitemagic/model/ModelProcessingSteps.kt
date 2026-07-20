package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.transformer.DefaultTransformerCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCodeGenerationStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionStep

internal fun modelProcessingSteps(environment: Environment): List<ProcessingStep> = listOf(
  DefaultTransformerCollectionStep(environment),
  DatabaseConfigurationCollectionStep(environment),
  TransformerCollectionStep(environment),
  TransformerCodeGenerationStep(environment),
  // TODO(Phase 4): ModelCollectionStep(environment),
  // TODO(Phase 5): ModelCodeGenerationStep(environment)
)
