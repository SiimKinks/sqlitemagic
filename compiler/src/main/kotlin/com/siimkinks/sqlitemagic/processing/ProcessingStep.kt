package com.siimkinks.sqlitemagic.processing

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue

interface ProcessingStep {
  fun process(resolver: Resolver): ProcessingStepResult = Continue

  fun finish(): ProcessingStepResult = Continue
}

sealed interface ProcessingStepResult {
  data object Continue : ProcessingStepResult
  data object Failed : ProcessingStepResult
  data class Deferred(val symbols: List<KSAnnotated>) : ProcessingStepResult
}
