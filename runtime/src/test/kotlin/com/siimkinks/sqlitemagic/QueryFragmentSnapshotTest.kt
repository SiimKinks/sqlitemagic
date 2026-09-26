package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.SqliteSchema
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import org.junit.Assert.assertThrows
import org.junit.Test

internal class QueryFragmentSnapshotTest {
  @Test
  fun scalarExpressionArgsFollowSqlInWhereHavingAndJoinOn() {
    val root = sourceTable("root")
    val nested = sourceTable("nested")
    val joined = sourceTable("joined")
    val scalar = Select
      .column(Select.asColumn(1))
      .from(nested)
      .where(Select.asColumn(1).`is`(7))
      .toColumn("nested_value")
    val cases = listOf(
      "WHERE" to {
        Select
          .from(root)
          .where(scalar.`is`(11))
          .compile() as CompiledSelectDetails
      },
      "HAVING" to {
        Select
          .from(root)
          .groupBy(Select.asColumn(1))
          .having(scalar.`is`(11))
          .compile() as CompiledSelectDetails
      },
      "ON" to {
        Select
          .from(root)
          .innerJoin(joined.on(scalar.`is`(11)))
          .compile() as CompiledSelectDetails
      }
    )

    for ((label, compile) in cases) {
      val actual = compile()
      assertWithMessage("$label SQL")
        .that(actual.sql)
        .contains("$label (SELECT 1 FROM main.nested WHERE 1=? )=?")
      assertWithMessage("$label placeholder count")
        .that(actual.sql.count { it == '?' })
        .isEqualTo(2)
      assertWithMessage("$label bind order")
        .that(actual.args?.asList())
        .containsExactly("7", "11")
        .inOrder()
    }
  }

  @Test
  fun scalarMembershipAndBetweenArgumentsFollowTheirSqlOccurrences() {
    val root = sourceTable("root")
    fun scalar(value: Int) = Select
      .column(Select.asColumn(1))
      .from(sourceTable("nested_$value"))
      .where(Select.asColumn(1).`is`(value))
      .toColumn("scalar_$value")

    val membership = Select
      .from(root)
      .where(scalar(7).`in`(11, 12))
      .compile() as CompiledSelectDetails
    assertThat(membership.sql).contains("WHERE (SELECT 1 FROM main.nested_7 WHERE 1=? ) IN (?,?)")
    assertThat(membership.sql.count { it == '?' }).isEqualTo(3)
    assertThat(membership.args?.asList()).containsExactly("7", "11", "12").inOrder()

    val between = Select
      .from(root)
      .where(scalar(7).between(scalar(8)).and(scalar(9)))
      .compile() as CompiledSelectDetails
    assertThat(between.sql).contains("WHERE (SELECT 1 FROM main.nested_7 WHERE 1=? ) BETWEEN ")
    assertThat(between.sql).contains("AND (SELECT 1 FROM main.nested_9 WHERE 1=? )")
    assertThat(between.sql.count { it == '?' }).isEqualTo(3)
    assertThat(between.args?.asList()).containsExactly("7", "8", "9").inOrder()
  }

  @Test
  fun joinedDerivedTableArgumentsPrecedeOnArguments() {
    val root = sourceTable("root")
    val nested = Select
      .column(Select.asColumn(1))
      .from(sourceTable("nested"))
      .where(Select.asColumn(1).`is`(7))
    val derived = SelectionTable.from<Any>(
      selectTableBuilder = nested.selectBuilder,
      alias = "joined"
    )
    val cases = listOf(
      "direct" to {
        Select
          .from(root)
          .innerJoin(derived)
          .compile() as CompiledSelectDetails
      },
      "ON" to {
        Select
          .from(root)
          .innerJoin(derived.on(Select.asColumn(1).`is`(11)))
          .compile() as CompiledSelectDetails
      }
    )

    for ((label, compile) in cases) {
      val actual = compile()
      val expectedArgs = when (label) {
        "direct" -> listOf("7")
        else -> listOf("7", "11")
      }
      assertWithMessage("$label derived SQL")
        .that(actual.sql)
        .contains("INNER JOIN (SELECT 1 FROM main.nested WHERE 1=? ) AS joined")
      assertWithMessage("$label placeholder count")
        .that(actual.sql.count { it == '?' })
        .isEqualTo(expectedArgs.size)
      assertWithMessage("$label bind order")
        .that(actual.args?.asList())
        .containsExactlyElementsIn(expectedArgs)
        .inOrder()
    }
  }

  @Test
  fun embeddedScalarExpressionAndCompoundBranchesFreezeSqlArgsAndDependencies() {
    val cases = listOf(
      "scalar column" to {
        val nestedFrom = Select
          .column(Select.asColumn(1))
          .from(sourceTable("nested"))
        val nested = nestedFrom.where(Select.asColumn(1).`is`(7))
        val scalar = nested.toColumn("nested_value")
        EmbeddedCase(
          compileOuter = {
            Select
              .columns(scalar)
              .from(sourceTable("root"))
              .compile() as CompiledSelectDetails
          },
          mutateSource = { nestedFrom.innerJoin(sourceTable("late")) },
          expectedSqlFragment = "(SELECT 1 FROM main.nested WHERE 1=?",
          expectedRoot = "root"
        )
      },
      "expression subquery" to {
        val nestedFrom = Select
          .column(Select.asColumn(1))
          .from(sourceTable("nested"))
        val nested = nestedFrom.where(Select.asColumn(1).`is`(7))
        val predicate = Select.asColumn(1).`in`(nested)
        EmbeddedCase(
          compileOuter = {
            Select
              .from(sourceTable("root"))
              .where(predicate)
              .compile() as CompiledSelectDetails
          },
          mutateSource = { nestedFrom.innerJoin(sourceTable("late")) },
          expectedSqlFragment = "IN (SELECT 1 FROM main.nested WHERE 1=?",
          expectedRoot = "root"
        )
      },
      "compound branch" to {
        val branchFrom = Select
          .column(Select.asColumn(1))
          .from(sourceTable("nested"))
        val branch = branchFrom.where(Select.asColumn(1).`is`(7))
        val compound = Select
          .column(Select.asColumn(1))
          .from(sourceTable("root"))
          .union(branch)
        EmbeddedCase(
          compileOuter = { compound.compile() as CompiledSelectDetails },
          mutateSource = { branchFrom.innerJoin(sourceTable("late")) },
          expectedSqlFragment = "UNION SELECT 1 FROM main.nested WHERE 1=?",
          expectedRoot = "root"
        )
      }
    )

    for ((label, createCase) in cases) {
      val case = createCase()
      val failure = assertThrows(IllegalStateException::class.java) {
        case.mutateSource()
      }
      assertWithMessage("$label mutation failure")
        .that(failure.message.orEmpty())
        .containsMatch("(?i)(embedded|frozen)")

      val actual = case.compileOuter()
      assertWithMessage("$label SQL")
        .that(actual.sql)
        .contains(case.expectedSqlFragment)
      assertWithMessage("$label bind args")
        .that(actual.args?.asList())
        .containsExactly("7")
      assertWithMessage("$label direct sources")
        .that(actual.queryDependencies.directSources)
        .containsExactly(
          tableSource(case.expectedRoot),
          tableSource("nested")
        )
      assertWithMessage("$label observed tables")
        .that(actual.queryDependencies.observedTables.asList())
        .containsExactly(case.expectedRoot, "nested")
      assertWithMessage("$label complete direct sources")
        .that(actual.queryDependencies.directSourcesComplete)
        .isTrue()
    }
  }

  @Test
  fun frozenScalarFragmentCanBeReusedAcrossInterleavedOuterQueries() {
    val nestedFrom = Select
      .column(Select.asColumn(1))
      .from(sourceTable("shared"))
    val scalar = nestedFrom
      .where(Select.asColumn(1).`is`(7))
      .toColumn("shared_value")
    val first = Select
      .columns(scalar)
      .from(sourceTable("first_root"))
      .where(Select.asColumn(1).`is`(11))
    val second = Select
      .columns(scalar)
      .from(sourceTable("second_root"))
      .where(Select.asColumn(1).`is`(22))

    val secondCompiled = second.compile() as CompiledSelectDetails
    val firstCompiled = first.compile() as CompiledSelectDetails

    assertThat(firstCompiled.sql).contains("(SELECT 1 FROM main.shared WHERE 1=?")
    assertThat(secondCompiled.sql).contains("(SELECT 1 FROM main.shared WHERE 1=?")
    assertThat(firstCompiled.args?.asList()).containsExactly("7", "11").inOrder()
    assertThat(secondCompiled.args?.asList()).containsExactly("7", "22").inOrder()
    assertThat(firstCompiled.queryDependencies.directSources)
      .containsExactly(tableSource("first_root"), tableSource("shared"))
    assertThat(secondCompiled.queryDependencies.directSources)
      .containsExactly(tableSource("second_root"), tableSource("shared"))
    assertThat(firstCompiled.queryDependencies.observedTables.asList()).containsExactly("first_root", "shared")
    assertThat(secondCompiled.queryDependencies.observedTables.asList()).containsExactly("second_root", "shared")
    assertThat(firstCompiled.queryDependencies.directSourcesComplete).isTrue()
    assertThat(secondCompiled.queryDependencies.directSourcesComplete).isTrue()
    assertThat(firstCompiled.queryDependencies).isNotSameInstanceAs(secondCompiled.queryDependencies)
  }

  @Test
  fun frozenFunctionColumnCompoundBranchCannotBeCompiledIndependently() {
    val functionColumn = Select.abs(Select.asColumn(1)).`as`("n")
    val branch = Select
      .column(functionColumn)
      .from(sourceTable("nested"))
    val compound = Select
      .column(Select.asColumn(1))
      .from(sourceTable("root"))
      .union(branch)

    val failure = assertThrows(IllegalStateException::class.java) {
      branch.compile()
    }
    assertThat(failure).hasMessageThat().containsMatch("(?i)(embedded|frozen)")

    val actual = compound.compile() as CompiledSelectDetails
    assertThat(actual.sql).contains("UNION SELECT abs(1) AS 'n' FROM main.nested")
    assertThat(actual.queryDependencies.directSources)
      .containsExactly(tableSource("root"), tableSource("nested"))
    assertThat(actual.queryDependencies.directSourcesComplete).isTrue()
  }

  @Test
  fun generatedViewIsDirectSourceWhileItsBaseTableRemainsObserved() {
    val definition = SqlUtil.viewDefinition(
      query = Select.from(sourceTable("authors")).compile(),
      viewName = "author_report"
    )
    val view = ContractViewTable(definition = definition)
    val actual = Select
      .from(view)
      .compile() as CompiledSelectDetails

    assertThat(actual.queryDependencies.directSources)
      .containsExactly(
        SqliteQuerySource(
          identity = SqliteSchemaIdentity.from(
            schema = SqliteSchema.MAIN,
            rawName = "author_report"
          ),
          kind = SqliteObjectKind.VIEW
        )
      )
    assertThat(actual.queryDependencies.observedTables.asList()).containsExactly("authors")
    assertThat(actual.queryDependencies.directSourcesComplete).isTrue()
    assertThat(actual.queryDependencies.directSources).doesNotContain(tableSource("authors"))
  }

  private class EmbeddedCase(
    val compileOuter: () -> CompiledSelectDetails,
    val mutateSource: () -> Unit,
    val expectedSqlFragment: String,
    val expectedRoot: String
  )

  private class ContractViewTable(
    definition: ViewDefinition
  ) : Table<Any>(
    name = "author_report",
    alias = null,
    nrOfColumns = 1,
    mapper = { _, _, _ -> Query.Mapper { Any() } },
    viewDefinition = { definition }
  )

  private fun tableSource(name: String) = SqliteQuerySource(
    identity = SqliteSchemaIdentity.from(
      schema = SqliteSchema.MAIN,
      rawName = name
    ),
    kind = SqliteObjectKind.TABLE
  )

  private fun sourceTable(name: String) = testTable<Any>(
    name = name,
    mapper = { _, _, _ -> Query.Mapper { Any() } }
  )
}
