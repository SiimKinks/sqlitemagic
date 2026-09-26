package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import org.junit.Assert.assertThrows
import org.junit.Test

internal class QueryDependenciesTest {
  @Test
  fun sqliteSchemaIdentityPreservesRawSpellingAndUsesAsciiCaseFoldingForKeys() {
    val cases = listOf(
      Triple(
        first = MAIN,
        second = "Books",
        third = "bOOKS"
      ),
      Triple(
        first = TEMPORARY,
        second = "Reports",
        third = "rEPORTS"
      )
    )

    for ((schema, firstName, secondName) in cases) {
      val first = SqliteSchemaIdentity.from(
        schema = schema,
        rawName = firstName
      )
      val second = SqliteSchemaIdentity.from(
        schema = schema,
        rawName = secondName
      )
      assertWithMessage("ASCII case in $schema")
        .that(first)
        .isNotEqualTo(second)
      assertWithMessage("normalized key in $schema")
        .that(first.normalizedKey)
        .isEqualTo(second.normalizedKey)
    }

    val main = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "Books"
    )
    val temporary = SqliteSchemaIdentity.from(
      schema = TEMPORARY,
      rawName = "BOOKS"
    )
    assertThat(main.normalizedKey)
      .isEqualTo(
        SqliteSchemaKey(
          schema = MAIN,
          normalizedName = "books"
        )
      )
    assertThat(main.normalizedKey)
      .isNotEqualTo(temporary.normalizedKey)

    val accentedUpper = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "Ärger"
    )
    val accentedLower = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "ärger"
    )
    assertThat(accentedUpper.normalizedKey)
      .isEqualTo(
        SqliteSchemaKey(
          schema = MAIN,
          normalizedName = "Ärger"
        )
      )
    assertThat(accentedUpper.normalizedKey)
      .isNotEqualTo(accentedLower.normalizedKey)
  }

  @Test
  fun builderDeduplicatesDirectSourcesByIdentityInFirstSeenOrder() {
    val books = SqliteQuerySource(
      identity = SqliteSchemaIdentity.from(
        schema = MAIN,
        rawName = "Books"
      ),
      kind = TABLE
    )
    val reports = SqliteQuerySource(
      identity = SqliteSchemaIdentity.from(
        schema = TEMPORARY,
        rawName = "reports"
      ),
      kind = VIEW
    )
    val builder = QueryDependencies.Builder()
    listOf(
      books,
      SqliteQuerySource(
        identity = SqliteSchemaIdentity.from(
          schema = MAIN,
          rawName = "books"
        ),
        kind = TABLE
      ),
      reports,
      books,
      SqliteQuerySource(
        identity = SqliteSchemaIdentity.from(
          schema = TEMPORARY,
          rawName = "REPORTS"
        ),
        kind = VIEW
      )
    ).forEach(builder::addSource)

    val dependencies = builder.build()
    assertThat(dependencies.directSources)
      .containsExactly(books, reports)
      .inOrder()
    assertThat(dependencies.directSources.map(SqliteQuerySource::name))
      .containsExactly("Books", "reports")
      .inOrder()
  }

  @Test
  fun builderRejectsConflictingKindsForOneIdentity() {
    val identity = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = "report"
    )
    val builder = QueryDependencies.Builder()
    builder.addSource(SqliteQuerySource(identity = identity, kind = TABLE))

    val failure = assertThrows(IllegalArgumentException::class.java) {
      builder.addSource(
        SqliteQuerySource(
          identity = SqliteSchemaIdentity.from(
            schema = MAIN,
            rawName = "REPORT"
          ),
          kind = VIEW
        )
      )
    }
    assertThat(failure)
      .hasMessageThat()
      .contains("Conflicting SQLite source kinds")
  }

  @Test
  fun rawWhereExpressionRetainsKnownSourceButMarksDirectSourcesIncomplete() {
    val actual = Select
      .from(sourceTable("root"))
      .where(Expr.raw("EXISTS (SELECT 1 FROM main.hidden)"))
      .compile() as CompiledSelectDetails

    assertThat(actual.sql)
      .contains("EXISTS (SELECT 1 FROM main.hidden)")
    assertThat(actual.observedTables)
      .isSameInstanceAs(actual.queryDependencies.observedTables)
    assertThat(actual.queryDependencies.directSources)
      .containsExactly(tableSource("root"))
    assertThat(actual.queryDependencies.observedTables.asList())
      .containsExactly("root")
    assertThat(actual.queryDependencies.directSourcesComplete)
      .isFalse()
  }

  @Test
  fun rawSelectedColumnCopiesRemainIncompleteAndViewDefinitionRetainsThatState() {
    val rawColumn = Select.asRawColumn("42")
      .`as`("answer")
      .inTable(Table.ANONYMOUS_TABLE)
    val actual = Select
      .column(rawColumn)
      .from(sourceTable("root"))
      .compile() as CompiledSelectDetails
    val definition = SqlUtil.viewDefinition(
      query = actual as CompiledSelect<*, *>,
      viewName = "raw_view"
    )

    assertThat(actual.sql)
      .contains("SELECT 42 AS 'answer' FROM main.root")
    assertThat(actual.observedTables)
      .isSameInstanceAs(actual.queryDependencies.observedTables)
    assertThat(actual.queryDependencies.directSources)
      .containsExactly(tableSource("root"))
    assertThat(actual.queryDependencies.directSourcesComplete)
      .isFalse()
    assertThat(definition.queryDependencies.directSources)
      .containsExactly(tableSource("root"))
    assertThat(definition.queryDependencies.directSourcesComplete)
      .isFalse()
  }

  @Test
  fun rawExpressionInFrozenNestedFragmentKeepsOuterAndInnerKnownSources() {
    val nested = Select
      .column(Select.asColumn(1))
      .from(sourceTable("nested"))
      .where(Expr.raw("EXISTS (SELECT 1 FROM main.hidden)"))
      .toColumn("nested_value")
    val actual = Select
      .columns(nested)
      .from(sourceTable("root"))
      .compile() as CompiledSelectDetails

    assertThat(actual.sql)
      .contains("(SELECT 1 FROM main.nested WHERE EXISTS (SELECT 1 FROM main.hidden)")
    assertThat(actual.queryDependencies.directSources)
      .containsExactly(tableSource("root"), tableSource("nested"))
    assertThat(actual.queryDependencies.observedTables.asList())
      .containsExactly("root", "nested")
    assertThat(actual.queryDependencies.directSourcesComplete)
      .isFalse()
  }

  @Test
  fun emptyMembershipExpressionKeepsDirectSourcesComplete() {
    val actual = Select
      .from(sourceTable("root"))
      .where(Select.asColumn(1).`in`(emptyList()))
      .compile() as CompiledSelectDetails

    assertThat(actual.sql)
      .contains("WHERE ?")
    assertThat(actual.args?.asList())
      .containsExactly("0")
    assertThat(actual.queryDependencies.directSources)
      .containsExactly(tableSource("root"))
    assertThat(actual.queryDependencies.directSourcesComplete)
      .isTrue()
  }

  private fun tableSource(name: String) = SqliteQuerySource(
    identity = SqliteSchemaIdentity.from(
      schema = MAIN,
      rawName = name
    ),
    kind = TABLE
  )

  private fun sourceTable(name: String) = testTable<Any>(
    name = name,
    mapper = { _, _, _ -> Query.Mapper { Any() } }
  )
}
