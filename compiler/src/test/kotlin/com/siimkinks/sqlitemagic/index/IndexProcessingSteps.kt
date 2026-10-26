package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.modelCodeGenerationProcessingSteps
import com.siimkinks.sqlitemagic.model.modelCollectionProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.view.ViewCodeGenerationStep
import com.siimkinks.sqlitemagic.view.ViewCollectionStep

internal fun indexCollectionProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCollectionProcessingSteps(environment) + IndexCollectionStep(environment)

internal fun indexProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCodeGenerationProcessingSteps(environment) + listOf(
    ViewCollectionStep(environment),
    ViewCodeGenerationStep(environment),
    IndexCollectionStep(environment),
    GenClassesManagerStep(environment)
  )
