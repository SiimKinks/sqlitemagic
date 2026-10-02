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
class RelationshipFullModelListQueryTest(
  private val modelCase: RelationshipQueryModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeReturnsExpectedShallowModels() {
    assertExecutedShallowModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedShallowModels() {
    assertObservedShallowModels(modelCase = modelCase)
  }

  @Test
  fun executeReturnsExpectedDeepModels() {
    assertExecutedDeepModels(modelCase = modelCase)
  }

  @Test
  fun observeRunQueryOnceReturnsExpectedDeepModels() {
    assertObservedDeepModels(modelCase = modelCase)
  }

  @Test
  fun executeReturnsEmptyShallowModelListForEmptyTable() {
    assertThat(
      Select
        .from(modelCase.table)
        .execute()
    ).isEmpty()
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyShallowModelListForEmptyTable() {
    Select
      .from(modelCase.table)
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(emptyList())
  }

  @Test
  fun executeReturnsEmptyDeepModelListForEmptyTable() {
    assertThat(
      Select
        .from(modelCase.table)
        .queryDeep()
        .execute()
    ).isEmpty()
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyDeepModelListForEmptyTable() {
    Select
      .from(modelCase.table)
      .queryDeep()
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(emptyList())
  }

  private fun <T> assertExecutedShallowModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).shallow

    assertThat(
      Select
        .from(modelCase.table)
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedShallowModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).shallow

    assertThat(
      Select
        .from(modelCase.table)
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertExecutedDeepModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).deep

    assertThat(
      Select
        .from(modelCase.table)
        .queryDeep()
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedDeepModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).deep

    assertThat(
      Select
        .from(modelCase.table)
        .queryDeep()
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.relationshipModelQueryCases
  }
}
