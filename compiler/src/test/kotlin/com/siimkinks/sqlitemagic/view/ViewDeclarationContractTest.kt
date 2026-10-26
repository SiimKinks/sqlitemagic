package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class ViewDeclarationContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @Test
  fun `generates data, non-data, inherited mutable and nested internal views`() {
    compiler
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        ),
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
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "DataClassViewTable.kt",
        "NonDataConstructorViewTable.kt",
        "MutableInheritedViewTable.kt",
        "ViewContainer_NestedInternalViewTable.kt"
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidViewModels")
  fun `view models outside their contracts do not compile`(
    viewName: String,
    body: String,
    diagnostic: String,
    subject: String
  ) {
    compiler
      .compile(
        ViewSources.view(
          name = viewName,
          body = body
        )
      )
      .assertCompilationError(diagnostic, subject)
  }

  companion object {
    @JvmStatic
    fun invalidViewModels() = listOf(
      Arguments.of(
        "GenericView",
        """
          @View
          class GenericView<T>(
            @ViewColumn("value")
            val value: T
          ) {
            companion object {
              @ViewQuery
              val QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "Generic @View models are unsupported",
        "GenericView"
      ),
      Arguments.of(
        "InnerView",
        """
          class ViewContainer {
            @View
            inner class InnerView(
              @ViewColumn("value")
              val value: String
            )
          }
        """,
        "Inner @View models are unsupported",
        "ViewContainer.InnerView"
      ),
      Arguments.of(
        "ObjectView",
        """
          @View
          object ObjectView {
            @ViewColumn("value")
            var value: String = ""

            @ViewQuery
            val QUERY: SelectQuery<String, *> = error("compile-only")
          }
        """,
        "Unsupported @View model shape",
        "ObjectView"
      ),
      Arguments.of(
        "AbstractView",
        """
          @View
          abstract class AbstractView(
            @ViewColumn("value")
            val value: String
          ) {
            companion object {
              @ViewQuery
              val QUERY: SelectQuery<String, *> = error("compile-only")
            }
          }
        """,
        "Unsupported @View model shape",
        "AbstractView"
      )
    )
  }
}
