package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SuppressLint("CheckResult")
@RunWith(Parameterized::class)
class RelationshipFullModelFirstQueryTest(
  private val modelCase: RelationshipQueryModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedShallowModel() {
    assertFirstShallowExecute(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedShallowModel() {
    assertFirstShallowObserved(modelCase = modelCase)
  }

  @Test
  fun executeReturnsExpectedDeepModel() {
    assertFirstDeepExecute(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedDeepModel() {
    assertFirstDeepObserved(modelCase = modelCase)
  }

  @Test
  fun executeReturnsNullForEmptyTableWithShallowQuery() {
    assertThat(
      Select
        .from(modelCase.table)
        .takeFirst()
        .execute()
    ).isNull()
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTableWithShallowQuery() {
    Select
      .from(modelCase.table)
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult()
  }

  @Test
  fun executeReturnsNullForEmptyTableWithDeepQuery() {
    assertThat(
      Select
        .from(modelCase.table)
        .queryDeep()
        .takeFirst()
        .execute()
    ).isNull()
  }

  @Test
  fun observeRunQueryOnceReturnsNoValueForEmptyTableWithDeepQuery() {
    Select
      .from(modelCase.table)
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
      Select
        .from(modelCase.table)
        .takeFirst()
        .execute()
    ).isEqualTo(expected)
  }

  private fun <T> assertFirstShallowObserved(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 1
    ).shallow.single()

    Select
      .from(modelCase.table)
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
      Select
        .from(modelCase.table)
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

    Select
      .from(modelCase.table)
      .queryDeep()
      .takeFirst()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(expected)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.relationshipModelQueryCases
  }
}
