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
internal class RelationshipFullModelListQueryTest(
  private val modelCase: RelationshipQueryModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeReturnsExpectedShallowModels() = assertExecutedShallowModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedShallowModels() = assertObservedShallowModels(modelCase = modelCase)

  @Test
  fun executeReturnsExpectedDeepModels() = assertExecutedDeepModels(modelCase = modelCase)

  @Test
  fun observeRunQueryOnceReturnsExpectedDeepModels() = assertObservedDeepModels(modelCase = modelCase)

  @Test
  fun executeTypedCursorReturnsExpectedDeepModels() = assertExecutedDeepCursorModels(modelCase = modelCase)

  @Test
  fun observeTypedCursorRunQueryOnceReturnsExpectedDeepModels() = assertObservedDeepCursorModels(modelCase = modelCase)

  @Test
  fun executeReturnsEmptyShallowModelListForEmptyTable() {
    assertThat(
      SELECT
        .FROM(modelCase.table)
        .execute()
    ).isEmpty()
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyShallowModelListForEmptyTable() {
    SELECT
      .FROM(modelCase.table)
      .observe()
      .runQueryOnce()
      .test()
      .assertResult(emptyList())
  }

  @Test
  fun executeReturnsEmptyDeepModelListForEmptyTable() {
    assertThat(
      SELECT
        .FROM(modelCase.table)
        .queryDeep()
        .execute()
    ).isEmpty()
  }

  @Test
  fun observeRunQueryOnceReturnsEmptyDeepModelListForEmptyTable() {
    SELECT
      .FROM(modelCase.table)
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
      SELECT
        .FROM(modelCase.table)
        .execute()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedShallowModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).shallow

    assertThat(
      SELECT
        .FROM(modelCase.table)
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
      SELECT
        .FROM(modelCase.table)
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
      SELECT
        .FROM(modelCase.table)
        .queryDeep()
        .observe()
        .runQueryOnce()
        .blockingGet()
    ).containsExactlyElementsIn(expected)
  }

  private fun <T> assertExecutedDeepCursorModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).deep
    val cursorSelect = SELECT
      .FROM(modelCase.table)
      .queryDeep()
      .toCursor()
    val actual = readTypedCursor(cursorSelect = cursorSelect)

    assertThat(actual).containsExactlyElementsIn(expected)
  }

  private fun <T> assertObservedDeepCursorModels(modelCase: RelationshipQueryModelCase<T>) {
    val expected = seedRelationshipQueryExpectedRows(
      modelCase = modelCase,
      count = 3
    ).deep
    val cursorSelect = SELECT
      .FROM(modelCase.table)
      .queryDeep()
      .toCursor()
    val actual = readTypedCursor(
      cursorSelect = cursorSelect,
      cursor = cursorSelect
        .observe()
        .runQueryOnce()
        .blockingGet()
    )

    assertThat(actual).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.relationshipModelQueryCases
  }
}
