package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import org.junit.Test

internal class NestedQueryDependencyTest {
  @Test
  fun twoLevelExpressionScalarAndCompoundQueriesRetainDeepestDependencies() {
    val root = sourceTable("root")
    val middle = sourceTable("middle")
    val deepest = sourceTable("deepest")
    val cases = listOf(
      "expression" to {
        Select
          .from(root)
          .where(
            Select.asColumn(1)
              .`in`(
                Select
                  .column(Select.asColumn(1))
                  .from(middle)
                  .where(
                    Select.asColumn(1)
                      .`in`(
                        Select
                          .column(Select.asColumn(1))
                          .from(deepest)
                      )
                  )
              )
          )
          .compile()
      },
      "scalar" to {
        val scalar = Select
          .column(Select.asColumn(1))
          .from(middle)
          .where(
            Select.asColumn(1)
              .`in`(
                Select
                  .column(Select.asColumn(1))
                  .from(deepest)
              )
          )
          .toColumn("nested_value")
        Select
          .columns(scalar)
          .from(root)
          .compile()
      },
      "compound" to {
        val branch = Select
          .column(Select.asColumn(1))
          .from(middle)
          .where(
            Select.asColumn(1)
              .`in`(
                Select
                  .column(Select.asColumn(1))
                  .from(deepest)
              )
          )
        Select
          .column(Select.asColumn(1))
          .from(root)
          .union(branch)
          .compile()
      }
    )

    for ((label, compile) in cases) {
      val actual = compile() as CompiledSelectDetails
      assertDependencies(
        label = label,
        actual = actual,
        expectedTables = listOf("root", "middle", "deepest")
      )
    }
  }

  @Test
  fun sharedPredicateContributesToBothOuterBuildersBeforeEitherCompiles() {
    val nested = sourceTable("shared_predicate")
    val firstRoot = sourceTable("first_root")
    val secondRoot = sourceTable("second_root")
    val predicate = Select.asColumn(1)
      .`in`(
        Select
          .column(Select.asColumn(1))
          .from(nested)
      )
    val first = Select.from(firstRoot).where(predicate)
    val second = Select.from(secondRoot).where(predicate)

    val secondCompiled = second.compile() as CompiledSelectDetails
    val firstCompiled = first.compile() as CompiledSelectDetails

    assertDependencies(
      label = "second predicate user",
      actual = secondCompiled,
      expectedTables = listOf("second_root", "shared_predicate")
    )
    assertDependencies(
      label = "first predicate user",
      actual = firstCompiled,
      expectedTables = listOf("first_root", "shared_predicate")
    )
    assertThat(firstCompiled.queryDependencies)
      .isNotSameInstanceAs(secondCompiled.queryDependencies)
    assertThat(firstCompiled.queryDependencies.directSources)
      .isNotSameInstanceAs(secondCompiled.queryDependencies.directSources)
    assertThat(firstCompiled.queryDependencies.observedTables)
      .isNotSameInstanceAs(secondCompiled.queryDependencies.observedTables)
  }

  @Test
  fun sharedScalarColumnContributesToBothOuterBuildersBeforeEitherCompiles() {
    val nested = sourceTable("shared_scalar")
    val firstRoot = sourceTable("first_root")
    val secondRoot = sourceTable("second_root")
    val scalar = Select
      .column(Select.asColumn(1))
      .from(nested)
      .toColumn("nested_value")
    val first = Select.columns(scalar).from(firstRoot)
    val second = Select.columns(scalar).from(secondRoot)

    val firstCompiled = first.compile() as CompiledSelectDetails
    val secondCompiled = second.compile() as CompiledSelectDetails

    assertDependencies(
      label = "first scalar user",
      actual = firstCompiled,
      expectedTables = listOf("first_root", "shared_scalar")
    )
    assertDependencies(
      label = "second scalar user",
      actual = secondCompiled,
      expectedTables = listOf("second_root", "shared_scalar")
    )
    assertThat(firstCompiled.sql).contains("(SELECT")
    assertThat(secondCompiled.sql).contains("(SELECT")
    assertThat(firstCompiled.queryDependencies)
      .isNotSameInstanceAs(secondCompiled.queryDependencies)
    assertThat(firstCompiled.queryDependencies.directSources)
      .isNotSameInstanceAs(secondCompiled.queryDependencies.directSources)
    assertThat(firstCompiled.queryDependencies.observedTables)
      .isNotSameInstanceAs(secondCompiled.queryDependencies.observedTables)
  }

  @Test
  fun nestedQueryGraphJoinContributesToBothDependencySets() {
    val joined = sourceTable("graph_join")
    val nested = testTable(
      name = "nested_root",
      mapper = { _, _, _ -> Query.Mapper { Any() } },
      addDeepQueryParts = { _, _, _ ->
        addLeftJoin(
          table = joined,
          on = Expr.raw("1 = 1")
        )
      }
    )
    val actual = Select
      .from(sourceTable("outer_root"))
      .where(
        Select.asColumn(1)
          .`in`(
            Select
              .column(Select.asColumn(1))
              .from(nested)
          )
      )
      .compile() as CompiledSelectDetails

    assertDependencies(
      label = "nested graph join",
      actual = actual,
      expectedTables = listOf("outer_root", "nested_root", "graph_join")
    )
  }

  private fun assertDependencies(
    label: String,
    actual: CompiledSelectDetails,
    expectedTables: List<String>
  ) {
    assertWithMessage("$label observed tables")
      .that(actual.queryDependencies.observedTables.asList())
      .containsExactlyElementsIn(expectedTables)
    assertWithMessage("$label direct sources")
      .that(actual.queryDependencies.directSources)
      .containsExactlyElementsIn(
        expectedTables.map { table ->
          SqliteQuerySource(
            schema = SqliteSchema.MAIN,
            name = table
          )
        }
      )
  }

  private fun sourceTable(name: String) = testTable(
    name = name,
    mapper = { _, _, _ -> Query.Mapper { Any() } }
  )
}
