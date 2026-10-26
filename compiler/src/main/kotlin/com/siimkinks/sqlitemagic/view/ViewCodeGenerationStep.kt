package com.siimkinks.sqlitemagic.view

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.writeEach

internal class ViewCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private val writers = listOf(
    ViewDaoWriter(environment),
    ViewTableWriter(environment)
  )

  override fun process(resolver: Resolver) = environment.writeEach(
    elements = environment.viewRoundElementsForCurrentRound
  ) { roundElement ->
    writers.forEach { it.write(roundElement) }
  }
}
