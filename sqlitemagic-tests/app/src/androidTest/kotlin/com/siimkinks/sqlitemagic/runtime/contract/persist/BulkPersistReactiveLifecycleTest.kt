package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.SuccessiveTraversalIterable
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
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
class BulkPersistReactiveLifecycleTest(
  private val modelCase: BulkPersistModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun disposeAfterFirstEntityOrGraphWithDefaultConflictRollsBack() {
    assertDisposedBulkPersist(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun disposeAfterFirstEntityOrGraphWithConflictIgnoreStopsFurtherWork() {
    assertDisposedBulkPersist(conflictAlgorithm = CONFLICT_IGNORE)
  }

  @Test
  fun repeatedSubscriptionToBulkPersistWithDefaultConflictReexecutesIterable() {
    assertRepeatedSubscriptions(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun repeatedSubscriptionToBulkPersistWithConflictIgnoreReexecutesIterable() {
    assertRepeatedSubscriptions(conflictAlgorithm = CONFLICT_IGNORE)
  }

  @Test
  fun chainedObserveBulkPersistsWithDefaultConflictCommitsBothBatches() {
    assertChainedBulkPersists(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun chainedObserveBulkPersistsWithConflictIgnoreCommitsBothBatches() {
    assertChainedBulkPersists(conflictAlgorithm = CONFLICT_IGNORE)
  }

  private fun assertDisposedBulkPersist(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkPersistModelCase<*> -> assertRecursiveDisposedBulkPersist(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectDisposedBulkPersist(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectDisposedBulkPersist(
    modelCase: BulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValue = modelCase.updatedValue(
      value = captureRows(table = modelCase.table).single(),
      sequence = 2
    )
    val values = listOf(
      updatedValue,
      modelCase.newValue(sequence = 3)
    )
    val operationObserver = TestObserver<Void>()

    modelCase
      .observeBulkPersist(
        values = disposeAfterFirst(
          values = values,
          observer = operationObserver
        ),
        conflictAlgorithm = conflictAlgorithm
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
  }

  private fun <T> assertRecursiveDisposedBulkPersist(
    modelCase: RecursiveBulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val updatedValue = modelCase.updatedValue(
      value = captureRows(table = modelCase.table).single(),
      sequence = 2
    )
    val values = listOf(
      updatedValue,
      modelCase.newValue(sequence = 3)
    )
    val operationObserver = TestObserver<Void>()

    modelCase
      .observeBulkPersist(
        values = disposeAfterFirst(
          values = values,
          observer = operationObserver
        ),
        conflictAlgorithm = conflictAlgorithm
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    when (conflictAlgorithm) {
      CONFLICT_NONE -> assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = snapshotBefore
      )
      CONFLICT_IGNORE -> {
        val expectedParents = listOf(modelCase.expectedAfterUpdate(value = updatedValue))
        assertDatabaseSnapshotIgnoringOrder(
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
    is RecursiveBulkPersistModelCase<*> -> assertRecursiveRepeatedSubscriptions(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectRepeatedSubscriptions(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectRepeatedSubscriptions(
    modelCase: BulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val originalValues = captureRows(table = modelCase.table)
    val firstValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 3,
      insertSequence = 4
    )
    val secondValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 5,
      insertSequence = 6
    )
    val values = SuccessiveTraversalIterable(
      traversalBatches = listOf(firstValues, secondValues)
    )
    val operation = modelCase.observeBulkPersist(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(1)
    val firstActual = captureRows(table = modelCase.table)
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = expectedMixedParents(
        modelCase = modelCase,
        before = originalValues,
        updatedIndex = 0,
        updatedValue = firstValues[0],
        insertedValues = listOf(firstValues[1]),
        actual = firstActual
      )
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(2)
    val secondActual = captureRows(table = modelCase.table)
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = expectedMixedParents(
        modelCase = modelCase,
        before = firstActual,
        updatedIndex = 0,
        updatedValue = secondValues[0],
        insertedValues = listOf(secondValues[1]),
        actual = secondActual
      )
    )
  }

  private fun <T> assertRecursiveRepeatedSubscriptions(
    modelCase: RecursiveBulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    seedRows(
      modelCase = modelCase,
      count = 2
    )
    val originalValues = captureRows(table = modelCase.table)
    val firstValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 3,
      insertSequence = 4
    )
    val secondValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 5,
      insertSequence = 6
    )
    val values = SuccessiveTraversalIterable(
      traversalBatches = listOf(firstValues, secondValues)
    )
    val operation = modelCase.observeBulkPersist(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(1)
    val firstActual = captureRows(table = modelCase.table)
    val firstExpected = expectedMixedParents(
      modelCase = modelCase,
      before = originalValues,
      updatedIndex = 0,
      updatedValue = firstValues[0],
      insertedValues = listOf(firstValues[1]),
      actual = firstActual
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = firstExpected,
        related = relatedRows(
          modelCase = modelCase,
          values = firstExpected
        )
      )
    )

    operation
      .test()
      .assertResult()
    assertThat(values.traversalCount).isEqualTo(2)
    val secondActual = captureRows(table = modelCase.table)
    val secondExpected = expectedMixedParents(
      modelCase = modelCase,
      before = firstActual,
      updatedIndex = 0,
      updatedValue = secondValues[0],
      insertedValues = listOf(secondValues[1]),
      actual = secondActual
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = secondExpected,
        related = relatedRows(
          modelCase = modelCase,
          values = secondExpected
        )
      )
    )
  }

  private fun assertChainedBulkPersists(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkPersistModelCase<*> -> assertRecursiveChainedBulkPersists(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectChainedBulkPersists(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectChainedBulkPersists(
    modelCase: BulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val firstValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 3,
      insertSequence = 4
    )
    val secondValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[1],
      updateSequence = 5,
      insertSequence = 6
    )

    modelCase
      .observeBulkPersist(
        values = firstValues,
        conflictAlgorithm = conflictAlgorithm
      )
      .andThen(
        modelCase.observeBulkPersist(
          values = secondValues,
          conflictAlgorithm = conflictAlgorithm
        )
      )
      .test()
      .assertResult()

    val actual = captureRows(table = modelCase.table)
    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = expectedChainedParents(
        modelCase = modelCase,
        before = originalValues,
        firstUpdatedValue = firstValues[0],
        secondUpdatedValue = secondValues[0],
        insertedValues = listOf(firstValues[1], secondValues[1]),
        actual = actual
      )
    )
  }

  private fun <T> assertRecursiveChainedBulkPersists(
    modelCase: RecursiveBulkPersistModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val firstValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[0],
      updateSequence = 3,
      insertSequence = 4
    )
    val secondValues = mixedValues(
      modelCase = modelCase,
      existing = captureRows(table = modelCase.table)[1],
      updateSequence = 5,
      insertSequence = 6
    )

    modelCase
      .observeBulkPersist(
        values = firstValues,
        conflictAlgorithm = conflictAlgorithm
      )
      .andThen(
        modelCase.observeBulkPersist(
          values = secondValues,
          conflictAlgorithm = conflictAlgorithm
        )
      )
      .test()
      .assertResult()

    val actual = captureRows(table = modelCase.table)
    val expectedParents = expectedChainedParents(
      modelCase = modelCase,
      before = originalValues,
      firstUpdatedValue = firstValues[0],
      secondUpdatedValue = secondValues[0],
      insertedValues = listOf(firstValues[1], secondValues[1]),
      actual = actual
    )
    assertDatabaseSnapshotIgnoringOrder(
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

  private fun <T> expectedMixedParents(
    modelCase: BulkPersistModelCase<T>,
    before: List<T>,
    updatedIndex: Int,
    updatedValue: T,
    insertedValues: List<T>,
    actual: List<T>
  ): List<T> {
    val expectedExisting = before.mapIndexed { index, value ->
      when (index) {
        updatedIndex -> modelCase.expectedAfterUpdate(value = updatedValue)
        else -> value
      }
    }
    val expectedCandidates = expectedExisting + insertedValues
    return modelCase.expectedAfterBulkInsert(
      values = expectedCandidates,
      actual = actual
    )
  }

  private fun <T> expectedChainedParents(
    modelCase: BulkPersistModelCase<T>,
    before: List<T>,
    firstUpdatedValue: T,
    secondUpdatedValue: T,
    insertedValues: List<T>,
    actual: List<T>
  ): List<T> {
    val expectedExisting = before.mapIndexed { index, value ->
      when (index) {
        0 -> modelCase.expectedAfterUpdate(value = firstUpdatedValue)
        1 -> modelCase.expectedAfterUpdate(value = secondUpdatedValue)
        else -> value
      }
    }
    val expectedCandidates = expectedExisting + insertedValues
    return modelCase.expectedAfterBulkInsert(
      values = expectedCandidates,
      actual = actual
    )
  }

  private fun <T> mixedValues(
    modelCase: BulkPersistModelCase<T>,
    existing: T,
    updateSequence: Int,
    insertSequence: Int
  ) = listOf(
    modelCase.updatedValue(
      value = existing,
      sequence = updateSequence
    ),
    modelCase.newValue(sequence = insertSequence)
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkPersistCases
  }
}
