package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewEmbeddedQueryColumnNullabilityContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @Test
  fun `embedded ancestors determine query column nullability without changing declared leaf nullability`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "EmbeddedQueryColumnNullability.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.Column
            import com.siimkinks.sqlitemagic.EmbeddedQueryColumnNullabilityViewTable
            import com.siimkinks.sqlitemagic.Required
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.SqlNullable
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
                val query: SelectQuery<SourceRow, *> = error("compile-only")
              }
            }

            private typealias NullabilityView = EmbeddedQueryColumnNullabilityView

            fun requireEmbeddedQueryColumnTypes() {
              val viewTable = EmbeddedQueryColumnNullabilityViewTable.EMBEDDED_QUERY_COLUMN_NULLABILITY_VIEW
              val outerText: Column<NullabilityView, String, SqlNullable, String?, String?, SqlNullable, String?> =
                viewTable.OUTER_OPTIONAL_INNER_TEXT
              val outerNumber: Column<NullabilityView, Int, SqlNullable, Int?, Int?, SqlNullable, Int?> =
                viewTable.OUTER_OPTIONAL_INNER_NUMBER
              val innerText: Column<NullabilityView, String, SqlNullable, String?, String?, SqlNullable, String?> =
                viewTable.INNER_OPTIONAL_INNER_TEXT
              val innerNumber: Column<NullabilityView, Int, SqlNullable, Int?, Int?, SqlNullable, Int?> =
                viewTable.INNER_OPTIONAL_INNER_NUMBER
              val requiredText: Column<NullabilityView, String, SqlNullable, String?, String, Required, String> =
                viewTable.REQUIRED_INNER_TEXT
              val requiredNumber: Column<NullabilityView, Int, SqlNullable, Int, Int, Required, Int> =
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
      .withGeneratedSource("EmbeddedQueryColumnNullabilityViewTable.kt") {
        it.assertContains(
          "selectedRead = { cursor, index -> (if (cursor.isNull(index)) null else cursor.getInt(index)) }",
          "selectedRead = { cursor, index -> cursor.getInt(index) }",
          "parameterEncoding = SqlType.INT.nullBypassParameterEncoding()",
          "parameterEncoding = SqlType.INT.parameterEncoding()"
        )
      }
      .withGeneratedSource("SqliteMagic_EmbeddedQueryColumnNullabilityView_Dao.kt") {
        it.assertContains(
          """Column \"outer_optional_inner_text\" was NULL""",
          """Column \"inner_optional_inner_number\" was NULL"""
        )
      }
  }
}
