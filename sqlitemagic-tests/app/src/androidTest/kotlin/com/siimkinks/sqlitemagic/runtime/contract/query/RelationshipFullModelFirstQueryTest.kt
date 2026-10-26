package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
internal class RelationshipFullModelFirstQueryTest(
  private val modelCase: RelationshipQueryModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedShallowModel() = assertFirstShallowExecute(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedShallowModel() = assertFirstShallowObserved(modelCase = modelCase)

  @Test
  fun executeReturnsExpectedDeepModel() = assertFirstDeepExecute(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedDeepModel() = assertFirstDeepObserved(modelCase = modelCase)

  @Test
  fun executeReturnsNullForEmptyTableWithShallowQuery() {
    assertThat(
      SELECT
        .FROM(modelCase.table)
        .takeFirst()
        .execute()
    ).isNull()
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTableWithShallowQuery() {
    SELECT
      .FROM(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult()
  }

  @Test
  fun executeReturnsNullForEmptyTableWithDeepQuery() {
    assertThat(
      SELECT
        .FROM(modelCase.table)
        .queryDeep()
        .takeFirst()
        .execute()
    ).isNull()
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTableWithDeepQuery() {
    SELECT
      .FROM(modelCase.table)
      .queryDeep()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult()
  }

  private fun <T> assertFirstShallowExecute(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 1
    ).shallow.single()

    assertThat(
      SELECT
        .FROM(modelCase.table)
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstShallowObserved(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 1
    ).shallow.single()

    SELECT
      .FROM(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  private fun <T> assertFirstDeepExecute(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 1
    ).deep.single()

    assertThat(
      SELECT
        .FROM(modelCase.table)
        .queryDeep()
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstDeepObserved(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 1
    ).deep.single()

    SELECT
      .FROM(modelCase.table)
      .queryDeep()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.relationshipModelQueryCases
  }
}
