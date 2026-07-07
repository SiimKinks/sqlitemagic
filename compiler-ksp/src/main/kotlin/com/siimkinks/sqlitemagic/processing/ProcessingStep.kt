package com.siimkinks.sqlitemagic.processing

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated

interface ProcessingStep {
  fun process(resolver: Resolver): ProcessingStepResult
}

sealed interface ProcessingStepResult {
  data object Continue : ProcessingStepResult
  data object Failed : ProcessingStepResult
  data class Deferred(val symbols: List<KSAnnotated>) : ProcessingStepResult
}
