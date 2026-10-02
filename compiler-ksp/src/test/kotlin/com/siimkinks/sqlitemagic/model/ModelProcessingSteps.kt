package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.transformer.transformerCodeGenerationProcessingSteps

internal fun modelCollectionProcessingSteps(environment: Environment) =
  transformerCodeGenerationProcessingSteps(environment) + ModelCollectionStep(environment)

internal fun modelCodeGenerationProcessingSteps(environment: Environment) =
  modelCollectionProcessingSteps(environment) + ModelCodeGenerationStep(environment)

internal fun modelProcessingSteps(environment: Environment) =
  modelCodeGenerationProcessingSteps(environment) + GenClassesManagerStep(environment)
