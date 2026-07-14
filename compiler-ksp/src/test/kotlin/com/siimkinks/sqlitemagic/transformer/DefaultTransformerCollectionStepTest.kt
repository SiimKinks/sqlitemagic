package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import org.junit.jupiter.api.Test

internal class DefaultTransformerCollectionStepTest : ProcessingStepsTest {
  override val processingSteps
    get() = { env: Environment ->
      listOf(DefaultTransformerCollectionStep(env))
    }

  @Test
  fun `collects default transformers`() {
    SqliteMagicCompilation
      .compile(emailValueType())
      .isOk()
      .assertEmptyTransformers()
      .apply {
        val transformerOwnerNames = environment.transformerElements.values
          .filter(TransformerElement::isDefaultTransformer)
          .map { it.objectToDbValueMethod.ownerQualifiedName }
        assertThat(transformerOwnerNames).containsExactlyElementsIn(DEFAULT_TRANSFORMERS)
      }
  }
}
