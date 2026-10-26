package com.siimkinks.sqlitemagic.transformer

import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.writeEach

internal class TransformerCodeGenerationStep(
  private val environment: Environment
) : ProcessingStep {
  private val generatedDescriptors = mutableSetOf<String>()

  override fun process(resolver: Resolver) = environment.writeEach(
    elements = environment.transformerElementsForCurrentRound.values
  ) { roundTransformer ->
    val transformer = roundTransformer.toTransformerElement()
    when {
      transformer.isDefaultTransformer -> Unit
      resolver.hasGeneratedTransformerDescriptor(transformer) -> Unit
      !generatedDescriptors.add(transformer.typeKey) -> Unit
      else -> TransformerSqlTypeWriter(
        transformer = transformer,
        codeGenerator = environment.codeGenerator
      ).write(roundTransformer.originatingFiles)
    }
  }
}
