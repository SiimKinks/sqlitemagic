package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import org.junit.jupiter.api.Test

internal class ViewQueryContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `rejects a view without a defining query`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "MissingQueryView",
          body = """
            @View
            data class MissingQueryView(
              @ViewColumn("value")
              val value: String
            )
          """
        )
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "MissingQueryView"
      )
  }

  @Test
  fun `rejects a view with multiple defining queries`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "MultipleQueryView",
          body = """
            @View
            data class MultipleQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val FIRST_QUERY: CompiledSelect<String, Select1> = compileOnlySelect()

                @ViewQuery
                val SECOND_QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "MultipleQueryView"
      )
  }

  @Test
  fun `rejects a private defining query`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "PrivateQueryView",
          body = """
            @View
            data class PrivateQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                private val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery property must be accessible to generated code",
        "PrivateQueryView.QUERY"
      )
  }

  @Test
  fun `rejects a defining query in an inaccessible companion object`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "PrivateCompanionQueryView",
          body = """
            @View
            data class PrivateCompanionQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              private companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery property must be accessible to generated code",
        "PrivateCompanionQueryView.QUERY"
      )
  }

  @Test
  fun `rejects an instance defining query`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "InstanceQueryView",
          body = """
            @View
            class InstanceQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              @ViewQuery
              val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
            }
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery must be a companion or static property",
        "InstanceQueryView.QUERY"
      )
  }

  @Test
  fun `rejects an explicitly nullable defining query`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "NullableQueryView",
          body = """
            @View
            data class NullableQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1>? = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery property must not be nullable",
        "NullableQueryView.QUERY"
      )
  }

  @Test
  fun `rejects a defining query with the wrong type`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "WrongQueryTypeView",
          body = """
            @View
            data class WrongQueryTypeView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: String = "not a compiled select"
              }
            }
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery property must have a CompiledSelect type",
        "WrongQueryTypeView.QUERY"
      )
  }

  @Test
  fun `accepts companion queries with and without JvmField`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "CompanionQuerySurfaces",
          body = """
            @View
            data class JvmFieldQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @JvmField
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View
            data class KotlinPropertyQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "JvmFieldQueryViewTable.kt",
        "KotlinPropertyQueryViewTable.kt"
      )
  }

  @Test
  fun `accepts a custom view name`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "CustomNamedView",
          body = """
            @View("custom_view_name")
            data class CustomNamedView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("CustomNamedViewTable.kt")
  }

  @Test
  fun `accepts inferred explicit single and multi generic query surfaces`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "QueryTypeVariants",
          body = """
            data class QueryRow(val value: String)

            @View
            data class InferredSingleQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY = compileOnlySelect<String, Select1>()
              }
            }

            @View
            data class ExplicitMultiQueryView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<QueryRow, SelectN> = compileOnlySelect()
              }
            }

            @View
            data class DifferentRowTypeView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<QueryRow, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "InferredSingleQueryViewTable.kt",
        "ExplicitMultiQueryViewTable.kt",
        "DifferentRowTypeViewTable.kt"
      )
  }
}
