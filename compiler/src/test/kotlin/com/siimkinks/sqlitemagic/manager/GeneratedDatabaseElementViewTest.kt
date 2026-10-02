package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.view.ViewElement
import com.siimkinks.sqlitemagic.view.ViewSources
import com.siimkinks.sqlitemagic.view.viewProcessingSteps
import org.junit.jupiter.api.Test

internal class GeneratedDatabaseElementViewTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `exposes views in declaration order and opens the view-only generation gate`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "GeneratedViews",
          body = """
            @View("first_view")
            data class FirstView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("second_view")
            data class SecondView(
              @ViewColumn("value") val value: String
            ) {
              companion object {
                @ViewQuery
                val query: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .apply {
        val database = GeneratedDatabaseElement.from(environment)

        assertThat(database.views.map(ViewElement::viewName))
          .containsExactly("first_view", "second_view")
          .inOrder()
        assertThat(database.views.map(ViewElement::declarationOrder))
          .containsExactlyElementsIn(database.views.map(ViewElement::declarationOrder).sorted())
          .inOrder()
        assertThat(database.shouldGenerate)
          .isTrue()
      }
  }
}
