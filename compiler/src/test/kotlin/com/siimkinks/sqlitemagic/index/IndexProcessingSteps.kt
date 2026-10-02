package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.modelCodeGenerationProcessingSteps
import com.siimkinks.sqlitemagic.model.modelCollectionProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep

internal fun indexCollectionProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCollectionProcessingSteps(environment) + IndexCollectionStep(environment)

internal fun indexProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCodeGenerationProcessingSteps(environment) + listOf(
    IndexCollectionStep(environment),
    // TODO Views: add view collection and generation only when the views slice is authorized.
    GenClassesManagerStep(environment)
  )
