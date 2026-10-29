package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.ReactiveStream
import com.siimkinks.sqlitemagic.runtime.support.reactiveAdapters
import com.siimkinks.sqlitemagic.runtime.support.record
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class QueryOperatorLifecycleTest(
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun listRunQueryFiltersEmptySnapshotAndCancelsAfterFirstValue() {
    val observer = adapter
      .runQuery(listQuery())
      .filter(List<SimpleMutableEntity>::isNotEmpty)
      .take(1)
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      val expected = insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertEmpty()

      observer.request(1)
      observer.assertResult(listOf(expected))

      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertResult(listOf(expected))
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun singleItemRunQueryRequestsAfterEmptyAndIgnoresAfterDisposal() {
    val observer = adapter
      .runQuery(firstQuery())
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      val expected = insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertEmpty()

      observer.request(1)
      observer.assertValuesOnly(expected)

      assertThat(
        expected
          .delete()
          .execute()
      ).isEqualTo(1)
      observer.request(1)
      observer.assertValuesOnly(expected)

      observer.dispose()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(expected)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun oneShotListIgnoresWritesAfterCompletion() {
    val expected = listOf(insertQueryOperatorLifecycleEntity(sequence = 1))

    assertOneShotIgnoresLaterWrite(
      source = adapter.runQueryOnce(listQuery()),
      expected = expected,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun oneShotFirstIgnoresWritesAfterCompletion() {
    val expected = insertQueryOperatorLifecycleEntity(sequence = 1)

    assertOneShotIgnoresLaterWrite(
      source = adapter.runQueryOnce(firstQuery()),
      expected = expected,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun oneShotFirstEmptyIgnoresWritesAfterCompletion() {
    val observer = adapter
      .runQueryOnce(firstQuery())
      .record(initialDemand = 0)
      .assertResult()

    try {
      observer.request(1)
      observer.assertResult()

      insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertResult()
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun oneShotCountIgnoresWritesAfterCompletion() {
    insertQueryOperatorLifecycleEntity(sequence = 1)

    assertOneShotIgnoresLaterWrite(
      source = adapter.runQueryOnce(
        SELECT
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .count()
      ),
      expected = 1L,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun runQueryOrDefaultTracksEmptyAndNonEmptySnapshotsAndStopsAfterDisposal() {
    val defaultValue = newQueryOperatorLifecycleEntity(sequence = 0)
    val observer = adapter
      .runQueryOrDefault(
        query = firstQuery(),
        defaultValue = defaultValue
      )
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertValuesOnly(defaultValue)

      val expected = insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertValuesOnly(defaultValue)

      observer.request(1)
      observer.assertValuesOnly(defaultValue, expected)

      assertThat(
        expected
          .delete()
          .execute()
      ).isEqualTo(1)
      observer.assertValuesOnly(defaultValue, expected)

      observer.request(1)
      observer.assertValuesOnly(defaultValue, expected, defaultValue)

      observer.dispose()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(defaultValue, expected, defaultValue)
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun runQueryOnceOrDefaultCompletesWithDefaultAndIgnoresLaterWrites() {
    val defaultValue = newQueryOperatorLifecycleEntity(sequence = 0)
    val observer = adapter
      .runQueryOnceOrDefault(
        query = firstQuery(),
        defaultValue = defaultValue
      )
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertResult(defaultValue)

      insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertResult(defaultValue)
    } finally {
      observer.dispose()
    }
  }

  private fun listQuery() = SELECT
    .FROM(SIMPLE_MUTABLE_ENTITY)
    .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())

  private fun firstQuery() = listQuery().takeFirst()

  private fun <T : Any> assertOneShotIgnoresLaterWrite(
    source: ReactiveStream<T>,
    expected: T,
    laterWrite: () -> Unit
  ) {
    val observer = source
      .record(initialDemand = 0)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertResult(expected)

      laterWrite()
      observer.assertResult(expected)
    } finally {
      observer.dispose()
    }
  }

  private fun insertSecondEntity() {
    insertQueryOperatorLifecycleEntity(sequence = 2)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun adapters() = reactiveAdapters
  }
}
