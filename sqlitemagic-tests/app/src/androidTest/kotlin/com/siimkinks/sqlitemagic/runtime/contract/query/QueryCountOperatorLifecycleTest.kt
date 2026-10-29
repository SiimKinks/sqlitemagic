package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.runtime.model.CountOperator.NOT_ZERO
import com.siimkinks.sqlitemagic.runtime.model.CountOperator.ZERO
import com.siimkinks.sqlitemagic.runtime.model.CountOperatorCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.CountOperatorCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class QueryCountOperatorLifecycleTest(
  private val operatorCase: CountOperatorCase,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun countOperatorTracksPendingSnapshotsAndStopsAfterCancellation() {
    val observer = observeCount()
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertValuesOnly(operatorCase.emptyValue)

      val first = insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertValuesOnly(operatorCase.emptyValue)

      observer.request(1)
      observer.assertValuesOnly(
        operatorCase.emptyValue,
        operatorCase.nonEmptyValue
      )

      assertThat(
        first
          .delete()
          .execute()
      ).isEqualTo(1)
      observer.assertValuesOnly(
        operatorCase.emptyValue,
        operatorCase.nonEmptyValue
      )

      observer.request(1)
      observer.assertValuesOnly(
        operatorCase.emptyValue,
        operatorCase.nonEmptyValue,
        operatorCase.emptyValue
      )

      observer.dispose()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(
        operatorCase.emptyValue,
        operatorCase.nonEmptyValue,
        operatorCase.emptyValue
      )
    } finally {
      observer.dispose()
    }
  }

  private fun observeCount() = when (operatorCase.operator) {
    ZERO -> adapter.isZero(
      SELECT
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .count()
    )

    NOT_ZERO -> adapter.isNotZero(
      SELECT
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .count()
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0} {1}")
    fun operatorCases() = withReactiveAdapters(CountOperatorCatalog.cases)
  }
}
