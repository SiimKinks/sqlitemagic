package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import org.junit.Test

internal class RawSelectDependencyTest {
  @Test
  fun noFromDeclarationHasIncompleteEmptySnapshot() {
    val compiled = Select.raw("SELECT 1").compile() as RawSelect.CompiledRawSelectImpl

    assertThat(compiled.queryDependencies.directSourcesComplete).isFalse()
    assertThat(compiled.queryDependencies.directSources).isEmpty()
    assertThat(compiled.queryDependencies.observedTables).isEmpty()
    assertThat(compiled.observedTables)
      .isSameInstanceAs(compiled.queryDependencies.observedTables)
  }

  @Test
  fun varargFromDeduplicatesAliasesRepeatedTablesAndCaseVariantsByDirectIdentity() {
    val books = testTable<Any>(name = "Books")
    val alias = testTable<Any>(name = "Books", alias = "left")
    val caseVariant = testTable<Any>(name = "bOOKS", alias = "right")
    val compiled = Select
      .raw("SELECT * FROM main.Books")
      .from(alias, books, alias, caseVariant)
      .compile() as RawSelect.CompiledRawSelectImpl

    assertThat(compiled.queryDependencies.directSourcesComplete).isTrue()
    assertThat(compiled.queryDependencies.directSources).containsExactly(
      SqliteQuerySource(schema = SqliteSchema.MAIN, name = "Books")
    )
    assertThat(compiled.queryDependencies.observedTables.asList())
      .containsExactly("Books", "bOOKS")
      .inOrder()
    assertThat(compiled.observedTables)
      .asList()
      .containsExactly("Books", "bOOKS")
      .inOrder()
  }

  @Test
  fun collectionFromKeepsMainAndTemporaryNamesDistinct() {
    val main = testTable<Any>(name = "items")
    val temporary = testTable<Any>(name = "items", temporary = true)
    val compiled = Select
      .raw("SELECT * FROM main.items")
      .from(listOf(temporary, main))
      .compile() as RawSelect.CompiledRawSelectImpl

    assertThat(compiled.queryDependencies.directSourcesComplete).isTrue()
    assertThat(compiled.queryDependencies.directSources)
      .containsExactly(
        SqliteQuerySource(schema = SqliteSchema.TEMPORARY, name = "items"),
        SqliteQuerySource(schema = SqliteSchema.MAIN, name = "items")
      )
      .inOrder()
    assertThat(compiled.queryDependencies.observedTables.asList())
      .containsExactly("items")
    assertThat(compiled.observedTables)
      .asList()
      .containsExactly("items")
  }

  @Test
  fun declaredViewIsDirectSourceAndItsDefinitionTableIsObserved() {
    val authors = testTable<Any>(name = "authors")
    val definitionDependencies = QueryDependencies.Builder()
      .also(authors::addDependencies)
      .build()
    val definition = ViewDefinition(
      sql = "SELECT * FROM main.authors",
      args = null,
      queryDependencies = definitionDependencies,
      columns = null,
      tableGraphNodeNames = null,
      queryDeep = false
    )
    val view = TestViewTable(definition = definition)
    val compiled = Select
      .raw("SELECT * FROM main.report")
      .from(view)
      .compile() as RawSelect.CompiledRawSelectImpl

    assertThat(compiled.queryDependencies.directSourcesComplete).isTrue()
    assertThat(compiled.queryDependencies.directSources).containsExactly(
      SqliteQuerySource(
        schema = SqliteSchema.MAIN,
        name = "report",
        kind = SqliteObjectKind.VIEW
      )
    )
    assertThat(compiled.queryDependencies.observedTables.asList())
      .containsExactly("authors")
    assertThat(compiled.observedTables)
      .asList()
      .containsExactly("authors")
  }

  private class TestViewTable(definition: ViewDefinition) : Table<Any>(
    name = "report",
    alias = null,
    nrOfColumns = 1,
    viewDefinition = { definition }
  )
}
