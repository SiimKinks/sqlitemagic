package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.model.modelCodeGenerationProcessingSteps

internal fun genClassesManagerProcessingSteps(environment: Environment) =
  modelCodeGenerationProcessingSteps(environment) + listOf(
    // TODO Views phase: ViewCollectionStep(environment), ViewCodeGenerationStep(environment)
    IndexCollectionStep(environment),
    GenClassesManagerStep(environment)
  )
