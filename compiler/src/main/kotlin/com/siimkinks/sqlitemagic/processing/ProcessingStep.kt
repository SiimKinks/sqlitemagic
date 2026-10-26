package com.siimkinks.sqlitemagic.processing

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import java.io.IOException

internal interface ProcessingStep {
  fun process(resolver: Resolver): ProcessingStepResult = Continue

  fun finish(): ProcessingStepResult = Continue
}

internal sealed interface ProcessingStepResult {
  data object Continue : ProcessingStepResult
  data object Failed : ProcessingStepResult
  data class Deferred(val symbols: List<KSAnnotated>) : ProcessingStepResult
}

internal inline fun <E> Environment.writeEach(
  elements: Iterable<E>,
  write: (E) -> Unit
): ProcessingStepResult {
  for (element in elements) {
    try {
      write(element)
    } catch (exception: IOException) {
      logger.exception(exception)
      return Failed
    }
  }
  return Continue
}
