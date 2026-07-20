package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ModelDeclarationShapeContractTest : ProcessingStepsTest {
  override val processingSteps = ::modelProcessingSteps

  @Test
  fun `generates nested non-inner table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NestedTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            class ModelContainer {
              @Table
              class NestedTable(val value: String)
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ModelContainer_NestedTable_Dao.kt",
        "SqliteMagic_ModelContainer_NestedTable_Handler.kt",
        "ModelContainer_NestedTableTable.kt",
        "_ModelContainer_NestedTable.kt"
      )
  }

  @Test
  fun `uses enclosing declaration paths to distinguish nested model artifacts`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "NestedTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            class FirstContainer {
              @Table
              class NestedTable(val firstValue: String)
            }

            class SecondContainer {
              @Table
              class NestedTable(val secondValue: String)
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_FirstContainer_NestedTable_Dao.kt",
        "SqliteMagic_FirstContainer_NestedTable_Handler.kt",
        "FirstContainer_NestedTableTable.kt",
        "_FirstContainer_NestedTable.kt",
        "SqliteMagic_SecondContainer_NestedTable_Dao.kt",
        "SqliteMagic_SecondContainer_NestedTable_Handler.kt",
        "SecondContainer_NestedTableTable.kt",
        "_SecondContainer_NestedTable.kt"
      )
  }

  @Test
  fun `rejects nested declaration paths with colliding artifact stems`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "CollidingNestedTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            class Outer_WithUnderscore {
              @Table
              class Nested(val firstValue: String)
            }

            class Outer {
              @Table
              class WithUnderscore_Nested(val secondValue: String)
            }
          """
        )
      )
      .assertCompilationError(
        "Generated model artifact stem 'Outer_WithUnderscore_Nested' is ambiguous",
        "Outer_WithUnderscore.Nested",
        "Outer.WithUnderscore_Nested"
      )
  }

  @Test
  fun `generates Kotlin value class table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ValueClassTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @JvmInline
            @Table
            value class ValueClassTable(@Id val value: String)
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ValueClassTable_Dao.kt",
        "SqliteMagic_ValueClassTable_Handler.kt",
        "ValueClassTableTable.kt",
        "_ValueClassTable.kt"
      )
  }

  @Test
  fun `rejects generic table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "GenericTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            class GenericTable<T> {
              var value: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "Generic @Table models are unsupported",
        "GenericTable"
      )
  }

  @Test
  fun `rejects inner table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InnerTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            class OuterModel {
              @Table
              inner class InnerTable {
                var value: String = ""
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Inner @Table models are unsupported",
        "OuterModel.InnerTable"
      )
  }

  @Test
  fun `rejects object table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ObjectTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            object ObjectTable {
              var value: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "ObjectTable"
      )
  }

  @Test
  fun `rejects enum table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EnumTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            enum class EnumTable {
              VALUE;

              var persistedValue: String = ""
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "EnumTable"
      )
  }

  @Test
  fun `rejects annotation class table models`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AnnotationTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            annotation class AnnotationTable(val value: String)
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "AnnotationTable"
      )
  }

  @Test
  fun `rejects table models inaccessible to generated code`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "PrivateTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            private class PrivateTable(val value: String)
          """
        )
      )
      .assertCompilationError(
        "@Table model must be accessible to generated code",
        "PrivateTable"
      )
  }

  @Test
  fun `rejects table interfaces`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "TableInterface.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            interface TableInterface
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "TableInterface"
      )
  }

  @Test
  fun `rejects abstract table classes`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "AbstractTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            abstract class AbstractTable {
              abstract val value: String
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        "AbstractTable"
      )
  }
}
