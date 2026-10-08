package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class QueryCompositionBaselineTest(
  private val queryCase: CompositionCase
) : RuntimeDatabaseTest() {
  @Test
  fun executePreservesCompositionSemantics() {
    seed()
    val actual = queryCase
      .query()
      .execute()
    assertThat(actual).isEqualTo(queryCase.expected)
  }

  @Test
  fun observeRunQueryOncePreservesCompositionSemantics() {
    seed()
    val observer = queryCase
      .query()
      .observe()
      .runQueryOnce()
      .test()
      .assertComplete()
      .assertNoErrors()
    assertThat(observer.values())
      .containsExactly(queryCase.expected)
  }

  private fun seed() = queryCase.rows.forEach { (string, integer) ->
    assertSeedInserted(
      result = ImmutableValueWithNullableFields(
        id = null,
        string = string,
        aBoolean = null,
        integer = integer
      )
        .insert()
        .execute(),
      modelName = queryCase.name
    )
  }

  class CompositionCase(
    val name: String,
    val rows: List<Pair<String?, Int?>>,
    val query: () -> SelectSqlNode.SelectNode<out String?, Select.Select1, *>,
    val expected: List<String?>
  ) {
    override fun toString() = name
  }

  companion object {
    private val table = IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
    private val ordinaryRows = listOf("alpha" to 101, "beta" to 202, "gamma" to 303)
    private val nullRows = listOf(null to 101, "present" to 202)

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun queryCases() = listOf(
      CompositionCase(
        name = "GROUP BY HAVING ORDER BY LIMIT OFFSET",
        rows = listOf(
          "alpha" to 10,
          "alpha" to 20,
          "beta" to 30,
          "beta" to 40,
          "gamma" to 50,
          "gamma" to 60,
          "delta" to 1
        ),
        query = {
          val total = Select.sum(table.INTEGER)
          Select.column(table.STRING)
            .from(table)
            .where(table.INTEGER.greaterThan(0))
            .groupBy(table.STRING)
            .having(total.greaterThan(Select.asColumn(20.0)))
            .orderBy(total.desc(), table.STRING.asc())
            .limit(1)
            .offset(1)
        },
        expected = listOf("beta")
      ),
      CompositionCase(
        name = "nested inner and outer distinct binding occurrences",
        rows = ordinaryRows,
        query = {
          val inner = Select.column(table.INTEGER)
            .from(table)
            .where(table.INTEGER.greaterThan(101))
          Select.column(table.STRING)
            .from(table)
            .where(table.INTEGER.`in`(inner).and(table.INTEGER.lessThan(303)))
            .orderBy(table.ID.asc())
        },
        expected = listOf("beta")
      ),
      CompositionCase(
        name = "INTERSECT scalar results",
        rows = ordinaryRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.INTEGER.lessOrEqual(202))
            .intersect(
              Select.column(table.STRING)
                .from(table)
                .where(table.INTEGER.greaterOrEqual(202))
            )
            .orderBy(table.STRING.asc())
        },
        expected = listOf("beta")
      ),
      CompositionCase(
        name = "EXCEPT scalar results",
        rows = ordinaryRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.INTEGER.lessOrEqual(202))
            .except(
              Select.column(table.STRING)
                .from(table)
                .where(table.INTEGER.greaterOrEqual(202))
            )
            .orderBy(table.STRING.asc())
        },
        expected = listOf("alpha")
      ),
      CompositionCase(
        name = "numeric literal equality retains legacy TEXT binding",
        rows = ordinaryRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(Select.asColumn(202).`is`(202))
            .orderBy(table.ID.asc())
        },
        expected = emptyList()
      ),
      CompositionCase(
        name = "numeric schema affinity matches legacy TEXT binding",
        rows = ordinaryRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.INTEGER.`is`(202))
            .orderBy(table.ID.asc())
        },
        expected = listOf("beta")
      ),
      CompositionCase(
        name = "equality excludes NULL input",
        rows = nullRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.STRING.`is`("present"))
            .orderBy(table.ID.asc())
        },
        expected = listOf("present")
      ),
      CompositionCase(
        name = "inequality excludes NULL input",
        rows = nullRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.STRING.isNot("absent"))
            .orderBy(table.ID.asc())
        },
        expected = listOf("present")
      ),
      CompositionCase(
        name = "explicit IS NULL selects NULL input",
        rows = nullRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.STRING.isNull())
            .orderBy(table.ID.asc())
        },
        expected = listOf(null)
      ),
      CompositionCase(
        name = "explicit IS NOT NULL excludes NULL input",
        rows = nullRows,
        query = {
          Select.column(table.STRING)
            .from(table)
            .where(table.STRING.isNotNull())
            .orderBy(table.ID.asc())
        },
        expected = listOf("present")
      )
    )
  }
}
