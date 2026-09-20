package com.siimkinks.sqlitemagic.transformer

import com.siimkinks.sqlitemagic.model.modelProcessingSteps
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.FIXTURE_PACKAGE
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.emailValueType
import com.siimkinks.sqlitemagic.transformer.TransformerCollectionSources.objectTransformer
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class TransformerColumnReuseTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `reuses ordinary transformer column from a dependency`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        processingStepsFactory = ::transformerCodeGenerationProcessingSteps
      )
      .isOk()

    provider
      .compile(
        databaseWithEmailTransformer(),
        consumerTable(unique = false)
      )
      .isOk()
      .assertNotGeneratedSources("EmailColumn.kt")
  }

  @Test
  fun `reuses unique transformer column from a dependency`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        providerTable(),
        processingStepsFactory = ::modelProcessingSteps
      )
      .isOk()

    provider
      .compile(
        databaseWithEmailTransformer(),
        consumerTable(unique = true)
      )
      .isOk()
      .assertNotGeneratedSources(
        "EmailColumn.kt",
        "UniqueEmailColumn.kt"
      )
  }

  @Test
  fun `generates ordinary transformer column when dependency has no artifact`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        processingStepsFactory = ::transformerCollectionProcessingSteps
      )
      .isOk()

    provider
      .compile(databaseWithEmailTransformer())
      .isOk()
      .assertGeneratedSources("EmailColumn.kt")
  }

  @Test
  fun `does not regenerate an existing dependency transformer column`() {
    val provider = SqliteMagicCompilation
      .compile(
        emailValueType(),
        objectTransformer(),
        SourceFile.kotlin(
          name = "ExistingEmailColumn.kt",
          contents = """
            package com.siimkinks.sqlitemagic

            class EmailColumn
            """
        ),
        processingStepsFactory = { emptyList() }
      )
      .isOk()

    provider
      .compile(databaseWithEmailTransformer())
      .isOk()
      .assertNotGeneratedSources("EmailColumn.kt")
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

  private fun consumerTable(unique: Boolean) = SourceFile.kotlin(
    name = "ConsumerTable.kt",
    contents = when {
      unique -> """
        package $FIXTURE_PACKAGE

        import com.siimkinks.sqlitemagic.annotation.Table
        import com.siimkinks.sqlitemagic.annotation.Unique

        @Table
        data class ConsumerTable(@Unique val email: Email)
        """
      else -> """
        package $FIXTURE_PACKAGE

        import com.siimkinks.sqlitemagic.annotation.Table

        @Table
        data class ConsumerTable(val email: Email)
        """
    }
  )

  private fun providerTable() = SourceFile.kotlin(
    name = "ProviderTable.kt",
    contents = """
      package $FIXTURE_PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Table
      import com.siimkinks.sqlitemagic.annotation.Unique

      @Table
      data class ProviderTable(@Unique val email: Email)
      """
  )
}
