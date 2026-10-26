package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import org.junit.jupiter.api.Test

internal class ViewNamingContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @Test
  fun `uses default explicit and nested view names`() {
    GENERATED
      .isOk()
      .assertGeneratedSources(
        "DefaultNamedViewTable.kt",
        "ExplicitNamedViewTable.kt",
        "NameContainer_NestedNamedViewTable.kt"
      )
  }

  @Test
  fun `derives view members from model names while preserving SQL names`() {
    GENERATED
      .isOk()
      .apply {
        listOf(
          NamingCase(
            modelName = "DottedIdentifierView",
            memberName = "DOTTED_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report.v1""""
          ),
          NamingCase(
            modelName = "SpacedIdentifierView",
            memberName = "SPACED_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report space""""
          ),
          NamingCase(
            modelName = "QuotedIdentifierView",
            memberName = "QUOTED_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report\"quote""""
          ),
          NamingCase(
            modelName = "KeywordIdentifierView",
            memberName = "KEYWORD_IDENTIFIER_VIEW",
            sqlNameLiteral = """"select""""
          ),
          NamingCase(
            modelName = "PunctuationIdentifierView",
            memberName = "PUNCTUATION_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report!?'@""""
          ),
          NamingCase(
            modelName = "NonbreakingSpaceIdentifierView",
            memberName = "NONBREAKING_SPACE_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report${'\u00A0'}space""""
          ),
          NamingCase(
            modelName = "HyphenIdentifierView",
            memberName = "HYPHEN_IDENTIFIER_VIEW",
            sqlNameLiteral = """"report-ok""""
          ),
          NamingCase(
            modelName = "AccentedIdentifierView",
            memberName = "ACCENTED_IDENTIFIER_VIEW",
            sqlNameLiteral = """"café""""
          )
        ).forEach { (modelName, memberName, sqlNameLiteral) ->
          withGeneratedSource("${modelName}Table.kt") {
            it.assertContains(
              "val $memberName:",
              "name = $sqlNameLiteral"
            )
          }
          withGeneratedSource("SqliteMagic_${modelName}_Dao.kt") {
            it.assertContains("viewName = $sqlNameLiteral")
          }
        }
      }
  }

  @Test
  fun `rejects a blank view column key`() {
    compiler
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
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
    GENERATED
      .isOk()
      .assertGeneratedSources("DottedQuotedKeysViewTable.kt")
  }

  @Test
  fun `rejects duplicate resolved view column keys`() {
    compiler
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
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
    compiler
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
                  val QUERY: SelectQuery<String, *> = error("compile-only")
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
                  val QUERY: SelectQuery<String, *> = error("compile-only")
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
    compiler
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
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
    compiler
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
                val QUERY: SelectQuery<String, *> = error("compile-only")
              }
            }
          """
        )
      )
      .assertCompilationError(
        "Index name 'shared_schema_name' conflicts with view name 'SHARED_SCHEMA_NAME'"
      )
  }

  private data class NamingCase(
    val modelName: String,
    val memberName: String,
    val sqlNameLiteral: String
  )

  companion object {
    private val GENERATED by lazy {
      SqliteMagicCompilation
        .compile(
          ViewSources.view(
            name = "ViewNames",
            body = """
              import com.siimkinks.sqlitemagic.DefaultNamedViewTable.Companion.DEFAULT_NAMED_VIEW
              import com.siimkinks.sqlitemagic.ExplicitNamedViewTable.Companion.EXPLICIT_NAMED_VIEW
              import com.siimkinks.sqlitemagic.NameContainer_NestedNamedViewTable

              @View
              data class DefaultNamedView(
                @ViewColumn("value")
                val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("explicit_view_name")
              data class ExplicitNamedView(
                @ViewColumn("value")
                val value: String
              ) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
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
                    val QUERY: SelectQuery<String, *> = error("compile-only")
                  }
                }
              }

              fun generatedViewMembers() = listOf(
                DEFAULT_NAMED_VIEW,
                EXPLICIT_NAMED_VIEW,
                NameContainer_NestedNamedViewTable.NAME_CONTAINER__NESTED_NAMED_VIEW
              )
            """
          ),
          ViewSources.view(
            name = "UnusualViewIdentifiers",
            body = """
              import com.siimkinks.sqlitemagic.AccentedIdentifierViewTable.Companion.ACCENTED_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.DottedIdentifierViewTable.Companion.DOTTED_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.HyphenIdentifierViewTable.Companion.HYPHEN_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.KeywordIdentifierViewTable.Companion.KEYWORD_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.NonbreakingSpaceIdentifierViewTable
              import com.siimkinks.sqlitemagic.PunctuationIdentifierViewTable.Companion.PUNCTUATION_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.QuotedIdentifierViewTable.Companion.QUOTED_IDENTIFIER_VIEW
              import com.siimkinks.sqlitemagic.SpacedIdentifierViewTable.Companion.SPACED_IDENTIFIER_VIEW

              @View("report.v1")
              data class DottedIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("report space")
              data class SpacedIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("report\"quote")
              data class QuotedIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("select")
              data class KeywordIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("report!?'@")
              data class PunctuationIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("report\u00A0space")
              data class NonbreakingSpaceIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("report-ok")
              data class HyphenIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              @View("café")
              data class AccentedIdentifierView(@ViewColumn("value") val value: String) {
                companion object {
                  @ViewQuery
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }

              fun unusualIdentifierViewMembers() = listOf(
                DOTTED_IDENTIFIER_VIEW,
                SPACED_IDENTIFIER_VIEW,
                QUOTED_IDENTIFIER_VIEW,
                KEYWORD_IDENTIFIER_VIEW,
                PUNCTUATION_IDENTIFIER_VIEW,
                NonbreakingSpaceIdentifierViewTable.NONBREAKING_SPACE_IDENTIFIER_VIEW,
                HYPHEN_IDENTIFIER_VIEW,
                ACCENTED_IDENTIFIER_VIEW
              )
            """
          ),
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
                  val QUERY: SelectQuery<String, *> = error("compile-only")
                }
              }
            """
          ),
          processingStepsFactory = ::viewProcessingSteps
        )
        .generatedSources()
    }
  }
}
