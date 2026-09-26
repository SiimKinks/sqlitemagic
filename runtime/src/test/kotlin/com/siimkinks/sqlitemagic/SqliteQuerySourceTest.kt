package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import org.junit.Test

internal class SqliteQuerySourceTest {
  @Test
  fun sameRawNameInMainAndTemporarySchemasRemainsDistinctAcrossAliasesAndOccurrences() {
    val main = sourceTable(name = "books")
    val temporary = sourceTable(
      name = "books",
      temporary = true
    )

    val actual = Select
      .from(main)
      .innerJoin(main.`as`("other_main"))
      .innerJoin(temporary.`as`("first_temp"))
      .innerJoin(temporary.`as`("other_temp"))
      .compile() as CompiledSelectDetails

    assertThat(actual.queryDependencies.directSources)
      .containsExactly(
        SqliteQuerySource(
          schema = SqliteSchema.MAIN,
          name = "books"
        ),
        SqliteQuerySource(
          schema = SqliteSchema.TEMPORARY,
          name = "books"
        )
      )
      .inOrder()
  }

  @Test
  fun rootAndExplicitJoinAreCapturedForImplicitAndProjectedSelections() {
    val root = sourceTable(name = "books")
    val joined = sourceTable(
      name = "authors",
      temporary = true
    )
    val cases = listOf(
      "implicit model selection" to {
        Select
          .from(root)
          .innerJoin(joined)
          .compile()
      },
      "projected scalar selection" to {
        Select
          .columns(Select.asColumn(1))
          .from(root)
          .innerJoin(joined)
          .compile()
      }
    )
    val expected = listOf(
      SqliteQuerySource(
        schema = SqliteSchema.MAIN,
        name = "books"
      ),
      SqliteQuerySource(
        schema = SqliteSchema.TEMPORARY,
        name = "authors"
      )
    )

    for ((label, build) in cases) {
      val actual = build() as CompiledSelectDetails
      assertWithMessage(label)
        .that(actual.queryDependencies.directSources)
        .containsExactlyElementsIn(expected)
        .inOrder()
    }
  }

  @Test
  fun nestedAndCompoundQueriesPropagateTheirSources() {
    val root = sourceTable(name = "books")
    val nested = sourceTable(
      name = "authors",
      temporary = true
    )
    val cases = listOf(
      "FROM subquery" to {
        Select
          .from(Select.from(nested))
          .innerJoin(root)
          .compile()
      },
      "selected scalar subquery" to {
        Select
          .columns(
            Select
              .column(Select.asColumn(1))
              .from(nested)
              .toColumn("author_count")
          )
          .from(root)
          .compile()
      },
      "WHERE expression subquery" to {
        Select
          .from(root)
          .where(
            Select.asColumn(1)
              .`in`(
                Select
                  .column(Select.asColumn(1))
                  .from(nested)
              )
          )
          .compile()
      },
      "compound branch" to {
        Select
          .from(root)
          .union(Select.from(nested))
          .compile()
      }
    )
    val expected = listOf(
      SqliteQuerySource(
        schema = SqliteSchema.MAIN,
        name = "books"
      ),
      SqliteQuerySource(
        schema = SqliteSchema.TEMPORARY,
        name = "authors"
      )
    )

    for ((label, build) in cases) {
      val actual = build() as CompiledSelectDetails
      assertWithMessage(label)
        .that(actual.queryDependencies.directSources)
        .containsExactlyElementsIn(expected)
    }
  }

  @Test
  fun composedExpressionsAndOrderingClausesRetainNestedSourcesAndObservation() {
    val root = sourceTable(name = "books")
    val nested = sourceTable(
      name = "authors",
      temporary = true
    )
    val subquery = {
      Select
        .column(Select.asColumn(1))
        .from(nested)
    }
    val scalar = {
      subquery()
        .toColumn("author_count")
    }
    val cases = listOf(
      "composed WHERE" to {
        val condition = Select.asColumn(1)
          .`in`(subquery())
          .and(Expr.raw("1 = 1"))
        Select
          .from(root)
          .where(!condition)
          .compile()
      },
      "join constraint" to {
        val condition = Select.asColumn(1)
          .`in`(subquery())
        val join = sourceTable(name = "publishers")
          .on(condition)
        Select
          .from(root)
          .innerJoin(join)
          .compile()
      },
      "HAVING" to {
        val condition = Select.asColumn(1)
          .`in`(subquery())
        Select
          .from(root)
          .groupBy(Select.asColumn(1))
          .having(condition)
          .compile()
      },
      "GROUP BY" to {
        Select
          .from(root)
          .groupBy(scalar())
          .compile()
      },
      "ORDER BY" to {
        Select
          .from(root)
          .orderBy(
            scalar()
              .asc()
          )
          .compile()
      }
    )
    val expectedSource = SqliteQuerySource(
      schema = SqliteSchema.TEMPORARY,
      name = "authors"
    )

    for ((label, build) in cases) {
      val actual = build() as CompiledSelectDetails
      assertWithMessage(label)
        .that(actual.queryDependencies.directSources)
        .contains(expectedSource)
      assertWithMessage(label)
        .that(actual.queryDependencies.observedTables.asList())
        .contains("authors")
    }
  }

  @Test
  fun viewDefinitionRetainsBaseObservationWhenComposedAndReused() {
    val main = sourceTable(name = "books")
    val temporary = sourceTable(
      name = "books",
      temporary = true
    )
    val compiled = Select
      .from(main)
      .innerJoin(temporary.`as`("temporary_books"))
      .compile()
    val definition = SqlUtil.viewDefinition(
      query = compiled,
      viewName = "book_report"
    )
    val retained = definition.queryDependencies
    val expected = listOf(
      SqliteQuerySource(
        schema = SqliteSchema.MAIN,
        name = "books"
      ),
      SqliteQuerySource(
        schema = SqliteSchema.TEMPORARY,
        name = "books"
      )
    )
    assertThat(retained.directSources)
      .containsExactlyElementsIn(expected)
      .inOrder()
    assertThat(retained.observedTables.asList())
      .containsExactly("books")

    val view = SourceViewTable(
      alias = null,
      definition = definition
    )
    val first = Select
      .from(view.`as`("first"))
      .compile() as CompiledSelectDetails
    val repeated = Select
      .from(view.`as`("left"))
      .innerJoin(view.`as`("right"))
      .compile() as CompiledSelectDetails

    val expectedViewSource = SqliteQuerySource(
      schema = SqliteSchema.MAIN,
      name = "book_report",
      kind = SqliteObjectKind.VIEW
    )
    assertThat(first.queryDependencies.directSources)
      .containsExactly(expectedViewSource)
      .inOrder()
    assertThat(repeated.queryDependencies.directSources)
      .containsExactly(expectedViewSource)
      .inOrder()
    assertThat(first.queryDependencies.observedTables.asList())
      .containsExactly("books")
    assertThat(repeated.queryDependencies.observedTables.asList())
      .containsExactly("books")
    assertThat(definition.queryDependencies)
      .isSameInstanceAs(retained)
    assertThat(retained.directSources)
      .containsExactlyElementsIn(expected)
      .inOrder()
  }

  private fun sourceTable(
    name: String,
    temporary: Boolean = false
  ) = testTable(
    name = name,
    temporary = temporary,
    mapper = { _, _, _ -> Query.Mapper { Any() } }
  )

  private class SourceViewTable(
    alias: String?,
    private val definition: ViewDefinition
  ) : Table<Any>(
    name = "book_report",
    alias = alias,
    nrOfColumns = 1,
    mapper = { _, _, _ -> Query.Mapper { Any() } },
    viewDefinition = { definition }
  ) {
    override fun `as`(alias: String) = SourceViewTable(
      alias = alias,
      definition = definition
    )
  }
}
