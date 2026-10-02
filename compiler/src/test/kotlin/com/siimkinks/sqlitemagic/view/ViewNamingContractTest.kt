package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.assertContains
import org.junit.jupiter.api.Test

internal class ViewNamingContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @Test
  fun `uses default explicit and nested view names`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "ViewNames",
          body = """
            import com.siimkinks.sqlitemagic.DefaultNamedViewTable.Companion.DEFAULT_NAMED_VIEW
            import com.siimkinks.sqlitemagic.ExplicitNamedViewTable.Companion.EXPLICIT_NAMED_VIEW
            import com.siimkinks.sqlitemagic.NameContainer_NestedNamedViewTable.Companion.NAME_CONTAINER__NESTED_NAMED_VIEW

            @View
            data class DefaultNamedView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("explicit_view_name")
            data class ExplicitNamedView(
              @ViewColumn("value")
              val value: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            class NameContainer {
              @View
              data class NestedNamedView(
                @ViewColumn("value")
                val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
                }
              }
            }

            fun generatedViewMembers() = listOf(
              DEFAULT_NAMED_VIEW,
              EXPLICIT_NAMED_VIEW,
              NAME_CONTAINER__NESTED_NAMED_VIEW
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources(
        "DefaultNamedViewTable.kt",
        "ExplicitNamedViewTable.kt",
        "NameContainer_NestedNamedViewTable.kt"
      )
  }

  @Test
  fun `derives view members from model names while preserving SQL names`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "UnusualViewIdentifiers",
          body = """
            import com.siimkinks.sqlitemagic.AccentedIdentifierViewTable.Companion.ACCENTED_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.DottedIdentifierViewTable.Companion.DOTTED_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.HyphenIdentifierViewTable.Companion.HYPHEN_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.KeywordIdentifierViewTable.Companion.KEYWORD_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.NonbreakingSpaceIdentifierViewTable.Companion.NONBREAKING_SPACE_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.PunctuationIdentifierViewTable.Companion.PUNCTUATION_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.QuotedIdentifierViewTable.Companion.QUOTED_IDENTIFIER_VIEW
            import com.siimkinks.sqlitemagic.SpacedIdentifierViewTable.Companion.SPACED_IDENTIFIER_VIEW

            @View("report.v1")
            data class DottedIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("report space")
            data class SpacedIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("report\"quote")
            data class QuotedIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("select")
            data class KeywordIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("report!?'@")
            data class PunctuationIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("report\u00A0space")
            data class NonbreakingSpaceIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("report-ok")
            data class HyphenIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            @View("café")
            data class AccentedIdentifierView(@ViewColumn("value") val value: String) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
              }
            }

            fun generatedViewMembers() = listOf(
              DOTTED_IDENTIFIER_VIEW,
              SPACED_IDENTIFIER_VIEW,
              QUOTED_IDENTIFIER_VIEW,
              KEYWORD_IDENTIFIER_VIEW,
              PUNCTUATION_IDENTIFIER_VIEW,
              NONBREAKING_SPACE_IDENTIFIER_VIEW,
              HYPHEN_IDENTIFIER_VIEW,
              ACCENTED_IDENTIFIER_VIEW
            )
          """
        )
      )
      .isOk()
      .apply {
        listOf(
          Triple(
            first = "DottedIdentifierView",
            second = "DOTTED_IDENTIFIER_VIEW",
            third = """"report.v1""""
          ),
          Triple(
            first = "SpacedIdentifierView",
            second = "SPACED_IDENTIFIER_VIEW",
            third = """"report space""""
          ),
          Triple(
            first = "QuotedIdentifierView",
            second = "QUOTED_IDENTIFIER_VIEW",
            third = """"report\"quote""""
          ),
          Triple(
            first = "KeywordIdentifierView",
            second = "KEYWORD_IDENTIFIER_VIEW",
            third = """"select""""
          ),
          Triple(
            first = "PunctuationIdentifierView",
            second = "PUNCTUATION_IDENTIFIER_VIEW",
            third = """"report!?'@""""
          ),
          Triple(
            first = "NonbreakingSpaceIdentifierView",
            second = "NONBREAKING_SPACE_IDENTIFIER_VIEW",
            third = """"report${'\u00A0'}space""""
          ),
          Triple(
            first = "HyphenIdentifierView",
            second = "HYPHEN_IDENTIFIER_VIEW",
            third = """"report-ok""""
          ),
          Triple(
            first = "AccentedIdentifierView",
            second = "ACCENTED_IDENTIFIER_VIEW",
            third = """"café""""
          )
        ).forEach { (modelName, memberName, sqlNameLiteral) ->
          withGeneratedSource("${modelName}Table.kt") { generatedSource ->
            generatedSource.assertContains(
              "val $memberName:",
              "name = $sqlNameLiteral"
            )
          }
          withGeneratedSource("SqliteMagic_${modelName}_Dao.kt") { generatedSource ->
            generatedSource.assertContains("viewName = $sqlNameLiteral")
          }
        }
      }
  }

  @Test
  fun `rejects a blank view column key`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "BlankKeyView",
          body = """
            @View
            data class BlankKeyView(
              @ViewColumn("")
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
        "@ViewColumn key must not be blank",
        "BlankKeyView.value"
      )
  }

  @Test
  fun `preserves dotted and quoted view column keys`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "DottedQuotedKeysView",
          body = """
            @View
            data class DottedQuotedKeysView(
              @ViewColumn("author.name")
              val authorName: String,
              @ViewColumn("\"quoted_alias\"")
              val quotedAlias: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, SelectN> = compileOnlySelect()
              }
            }
          """
        )
      )
      .isOk()
      .assertGeneratedSources("DottedQuotedKeysViewTable.kt")
  }

  @Test
  fun `rejects duplicate resolved view column keys`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "DuplicateViewColumnKeys",
          body = """
            @View
            data class DuplicateViewColumnKeys(
              @ViewColumn("same_key")
              val firstValue: String,
              @ViewColumn("same_key")
              val secondValue: String
            ) {
              companion object {
                @ViewQuery
                val QUERY: CompiledSelect<String, SelectN> = compileOnlySelect()
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Duplicate @ViewColumn key 'same_key'",
        "DuplicateViewColumnKeys.firstValue",
        "DuplicateViewColumnKeys.secondValue"
      )
  }

  @Test
  fun `rejects colliding nested view artifact stems`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "CollidingNestedViews",
          body = """
            class Outer_WithUnderscore {
              @View
              class NestedView(
                @ViewColumn("value")
                val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: CompiledSelect<String, Select1> = compileOnlySelect()
                }
              }
            }

            class Outer {
              @View
              class WithUnderscore_NestedView(
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
      .assertCompilationError(
        "declaration paths map to the same generated name",
        "Outer_WithUnderscore.NestedView",
        "Outer.WithUnderscore_NestedView"
      )
  }

  @Test
  fun `rejects a view name colliding with a table identity`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "ViewTableNameCollision",
          body = """
            @Table("shared_schema_name")
            data class ExistingTable(
              @Column
              val value: String
            )

            @View("SHARED_SCHEMA_NAME")
            data class ConflictingSchemaView(
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
        "View name 'SHARED_SCHEMA_NAME' conflicts with table name 'shared_schema_name'",
        "ConflictingSchemaView"
      )
  }

  @Test
  fun `rejects a view name colliding with an index identity`() {
    SqliteMagicCompilation
      .compile(
        ViewSources.view(
          name = "ViewIndexNameCollision",
          body = """
            import com.siimkinks.sqlitemagic.annotation.Index

            @Table
            data class IndexedTable(
              @Column
              @Index("shared_schema_name")
              val value: String
            )

            @View("SHARED_SCHEMA_NAME")
            data class ConflictingSchemaView(
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
        "Index name 'shared_schema_name' conflicts with view name 'SHARED_SCHEMA_NAME'"
      )
  }
}
