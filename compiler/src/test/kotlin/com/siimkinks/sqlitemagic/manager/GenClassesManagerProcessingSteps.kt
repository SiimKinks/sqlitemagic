package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.model.modelCodeGenerationProcessingSteps
import com.siimkinks.sqlitemagic.view.ViewCodeGenerationStep
import com.siimkinks.sqlitemagic.view.ViewCollectionStep

internal fun genClassesManagerProcessingSteps(environment: Environment) =
  modelCodeGenerationProcessingSteps(environment) + listOf(
    ViewCollectionStep(environment),
    ViewCodeGenerationStep(environment),
    IndexCollectionStep(environment),
    GenClassesManagerStep(environment)
  )
