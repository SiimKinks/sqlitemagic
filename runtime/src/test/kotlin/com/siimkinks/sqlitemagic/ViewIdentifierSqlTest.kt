package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.Utils.STRING_PARSER
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import org.junit.Test

internal class ViewIdentifierSqlTest {
  @Test
  fun generatedViewNamesRenderAsSqlIdentifiersWhileMappingKeysRemainRaw() {
    val cases = listOf(
      "dotted" to "report.v1",
      "spaced" to "report space",
      "embedded quote" to "report\"quote",
      "keyword" to "select"
    )

    for ((label, rawName) in cases) {
      val view = IdentifierViewTable(
        view = GeneratedView(
          viewName = rawName,
          temporary = false,
          definitionProvider = { definition }
        )
      )
      val column = nameColumn(view)
      val quotedName = SqlUtil.quoteSqlIdentifier(rawName)
      val compiled = Select
        .columns(column)
        .from(view)
        .where(column.`is`("Ada"))
        .compile() as CompiledSelectImpl<*, *>

      assertWithMessage(label)
        .that(compiled.sql)
        .contains("SELECT $quotedName.name FROM main.$quotedName WHERE $quotedName.name=?")
      assertWithMessage("$label mapping key")
        .that(column.nameInQuery)
        .isEqualTo("$rawName.name")
      assertWithMessage("$label position")
        .that(compiled.columns?.get("$rawName.name"))
        .isEqualTo(0)
      assertWithMessage("$label dependency")
        .that(compiled.queryDependencies.directSources)
        .containsExactly(
          SqliteQuerySource(
            schema = MAIN,
            name = rawName,
            kind = SqliteObjectKind.VIEW
          )
        )

      val alias = view.`as`("safe_alias")
      val aliasedColumn = nameColumn(alias)
      val aliased = Select
        .columns(aliasedColumn)
        .from(alias)
        .compile() as CompiledSelectImpl<*, *>
      assertWithMessage("$label alias")
        .that(aliased.sql)
        .contains("SELECT safe_alias.name FROM main.$quotedName AS safe_alias")
      assertWithMessage("$label alias position")
        .that(aliased.columns?.get("safe_alias.name"))
        .isEqualTo(0)

      val internalAlias = view.internalAlias("internal_alias")
      val internalColumn = nameColumn(internalAlias)
      assertWithMessage("$label internal alias")
        .that(buildString { internalAlias.appendToSqlFromClause(this) })
        .isEqualTo("main.$quotedName AS internal_alias")
      assertWithMessage("$label internal column")
        .that(buildString { internalColumn.appendSql(this) })
        .isEqualTo("internal_alias.name")
      assertWithMessage("$label internal alias mapping")
        .that(internalColumn.nameInQuery)
        .isEqualTo("internal_alias.name")
      assertWithMessage("$label internal alias keeps no definition")
        .that(internalAlias.hasViewDefinitionPositions)
        .isFalse()
    }
  }

  @Test
  fun legacyViewDefinitionUsesTheSameIdentifierRendering() {
    val view = object : Table<Any>(
      name = "legacy.view",
      alias = null,
      nrOfColumns = 1,
      mapper = { _, _, _ -> Query.Mapper { Any() } },
      viewDefinition = { definition }
    ) {}
    val column = nameColumn(view)
    val compiled = Select
      .columns(column)
      .from(view)
      .compile() as CompiledSelectImpl<*, *>

    assertThat(compiled.sql)
      .contains("SELECT \"legacy.view\".name FROM main.\"legacy.view\"")
    assertThat(compiled.columns?.get("legacy.view.name"))
      .isEqualTo(0)

    val alias = view.`as`("safe_alias")
    val aliasedColumn = nameColumn(alias)
    assertThat(buildString { alias.appendToSqlFromClause(this) })
      .isEqualTo("main.\"legacy.view\" AS safe_alias")
    assertThat(buildString { aliasedColumn.appendSql(this) })
      .isEqualTo("safe_alias.name")
    assertThat(aliasedColumn.nameInQuery)
      .isEqualTo("safe_alias.name")
    assertThat(alias.hasViewDefinitionPositions)
      .isFalse()
  }

  private fun nameColumn(table: Table<Any>) = Column<String, String, CharSequence, Any, NotNullable>(
    table = table,
    name = "name",
    valueParser = STRING_PARSER,
    nullable = false,
    alias = null
  )

  private val definition = ViewDefinition(
    sql = "SELECT name FROM authors",
    args = null,
    queryDependencies = QueryDependencies.Builder()
      .addSource(
        SqliteQuerySource(
          schema = MAIN,
          name = "authors"
        )
      )
      .build(),
    columns = SimpleArrayMap<String, Int>().apply { put("name", 0) },
    tableGraphNodeNames = null,
    queryDeep = false
  )

  private class IdentifierViewTable(
    private val view: GeneratedView,
    alias: String? = null
  ) : Table<Any>(
    name = view.viewName,
    alias = alias,
    nrOfColumns = 1,
    mapper = { _, _, _ -> Query.Mapper { Any() } },
    generatedView = view
  ) {
    override fun `as`(alias: String) = IdentifierViewTable(
      view = view,
      alias = alias
    )
  }
}
