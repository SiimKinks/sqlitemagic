package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.dbconfig.DatabaseConfigurationCollectionStep
import com.siimkinks.sqlitemagic.model.ModelCodeGenerationStep
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.transformer.DefaultTransformerCollectionStep
import com.siimkinks.sqlitemagic.transformer.TransformerCodeGenerationStep
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionStep

internal fun genClassesManagerProcessingSteps(environment: Environment) = listOf(
  DefaultTransformerCollectionStep(environment),
  DatabaseConfigurationCollectionStep(environment),
  TransformerCollectionStep(environment),
  TransformerCodeGenerationStep(environment),
  ModelCollectionStep(environment),
  ModelCodeGenerationStep(environment),
  // TODO Views phase: ViewCollectionStep(environment), ViewCodeGenerationStep(environment)
  // TODO Indices phase: IndexCollectionStep(environment)
  GenClassesManagerStep(environment),
)
