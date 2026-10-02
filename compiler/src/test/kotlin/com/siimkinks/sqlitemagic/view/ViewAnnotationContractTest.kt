package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

internal class ViewAnnotationContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `recognizes exact annotation identities`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "ExactAnnotationIdentity.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.CompiledSelect
            import com.siimkinks.sqlitemagic.Select.Select1

            @Target(AnnotationTarget.CLASS)
            annotation class View

            @Target(AnnotationTarget.FIELD)
            annotation class ViewColumn(val value: String)

            @Target(AnnotationTarget.FIELD)
            annotation class ViewQuery

            private fun <T, S> compileOnlySelect(): CompiledSelect<T, S> = error("compile-only")

            @View
            class ForeignView

            @com.siimkinks.sqlitemagic.annotation.View
            data class ExactIdentityView(
              @com.siimkinks.sqlitemagic.annotation.ViewColumn("value")
              val value: String
            ) {
              companion object {
                @com.siimkinks.sqlitemagic.annotation.ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("ExactIdentityViewTable.kt")
      .assertNotGeneratedSources(
        "ForeignViewTable.kt",
        "SqliteMagic_ForeignView_Dao.kt"
      )
  }

  @Test
  fun `ignores ordinary members and unowned view columns`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "IgnoredMembersView",
          body = """
            class UnownedColumnHolder {
              @ViewColumn("orphan_value")
              val orphanValue: String = ""
            }

            @View
            data class IgnoredMembersView(
              @ViewColumn("value")
              val value: String
            ) {
              val ordinaryMember: String = ""

              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("IgnoredMembersViewTable.kt")
      .assertNotGeneratedSources(
        "UnownedColumnHolderTable.kt",
        "SqliteMagic_UnownedColumnHolder_Dao.kt"
      )
      .withGeneratedSource("IgnoredMembersViewTable.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "ordinaryMember",
          "orphanValue"
        )
      }
  }

  @Test
  fun `rejects an orphan view query`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "OrphanQuery",
          body = """
            @ViewQuery
            val ORPHAN_QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
          """
        )
      )
      .assertCompilationError(
        "@ViewQuery must be declared directly inside a @View",
        "ORPHAN_QUERY"
      )
  }

  @Test
  fun `rejects a table and view annotation conflict`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "TableViewConflict",
          body = """
            @View
            @Table("table_view_conflict")
            data class TableViewConflict(
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
        "@Table and @View cannot be used together",
        "TableViewConflict"
      )
  }

  @Test
  fun `rejects table-only annotations on view properties`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "PropertyAnnotationConflict",
          body = """
            @View
            data class PropertyAnnotationConflict(
              @ViewColumn("value")
              @Column
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
        "@Column is not valid on a @View property",
        "PropertyAnnotationConflict.value"
      )
  }
}
