package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import org.junit.jupiter.api.Test

internal class ViewDeclarationContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `generates a data class constructor-backed view`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "DataClassView",
          body = """
            @View
            data class DataClassView(
              @ViewColumn("display_name")
              val displayName: String
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
      .assertGeneratedSources("DataClassViewTable.kt")
  }

  @Test
  fun `generates a non-data constructor-backed view`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "NonDataConstructorView",
          body = """
            @View
            class NonDataConstructorView(
              @ViewColumn("display_name")
              val displayName: String
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
      .assertGeneratedSources("NonDataConstructorViewTable.kt")
  }

  @Test
  fun `generates a mutable view with inherited properties`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "MutableInheritedView",
          body = """
            open class ViewBase {
              @ViewColumn("base_name")
              open var baseName: String = ""
            }

            @View
            class MutableInheritedView : ViewBase() {
              @ViewColumn("local_name")
              var localName: String = ""

              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("MutableInheritedViewTable.kt")
  }

  @Test
  fun `generates a nested internal view`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "NestedInternalView",
          body = """
            class ViewContainer {
              @View
              internal class NestedInternalView(
                @ViewColumn("value")
                val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
                }
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("ViewContainer_NestedInternalViewTable.kt")
  }

  @Test
  fun `rejects generic view models`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "GenericView",
          body = """
            @View
            class GenericView<T>(
              @ViewColumn("value")
              val value: T
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Generic @View models are unsupported",
        "GenericView"
      )
  }

  @Test
  fun `rejects inner view models`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "InnerView",
          body = """
            class ViewContainer {
              @View
              inner class InnerView(
                @ViewColumn("value")
                val value: String
              )
            }
          """
        )
      )
      .assertCompilationError(
        "Inner @View models are unsupported",
        "ViewContainer.InnerView"
      )
  }

  @Test
  fun `rejects object view models`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "ObjectView",
          body = """
            @View
            object ObjectView {
              @ViewColumn("value")
              var value: String = ""

              @ViewQuery
              val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
            }
          """
        )
      )
      .assertCompilationError(
        "Unsupported @View model shape",
        "ObjectView"
      )
  }

  @Test
  fun `rejects abstract view models`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "AbstractView",
          body = """
            @View
            abstract class AbstractView(
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
      .assertCompilationError(
        "Unsupported @View model shape",
        "AbstractView"
      )
  }
}
