package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.manager.GenClassesManagerStep
import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.processing.ProcessingStep

internal fun indexProcessingSteps(environment: Environment): List<ProcessingStep> =
  modelProcessingSteps(environment) + listOf(
    // TODO Phase 3: add the durable index model after its round boundary is approved.
    // TODO Phase 4: add index collection and validation after the durable index model exists.
    // TODO Phase 5: add index SQL generation and manager integration after index collection exists.
    // TODO Views: add view collection and generation only when the views slice is authorized.
    GenClassesManagerStep(environment)
  )
