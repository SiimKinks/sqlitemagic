package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test

internal class OngoingQueryObservationTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun onlyRelevantInsertRefreshesTheList() {
    val observer = SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(emptyList())
    try {
      NonNullQueryFixture.seed()
      observer.assertValuesOnly(emptyList())

      val expected = insertQueryOperatorLifecycleEntity(sequence = 1)

      observer.assertValuesOnly(
        emptyList(),
        listOf(expected)
      )
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun sqlExecutionErrorIsDelivered() {
    SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
      .WHERE(
        "EXISTS (SELECT 1 FROM missing)".expr(BOOLEAN)
          .observes(SIMPLE_MUTABLE_ENTITY)
      )
      .observe()
      .runQuery()
      .test()
      .assertFailure(::isMissingTableError)
  }

  private fun isMissingTableError(error: Throwable) = error.message?.contains("no such table: missing") == true
}
