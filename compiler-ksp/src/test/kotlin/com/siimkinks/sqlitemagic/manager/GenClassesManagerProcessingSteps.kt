package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.model.modelProcessingSteps

internal fun genClassesManagerProcessingSteps(environment: Environment) =
  modelProcessingSteps(environment) + listOf(
    // TODO Views phase: ViewCollectionStep(environment), ViewCodeGenerationStep(environment)
    IndexCollectionStep(environment),
    GenClassesManagerStep(environment)
  )
