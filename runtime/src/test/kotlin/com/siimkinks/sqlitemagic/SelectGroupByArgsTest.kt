package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import org.junit.Test

internal class SelectGroupByArgsTest {
  @Test
  fun scalarSubqueryArgsMatchGroupByAndOrderBySql() {
    val root = sourceTable(name = "books")
    val nested = sourceTable(name = "authors", temporary = true)
    val cases = listOf(
      "selected alias" to {
        val scalar = scalarSubquery(nested)
        Select
          .columns(scalar)
          .from(root)
          .groupBy(scalar)
          .compile() as CompiledSelectDetails
      },
      "unselected subquery" to {
        Select
          .from(root)
          .groupBy(scalarSubquery(nested))
          .compile() as CompiledSelectDetails
      },
      "selected ORDER BY alias" to {
        val scalar = scalarSubquery(nested)
        Select
          .columns(scalar)
          .from(root)
          .orderBy(scalar.desc())
          .compile() as CompiledSelectDetails
      },
      "unselected ORDER BY subquery" to {
        Select
          .from(root)
          .orderBy(scalarSubquery(nested).asc())
          .compile() as CompiledSelectDetails
      }
    )
    val expectedSources = listOf(
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
      val actual = build()
      val expectedClause = when (label) {
        "selected alias", "unselected subquery" -> "GROUP BY (SELECT"
        else -> "ORDER BY (SELECT"
      }
      val expectedArgs = when (label) {
        "selected alias", "selected ORDER BY alias" -> listOf("7", "7")
        else -> listOf("7")
      }
      assertWithMessage(label)
        .that(actual.sql)
        .contains(expectedClause)
      assertWithMessage(label)
        .that(actual.sql.count { it == '?' })
        .isEqualTo(expectedArgs.size)
      assertWithMessage(label)
        .that(actual.args?.asList())
        .containsExactlyElementsIn(expectedArgs)
        .inOrder()
      assertWithMessage(label)
        .that(actual.queryDependencies.observedTables.asList())
        .containsExactly("books", "authors")
      assertWithMessage(label)
        .that(actual.queryDependencies.directSources)
        .containsExactlyElementsIn(expectedSources)
    }
  }

  @Test
  fun selectedAliasMatchingSourceColumnDoesNotReplaceGroupOrOrderExpression() {
    val root = sourceTable(name = "books")
    val selected = Column<Int, Int, Int, Any, NotNullable>(
      table = root,
      name = "rating",
      valueParser = Utils.INTEGER_PARSER,
      nullable = false,
      alias = "title"
    )
    val physical = Column<Int, Int, Int, Any, NotNullable>(
      table = root,
      name = "title",
      valueParser = Utils.INTEGER_PARSER,
      nullable = false,
      alias = null
    )
    val actual = Select
      .columns(selected, physical)
      .from(root)
      .groupBy(selected)
      .orderBy(selected.asc())
      .compile() as CompiledSelectDetails

    assertThat(actual.sql).contains("SELECT books.rating AS 'title',books.title")
    assertThat(actual.sql).contains("GROUP BY books.rating")
    assertThat(actual.sql).contains("ORDER BY books.rating ASC")
    assertThat(actual.sql).doesNotContain("GROUP BY title")
    assertThat(actual.sql).doesNotContain("ORDER BY title")
    assertThat(actual.args).isNull()
  }

  @Test
  fun parameterFreeGroupByKeepsItsSqlAndMetadata() {
    val actual = Select
      .from(sourceTable(name = "books"))
      .groupBy(Select.asColumn(1))
      .compile() as CompiledSelectDetails

    assertThat(actual.sql).isEqualTo("SELECT * FROM main.books GROUP BY 1 ")
    assertThat(actual.args).isNull()
    assertThat(actual.queryDependencies.observedTables.asList()).containsExactly("books")
    assertThat(actual.queryDependencies.directSources).containsExactly(
      SqliteQuerySource(
        schema = SqliteSchema.MAIN,
        name = "books"
      )
    )
  }

  private fun scalarSubquery(table: Table<Any>) = Select
    .column(Select.asColumn(1))
    .from(table)
    .where(Select.asColumn(1).`is`(7))
    .toColumn("author_value")

  private fun sourceTable(
    name: String,
    temporary: Boolean = false
  ) = testTable(
    name = name,
    temporary = temporary,
    mapper = { _, _, _ -> Query.Mapper { Any() } }
  )
}
