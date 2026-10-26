package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ModelInteroperabilityContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      "package-private field | public | '' | Mutable @Table properties must be readable and writable",
      "package-private class | '' | public | @Table model must be accessible to generated code"
    ]
  )
  fun `generated code in the root package does not reach package-private Java declarations`(
    label: String,
    classModifier: String,
    fieldModifier: String,
    message: String
  ) {
    compiler
      .compile(
        SourceFile.java(
          name = "PackagePrivateModel.java",
          contents = """
            package $PACKAGE;

            import com.siimkinks.sqlitemagic.annotation.Id;
            import com.siimkinks.sqlitemagic.annotation.Table;

            @Table
            $classModifier class PackagePrivateModel {
              @Id public String id;
              $fieldModifier String value;

              public PackagePrivateModel() {}
            }
          """
        )
      )
      .assertCompilationError(
        message,
        "PackagePrivateModel"
      )
  }

  @Test
  fun `rejects non-Kotlin constructor callables without KSP property capabilities`() {
    compiler
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
      .assertCompilationError(
        "Table must define at least one persisted column",
        "ConstructorBackedInteropModel"
      )
  }

  @Test
  fun `supports mutable non-Kotlin declarations with the same Kotlin-visible model capabilities`() {
    compiler
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
        "SqliteMagic_InteropModel_Adapter.kt",
        "InteropModelTable.kt",
        "_InteropModel.kt"
      )
      .withGeneratedSource("SqliteMagic_InteropModel_Adapter.kt") {
        it.assertContains("value TEXT DEFAULT NULL")
      }
  }
}
