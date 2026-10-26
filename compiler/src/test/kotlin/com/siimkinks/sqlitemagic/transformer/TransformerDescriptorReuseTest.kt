package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNotGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class TransformerDescriptorReuseTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `reuses a transformer descriptor from a dependency`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        processingStepsFactory = ::transformerCodeGenerationProcessingSteps
      )
      .isOk()

    compiler
      .compile(
        databaseWithEmailTransformer(),
        consumerTable(emailAnnotation = ""),
        classpaths = listOf(provider.outputDirectory)
      )
      .isOk()
      .assertNotGeneratedSources("EmailSqlType.kt")
  }

  @Test
  fun `reuses a descriptor published with dependency tables`() {
    val provider = compiler
      .compile(
        emailValueType(),
        objectTransformer(),
        SourceFile.kotlin(
          name = "ProviderTable.kt",
          contents = """
            package $FIXTURE_PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.Unique

            @Table
            data class ProviderTable(@Unique val email: Email)
            """
        )
      )
      .isOk()

    compiler
      .compile(
        databaseWithEmailTransformer(),
        consumerTable(emailAnnotation = "@Unique"),
        classpaths = listOf(provider.outputDirectory)
      )
      .isOk()
      .assertNotGeneratedSources("EmailSqlType.kt")
  }

  @Test
  fun `generates a transformer descriptor when the dependency has none`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        processingStepsFactory = ::transformerCollectionProcessingSteps
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        databaseWithEmailTransformer(),
        classpaths = listOf(provider.outputDirectory),
        processingStepsFactory = ::transformerCodeGenerationProcessingSteps
      )
      .isOk()
      .assertGeneratedSources("EmailSqlType.kt")
  }

  @Test
  fun `does not regenerate an existing dependency descriptor`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        SourceFile.kotlin(
          name = "ExistingEmailSqlType.kt",
          contents = """
            package com.siimkinks.sqlitemagic

            val SqlType.EMAIL: Int
              get() = 0
            """
        ),
        processingStepsFactory = { emptyList() }
      )
      .isOk()

    SqliteMagicCompilation
      .compile(
        databaseWithEmailTransformer(),
        classpaths = listOf(provider.outputDirectory),
        processingStepsFactory = ::transformerCodeGenerationProcessingSteps
      )
      .isOk()
      .assertNotGeneratedSources("EmailSqlType.kt")
  }

  private fun databaseWithEmailTransformer() = SourceFile.kotlin(
    name = "ConsumerDatabase.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(externalTransformers = [EmailTransformer::class])
      class ConsumerDatabase
      """
  )

  private fun consumerTable(emailAnnotation: String) = SourceFile.kotlin(
    name = "ConsumerTable.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.Unique

      @Table
      data class ConsumerTable($emailAnnotation val email: Email)
      """
  )
}
