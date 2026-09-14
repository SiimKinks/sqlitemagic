package com.siimkinks.sqlitemagic.view

import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
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
