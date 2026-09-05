package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.runtime.model.CountOperator
import com.siimkinks.sqlitemagic.runtime.model.CountOperatorCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.CountOperatorCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import io.reactivex.BackpressureStrategy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class QueryCountOperatorLifecycleTest(
  private val operatorCase: CountOperatorCase
) : RuntimeDatabaseTest() {
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
    CountOperator.ZERO -> Select
      .from(SIMPLE_MUTABLE_ENTITY)
      .count()
      .observe()
      .isZero()

    CountOperator.NOT_ZERO -> Select
      .from(SIMPLE_MUTABLE_ENTITY)
      .count()
      .observe()
      .isNotZero()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun operatorCases() = CountOperatorCatalog.cases
  }
}
