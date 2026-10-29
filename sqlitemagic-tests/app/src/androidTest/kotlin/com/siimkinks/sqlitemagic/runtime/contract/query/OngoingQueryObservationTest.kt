package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.runtime.model.catalog.NonNullQueryFixture
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import com.siimkinks.sqlitemagic.runtime.support.record
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class OngoingQueryObservationTest(
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun onlyRelevantInsertRefreshesTheList() {
    val observer = adapter
      .runQuery(
        SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
          .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      )
      .record()
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
    adapter
      .runQuery(
        SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
          .WHERE(
            "EXISTS (SELECT 1 FROM missing)".expr(BOOLEAN)
              .observes(SIMPLE_MUTABLE_ENTITY)
          )
      )
      .record()
      .assertFailure(::isMissingTableError)
  }

  private fun isMissingTableError(error: Throwable) = error.message?.contains("no such table: missing") == true

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun adapters() = reactiveAdapters
  }
}
