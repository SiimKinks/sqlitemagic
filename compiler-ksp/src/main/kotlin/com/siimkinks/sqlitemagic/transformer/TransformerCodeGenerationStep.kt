package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Failed
import com.siimkinks.sqlitemagic.writer.ColumnClassWriter
import java.io.IOException

class TransformerCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  override fun process(resolver: Resolver): ProcessingStepResult {
    for (roundTransformer in environment.transformerElementsForCurrentRound) {
      val transformer = roundTransformer.toTransformerElement()
      if (transformer.isDefaultTransformer) continue
      try {
        ColumnClassWriter
          .from(
            transformerElement = transformer,
            codeGenerator = environment.codeGenerator,
            createUniqueClass = false
          )
          .write(roundTransformer.originatingFiles)
      } catch (exception: IOException) {
        environment.logger.exception(exception)
        return Failed
      }
    }
    return Continue
  }
}
