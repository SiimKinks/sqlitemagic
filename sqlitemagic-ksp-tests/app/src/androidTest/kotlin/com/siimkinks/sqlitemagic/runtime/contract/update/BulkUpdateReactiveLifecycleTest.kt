package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.SuccessiveTraversalIterable
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotInOrder
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.disposeAfterFirst
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import io.reactivex.observers.TestObserver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BulkUpdateReactiveLifecycleTest(
  private val modelCase: BulkUpdateModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun disposeAfterFirstEntityOrGraphWithDefaultConflictRollsBack() {
    assertDisposedBulkUpdate(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun disposeAfterFirstEntityOrGraphWithConflictIgnoreStopsFurtherWork() {
    assertDisposedBulkUpdate(conflictAlgorithm = CONFLICT_IGNORE)
  }

  @Test
  fun repeatedSubscriptionToBulkUpdateWithDefaultConflictReexecutesIterable() {
    assertRepeatedSubscriptions(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun repeatedSubscriptionToBulkUpdateWithConflictIgnoreReexecutesIterable() {
    assertRepeatedSubscriptions(conflictAlgorithm = CONFLICT_IGNORE)
  }

  @Test
  fun chainedObserveBulkUpdatesWithDefaultConflictCommitBothBatches() {
    assertChainedBulkUpdates(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun chainedObserveBulkUpdatesWithConflictIgnoreCommitBothBatches() {
    assertChainedBulkUpdates(conflictAlgorithm = CONFLICT_IGNORE)
  }

  private fun assertDisposedBulkUpdate(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkUpdateModelCase<*> -> assertRecursiveDisposedBulkUpdate(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectDisposedBulkUpdate(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectDisposedBulkUpdate(
    modelCase: BulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 3
    )
    val operationObserver = TestObserver<Void>()

    modelCase
      .observeBulkUpdate(
        values = disposeAfterFirst(
          values = updatedValues,
          observer = operationObserver
        ),
        conflictAlgorithm = conflictAlgorithm
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  private fun <T> assertRecursiveDisposedBulkUpdate(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 3
    )
    val operationObserver = TestObserver<Void>()

    modelCase
      .observeBulkUpdate(
        values = disposeAfterFirst(
          values = updatedValues,
          observer = operationObserver
        ),
        conflictAlgorithm = conflictAlgorithm
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    when (conflictAlgorithm) {
      CONFLICT_NONE -> assertDatabaseSnapshotInOrder(
        modelCase = modelCase,
        expected = snapshotBefore
      )
      CONFLICT_IGNORE -> {
        val expectedParents = listOf(
          modelCase.expectedAfterUpdate(value = updatedValues.first()),
          snapshotBefore.parents[1]
        )
        assertDatabaseSnapshotInOrder(
          modelCase = modelCase,
          expected = DatabaseSnapshot(
            parents = expectedParents,
            related = relatedRows(
              modelCase = modelCase,
              values = expectedParents
            )
          )
        )
      }
      else -> error("Unsupported conflict algorithm: $conflictAlgorithm")
    }
  }

  private fun assertRepeatedSubscriptions(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkUpdateModelCase<*> -> assertRecursiveRepeatedSubscriptions(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectRepeatedSubscriptions(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectRepeatedSubscriptions(
    modelCase: BulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val firstValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 3
    )
    val secondValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 5
    )
    val values = SuccessiveTraversalIterable(
      traversalBatches = listOf(firstValues, secondValues)
    )
    val operation = modelCase.observeBulkUpdate(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(1)
    assertRowsInOrder(
      table = modelCase.table,
      expected = firstValues.map(modelCase::expectedAfterUpdate)
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(2)
    assertRowsInOrder(
      table = modelCase.table,
      expected = secondValues.map(modelCase::expectedAfterUpdate)
    )
  }

  private fun <T> assertRecursiveRepeatedSubscriptions(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val firstValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 3
    )
    val secondValues = updatedValues(
      modelCase = modelCase,
      values = captureRows(table = modelCase.table),
      firstSequence = 5
    )
    val values = SuccessiveTraversalIterable(
      traversalBatches = listOf(firstValues, secondValues)
    )
    val operation = modelCase.observeBulkUpdate(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(1)
    val firstExpectedValues = firstValues.map(modelCase::expectedAfterUpdate)
    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = firstExpectedValues,
        related = relatedRows(
          modelCase = modelCase,
          values = firstExpectedValues
        )
      )
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(2)
    val secondExpectedValues = secondValues.map(modelCase::expectedAfterUpdate)
    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = secondExpectedValues,
        related = relatedRows(
          modelCase = modelCase,
          values = secondExpectedValues
        )
      )
    )
  }

  private fun assertChainedBulkUpdates(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkUpdateModelCase<*> -> assertRecursiveChainedBulkUpdates(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectChainedBulkUpdates(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectChainedBulkUpdates(
    modelCase: BulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 4
    )
    val firstValues = originalValues
      .take(2)
      .mapIndexed { index, value ->
        modelCase.updatedValue(
          value = value,
          sequence = index + 5
        )
      }
    val secondValues = originalValues
      .drop(2)
      .mapIndexed { index, value ->
        modelCase.updatedValue(
          value = value,
          sequence = index + 7
        )
      }

    modelCase
      .observeBulkUpdate(
        values = firstValues,
        conflictAlgorithm = conflictAlgorithm
      )
      .andThen(
        modelCase.observeBulkUpdate(
          values = secondValues,
          conflictAlgorithm = conflictAlgorithm
        )
      )
      .test()
      .assertResult()

    assertRowsInOrder(
      table = modelCase.table,
      expected = (firstValues + secondValues).map(modelCase::expectedAfterUpdate)
    )
  }

  private fun <T> assertRecursiveChainedBulkUpdates(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 4
    )
    val firstValues = originalValues
      .take(2)
      .mapIndexed { index, value ->
        modelCase.updatedValue(
          value = value,
          sequence = index + 5
        )
      }
    val secondValues = originalValues
      .drop(2)
      .mapIndexed { index, value ->
        modelCase.updatedValue(
          value = value,
          sequence = index + 7
        )
      }

    modelCase
      .observeBulkUpdate(
        values = firstValues,
        conflictAlgorithm = conflictAlgorithm
      )
      .andThen(
        modelCase.observeBulkUpdate(
          values = secondValues,
          conflictAlgorithm = conflictAlgorithm
        )
      )
      .test()
      .assertResult()

    val expectedValues = (firstValues + secondValues).map(modelCase::expectedAfterUpdate)
    assertDatabaseSnapshotInOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = expectedValues,
        related = relatedRows(
          modelCase = modelCase,
          values = expectedValues
        )
      )
    )
  }

  private fun <T> updatedValues(
    modelCase: BulkUpdateModelCase<T>,
    values: List<T>,
    firstSequence: Int
  ) = values.mapIndexed { index, value ->
    modelCase.updatedValue(
      value = value,
      sequence = firstSequence + index
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateCases
  }
}
