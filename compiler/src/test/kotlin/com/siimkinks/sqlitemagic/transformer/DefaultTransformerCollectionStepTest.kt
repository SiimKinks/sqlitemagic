package com.siimkinks.sqlitemagic.transformer

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Const.DEFAULT_TRANSFORMERS
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class DefaultTransformerCollectionStepTest {
  private val compiler = SqliteMagicCompiler(::transformerCodeGenerationProcessingSteps)

  @Test
  fun `collects default transformers without generating their descriptors`() {
    compiler
      .compile(
        emailValueType(),
        SourceFile.kotlin(
          name = "TestDatabase.kt",
          contents = """
            package $FIXTURE_PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Database

            @Database
            class TestDatabase
            """
        )
      )
      .isOk()
      .assertEmptyTransformers()
      .apply {
        val transformerOwnerNames = environment.transformerElements.values
          .filter(TransformerElement::isDefaultTransformer)
          .map { it.objectToDbValueMethod.ownerQualifiedName }
        assertThat(transformerOwnerNames).containsExactlyElementsIn(DEFAULT_TRANSFORMERS)
        assertThat(generatedSourceNames()).isEmpty()
      }
  }
}
