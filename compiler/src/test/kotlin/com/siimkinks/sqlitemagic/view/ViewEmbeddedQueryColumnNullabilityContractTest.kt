package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewEmbeddedQueryColumnNullabilityContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `embedded ancestors determine query column nullability without changing declared leaf nullability`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedQueryColumnNullability.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.Column
            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.EmbeddedQueryColumnNullabilityViewTable.Companion.EMBEDDED_QUERY_COLUMN_NULLABILITY_VIEW
            import com.siimkinks.sqlitemagic.NotNullable
            import com.siimkinks.sqlitemagic.Nullable
            import com.siimkinks.sqlitemagic.NumericColumn
            import com.siimkinks.sqlitemagic.Select.SelectN
            import com.siimkinks.sqlitemagic.annotation.Embedded
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            @Table
            data class SourceRow(@Id val id: Long)

            data class InnerValue(
              val text: String,
              val number: Int
            )

            data class NullableOuterValue(
              @Embedded(prefix = "inner_") val inner: InnerValue
            )

            data class NullableInnerValue(
              @Embedded(prefix = "inner_") val inner: InnerValue?
            )

            data class RequiredOuterValue(
              @Embedded(prefix = "inner_") val inner: InnerValue
            )

            @View
            data class EmbeddedQueryColumnNullabilityView(
              @Embedded(prefix = "outer_optional_") val outerOptional: NullableOuterValue?,
              @Embedded(prefix = "inner_optional_") val innerOptional: NullableInnerValue,
              @Embedded(prefix = "required_") val required: RequiredOuterValue
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<SourceRow, SelectN> = error("compile-only")
              }
            }

            fun requireEmbeddedQueryColumnTypes() {
              val viewTable = EMBEDDED_QUERY_COLUMN_NULLABILITY_VIEW
              val outerText: Column<String, String?, CharSequence, EmbeddedQueryColumnNullabilityView, Nullable> =
                viewTable.OUTER_OPTIONAL_INNER_TEXT
              val outerOptionalNumber: NumericColumn<Int, Int?, Number, EmbeddedQueryColumnNullabilityView, Nullable> =
                viewTable.OUTER_OPTIONAL_INNER_NUMBER
              val innerText: Column<String, String?, CharSequence, EmbeddedQueryColumnNullabilityView, Nullable> =
                viewTable.INNER_OPTIONAL_INNER_TEXT
              val innerOptionalNumber: NumericColumn<Int, Int?, Number, EmbeddedQueryColumnNullabilityView, Nullable> =
                viewTable.INNER_OPTIONAL_INNER_NUMBER
              val requiredText: Column<String, String, CharSequence, EmbeddedQueryColumnNullabilityView, NotNullable> =
                viewTable.REQUIRED_INNER_TEXT
              val requiredNumber: NumericColumn<Int, Int, Number, EmbeddedQueryColumnNullabilityView, NotNullable> =
                viewTable.REQUIRED_INNER_NUMBER
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "EmbeddedQueryColumnNullabilityViewTable.kt",
        "SqliteMagic_EmbeddedQueryColumnNullabilityView_Dao.kt"
      )
      .withGeneratedSource("EmbeddedQueryColumnNullabilityViewTable.kt") { generatedSource ->
        generatedSource.assertContains(
          "Utils.NULLABLE_INTEGER_PARSER",
          "Utils.INTEGER_PARSER",
          "nullable = true",
          "nullable = false"
        )
      }
      .withGeneratedSource("SqliteMagic_EmbeddedQueryColumnNullabilityView_Dao.kt") { generatedSource ->
        generatedSource.assertContains(
          "Column \\\"outer_optional_inner_text\\\" was NULL",
          "Column \\\"inner_optional_inner_number\\\" was NULL"
        )
      }
  }
}
