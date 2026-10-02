package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.ProcessorCompilationResult

fun ProcessorCompilationResult.assertEmptyTransformers() = apply {
  assertThat(nonDefaultTransformers()).isEmpty()
}

fun ProcessorCompilationResult.assertTransformers(
  vararg transformers: TransformerElement
) = apply {
  assertThat(nonDefaultTransformers())
    .isEqualTo(transformers.associateBy(TransformerElement::typeKey))
}

private fun ProcessorCompilationResult.nonDefaultTransformers() =
  environment.transformerElements.values
    .filterNot(TransformerElement::isDefaultTransformer)
    .associateBy(TransformerElement::typeKey)
