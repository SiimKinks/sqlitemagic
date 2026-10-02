package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.IndexCollectionStep
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.modelCodeGenerationProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep

internal fun viewProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCodeGenerationProcessingSteps(environment) + listOf(
    ViewCollectionStep(environment),
    ViewCodeGenerationStep(environment),
    IndexCollectionStep(environment),
    GenClassesManagerStep(environment)
  )
