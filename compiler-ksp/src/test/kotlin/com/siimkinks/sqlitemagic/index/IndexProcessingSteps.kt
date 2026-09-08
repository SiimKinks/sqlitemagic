package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.modelCollectionProcessingSteps
import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep

internal fun indexCollectionProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelCollectionProcessingSteps(environment) + IndexCollectionStep(environment)

internal fun indexProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelProcessingSteps(environment) + listOf(
    IndexCollectionStep(environment),
    // TODO Phase 5: add index SQL generation and manager integration after index collection exists.
    // TODO Views: add view collection and generation only when the views slice is authorized.
    GenClassesManagerStep(environment)
  )
