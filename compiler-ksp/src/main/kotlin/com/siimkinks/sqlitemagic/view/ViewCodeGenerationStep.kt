package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import java.io.IOException

class ViewCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private val writers = listOf<ViewWriter>(
    // TODO implement
//    ViewDaoWriter(environment),
//    ViewTableWriter(environment),
  )

  override fun process(resolver: Resolver): ProcessingStepResult {
    for (roundElement in environment.viewRoundElementsForCurrentRound) {
      try {
        writers.forEach { it.write(roundElement) }
      } catch (exception: IOException) {
        environment.logger.exception(exception)
        return Failed
      }
    }
    return Continue
  }
}
