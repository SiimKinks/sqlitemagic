package com.siimkinks.sqlitemagic.runtime.contract.query

import android.annotation.SuppressLint
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
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
  fun executeReturnsExpectedShallowModels() = assertSeededModels(
    expectedRows = RelationshipQueryExpectedRows<*>::shallow,
    read = { table ->
      SELECT
        .FROM(table)
        .execute()
    }
  )

  @Test
  fun observeRunQueryOnceReturnsExpectedShallowModels() = assertSeededModels(
    expectedRows = RelationshipQueryExpectedRows<*>::shallow,
    read = { table ->
      SELECT
        .FROM(table)
        .observe()
        .runQueryOnce()
        .blockingGet()
    }
  )

  @Test
  fun executeReturnsExpectedDeepModels() = assertSeededModels(
    expectedRows = RelationshipQueryExpectedRows<*>::deep,
    read = { table ->
      SELECT
        .FROM(table)
        .queryDeep()
        .execute()
    }
  )

  @Test
  fun observeRunQueryOnceReturnsExpectedDeepModels() = assertSeededModels(
    expectedRows = RelationshipQueryExpectedRows<*>::deep,
    read = { table ->
      SELECT
        .FROM(table)
        .queryDeep()
        .observe()
        .runQueryOnce()
        .blockingGet()
    }
  )

  @Test
  fun useRowsReturnsExpectedDeepModels() = assertSeededModels(
    expectedRows = RelationshipQueryExpectedRows<*>::deep,
    read = { table ->
      SELECT
        .FROM(table)
        .queryDeep()
        .useRows(Sequence<*>::toList)
    }
  )

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

  private fun assertSeededModels(
    expectedRows: (RelationshipQueryExpectedRows<*>) -> List<*>,
    read: (Table<*>) -> List<*>
  ) {
    val expected = expectedRows(
      seedRelationshipQueryExpectedRows(
        modelCase = modelCase,
        count = 3
      )
    )

    assertThat(read(modelCase.table)).containsExactlyElementsIn(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.relationshipModelQueryCases
  }
}
