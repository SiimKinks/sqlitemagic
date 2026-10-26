package com.siimkinks.sqlitemagic.model

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.writeEach

internal class ModelCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private val writers = listOf(
    ModelDaoWriter(environment),
    ModelAdapterWriter(environment),
    ModelTableWriter(environment),
    ModelExtensionsWriter(environment),
  )

  override fun process(resolver: Resolver) = environment.writeEach(
    elements = environment.tableRoundElementsForCurrentRound.values
  ) { roundTable ->
    writers.forEach { it.write(roundTable) }
  }
}
