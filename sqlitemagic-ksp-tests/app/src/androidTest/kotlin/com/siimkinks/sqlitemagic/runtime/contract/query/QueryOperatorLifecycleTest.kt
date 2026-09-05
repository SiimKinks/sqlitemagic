package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import org.junit.Test

class QueryOperatorLifecycleTest : RuntimeDatabaseTest() {
  @Test
  fun listRunQueryFiltersEmptySnapshotAndCancelsAfterFirstValue() {
    val observer = observeListQuery()
      .runQuery()
      .filter(List<SimpleMutableEntity>::isNotEmpty)
      .take(1)
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
      .assertEmpty()

    try {
      val expected = insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertEmpty()

      observer.request(1)
      observer.assertResult(listOf(expected))

      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertResult(listOf(expected))
    } finally {
      observer.cancel()
    }
  }

  @Test
  fun singleItemRunQueryRequestsAfterEmptyAndIgnoresAfterDisposal() {
    val observer = observeFirstQuery()
      .runQuery()
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
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

      observer.cancel()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(expected)
    } finally {
      observer.cancel()
    }
  }

  @Test
  fun oneShotListIgnoresWritesAfterCompletion() {
    val expected = listOf(insertQueryOperatorLifecycleEntity(sequence = 1))

    assertOneShotIgnoresLaterWrite(
      source = observeListQuery()
        .runQueryOnce()
        .toObservable(),
      expected = expected,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun oneShotFirstIgnoresWritesAfterCompletion() {
    val expected = insertQueryOperatorLifecycleEntity(sequence = 1)

    assertOneShotIgnoresLaterWrite(
      source = observeFirstQuery()
        .runQueryOnce()
        .toObservable(),
      expected = expected,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun oneShotFirstEmptyIgnoresWritesAfterCompletion() {
    val observer = observeFirstQuery()
      .runQueryOnce()
      .toObservable()
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
      .assertResult()

    try {
      observer.request(1)
      observer.assertResult()

      insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertResult()
    } finally {
      observer.cancel()
    }
  }

  @Test
  fun oneShotCountIgnoresWritesAfterCompletion() {
    insertQueryOperatorLifecycleEntity(sequence = 1)

    assertOneShotIgnoresLaterWrite(
      source = Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .count()
        .observe()
        .runQueryOnce()
        .toObservable(),
      expected = 1L,
      laterWrite = ::insertSecondEntity
    )
  }

  @Test
  fun runQueryOrDefaultTracksEmptyAndNonEmptySnapshotsAndStopsAfterDisposal() {
    val defaultValue = newQueryOperatorLifecycleEntity(sequence = 0)
    val observer = observeFirstQuery()
      .runQueryOrDefault(defaultValue)
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
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

      observer.cancel()
      insertQueryOperatorLifecycleEntity(sequence = 2)
      observer.assertValuesOnly(defaultValue, expected, defaultValue)
    } finally {
      observer.cancel()
    }
  }

  @Test
  fun runQueryOnceOrDefaultCompletesWithDefaultAndIgnoresLaterWrites() {
    val defaultValue = newQueryOperatorLifecycleEntity(sequence = 0)
    val observer = observeFirstQuery()
      .runQueryOnceOrDefault(defaultValue)
      .toObservable()
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertResult(defaultValue)

      insertQueryOperatorLifecycleEntity(sequence = 1)
      observer.assertResult(defaultValue)
    } finally {
      observer.cancel()
    }
  }

  private fun observeListQuery() = Select
    .from(SIMPLE_MUTABLE_ENTITY)
    .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
    .observe()

  private fun observeFirstQuery() = Select
    .from(SIMPLE_MUTABLE_ENTITY)
    .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
    .takeFirst()
    .observe()

  private fun <T> assertOneShotIgnoresLaterWrite(
    source: Observable<T>,
    expected: T,
    laterWrite: () -> Unit
  ) {
    val observer = source
      .toFlowable(BackpressureStrategy.LATEST)
      .test(0L)
      .assertEmpty()

    try {
      observer.request(1)
      observer.assertResult(expected)

      laterWrite()
      observer.assertResult(expected)
    } finally {
      observer.cancel()
    }
  }

  private fun insertSecondEntity() {
    insertQueryOperatorLifecycleEntity(sequence = 2)
  }
}
