package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep

internal fun transformerCollectionProcessingSteps(environment: Environment) = listOf(
  DefaultTransformerCollectionStep(environment),
  DatabaseConfigurationCollectionStep(environment),
  TransformerCollectionStep(environment)
)

internal fun transformerCodeGenerationProcessingSteps(environment: Environment) =
  transformerCollectionProcessingSteps(environment) + TransformerCodeGenerationStep(environment)
