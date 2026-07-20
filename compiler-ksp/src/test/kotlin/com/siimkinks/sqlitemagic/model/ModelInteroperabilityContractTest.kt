package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelInteroperabilityContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `supports constructor-backed non-Kotlin declarations with the same Kotlin-visible model capabilities`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.java(
          name = "ConstructorBackedInteropModel.java",
          contents = """
            package $PACKAGE;

            import com.siimkinks.sqlitemagic.annotation.Id;
            import com.siimkinks.sqlitemagic.annotation.Table;

            @Table
            public record ConstructorBackedInteropModel(
                @Id String id,
                String value
            ) {}
          """
        ),
        SourceFile.kotlin(
          name = "ConstructorBackedInteropModelConsumer.kt",
          contents = """
            package $PACKAGE

            fun createConstructorBackedInteropModel() =
                ConstructorBackedInteropModel("id", "value")

            fun readConstructorBackedInteropValue(model: ConstructorBackedInteropModel) =
                model.value
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ConstructorBackedInteropModel_Dao.kt",
        "SqliteMagic_ConstructorBackedInteropModel_Handler.kt",
        "ConstructorBackedInteropModelTable.kt",
        "_ConstructorBackedInteropModel.kt"
      )
      .withGeneratedSource("SqliteMagic_ConstructorBackedInteropModel_Handler.kt") { generatedSource ->
        generatedSource.assertContains("value TEXT DEFAULT NULL")
      }
  }

  @Test
  fun `supports mutable non-Kotlin declarations with the same Kotlin-visible model capabilities`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.java(
          name = "InteropModel.java",
          contents = """
            package $PACKAGE;

            import com.siimkinks.sqlitemagic.annotation.Id;
            import com.siimkinks.sqlitemagic.annotation.Table;

            @Table
            public class InteropModel {
              @Id public String id;
              public String value;

              public InteropModel() {}
            }
          """
        ),
        SourceFile.kotlin(
          name = "InteropModelConsumer.kt",
          contents = """
            package $PACKAGE

            fun createInteropModel() = InteropModel()

            fun readInteropValue(model: InteropModel) = model.value

            fun writeInteropValue(model: InteropModel) {
              model.value = "updated"
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_InteropModel_Dao.kt",
        "SqliteMagic_InteropModel_Handler.kt",
        "InteropModelTable.kt",
        "_InteropModel.kt"
      )
      .withGeneratedSource("SqliteMagic_InteropModel_Handler.kt") { generatedSource ->
        generatedSource.assertContains("value TEXT DEFAULT NULL")
      }
  }
}
