package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ModelDeclarationShapeContractTest {
  private val compiler = SqliteMagicCompiler(::modelProcessingSteps)

  @Test
  fun `generates nested and value class table models named by their declaration paths`() {
    val compilation = compiler
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
        ),
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
        "SqliteMagic_FirstContainer_NestedTable_Dao.kt",
        "SqliteMagic_FirstContainer_NestedTable_Adapter.kt",
        "FirstContainer_NestedTableTable.kt",
        "_FirstContainer_NestedTable.kt",
        "SqliteMagic_SecondContainer_NestedTable_Dao.kt",
        "SqliteMagic_SecondContainer_NestedTable_Adapter.kt",
        "SecondContainer_NestedTableTable.kt",
        "_SecondContainer_NestedTable.kt"
      )
      .assertGeneratedSources(
        "SqliteMagic_ValueClassTable_Dao.kt",
        "SqliteMagic_ValueClassTable_Adapter.kt",
        "ValueClassTableTable.kt",
        "_ValueClassTable.kt"
      )

    assertThat(compilation.environment.tableElements.values.map(TableElement::tableName))
      .containsExactly(
        "first_container_nested_table",
        "second_container_nested_table",
        "value_class_table"
      )
  }

  @Test
  fun `rejects nested declaration paths with colliding artifact stems`() {
    compiler
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
        "Cannot generate code for declarations",
        "their declaration paths map to the same generated name 'Outer_WithUnderscore_Nested'",
        "Rename one model or an enclosing class to make the generated names unique",
        "Outer_WithUnderscore.Nested",
        "Outer.WithUnderscore_Nested"
      )
  }

  @Test
  fun `rejects generic table models`() {
    compiler
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
    compiler
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

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    delimiter = '|',
    value = [
      """object | ObjectTable | object ObjectTable { var value: String = "" }""",
      """enum class | EnumTable | enum class EnumTable { VALUE; var persistedValue: String = "" }""",
      "annotation class | AnnotationTable | annotation class AnnotationTable(val value: String)",
      "interface | TableInterface | interface TableInterface",
      "abstract class | AbstractTable | abstract class AbstractTable { abstract val value: String }"
    ]
  )
  fun `rejects unsupported table model shapes`(
    label: String,
    modelName: String,
    declaration: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "$modelName.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            @Table
            $declaration
          """
        )
      )
      .assertCompilationError(
        "Unsupported @Table model shape",
        modelName
      )
  }

  @Test
  fun `rejects table models inaccessible to generated code`() {
    compiler
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
  fun `rejects table models inside inaccessible containers`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "PrivateContainer.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table

            private class PrivateContainer {
              @Table
              class NestedTable(val value: String)
            }
          """
        )
      )
      .assertCompilationError(
        "@Table model must be accessible to generated code",
        "PrivateContainer.NestedTable"
      )
  }
}
