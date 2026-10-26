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
import io.reactivex.BackpressureStrategy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class QueryCountOperatorLifecycleTest(
  private val operatorCase: CountOperatorCase
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun countOperatorTracksPendingSnapshotsAndStopsAfterCancellation() {
    val observer = observeCount()
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
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

      observer.cancel()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(
        operatorCase.emptyValue,
        operatorCase.nonEmptyValue,
        operatorCase.emptyValue
      )
    } finally {
      observer.cancel()
    }
  }

  private fun observeCount() = when (operatorCase.operator) {
    ZERO -> SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
      .count()
      .observe()
      .isZero()

    NOT_ZERO -> SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
      .count()
      .observe()
      .isNotZero()
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun operatorCases() = CountOperatorCatalog.cases
  }
}
