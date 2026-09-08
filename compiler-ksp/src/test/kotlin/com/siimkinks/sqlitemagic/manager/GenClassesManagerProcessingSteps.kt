package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.model.modelProcessingSteps

internal fun genClassesManagerProcessingSteps(environment: Environment) =
  modelProcessingSteps(environment) + listOf(
    // TODO Views phase: ViewCollectionStep(environment), ViewCodeGenerationStep(environment)
    // TODO Indices phase: IndexCollectionStep(environment)
    GenClassesManagerStep(environment)
  )
