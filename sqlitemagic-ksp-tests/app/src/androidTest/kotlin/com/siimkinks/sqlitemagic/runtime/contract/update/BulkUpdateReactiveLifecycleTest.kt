package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 2
    )
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
    assertRows(
      table = modelCase.table,
      expected = originalValues
    )
  }

  private fun <T> assertRecursiveDisposedBulkUpdate(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val originalValues = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val originalRelated = captureRows(table = modelCase.relatedTable)
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
      CONFLICT_NONE -> assertSnapshot(
        modelCase = modelCase,
        expectedParents = originalValues,
        expectedRelated = originalRelated
      )
      CONFLICT_IGNORE -> {
        val expectedParents = listOf(
          modelCase.expectedAfterUpdate(value = updatedValues.first()),
          originalValues[1]
        )
        assertSnapshot(
          modelCase = modelCase,
          expectedParents = expectedParents,
          expectedRelated = relatedRows(
            modelCase = modelCase,
            values = expectedParents
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
    var traversalCount = 0
    val values = object : Iterable<T> {
      override fun iterator(): Iterator<T> {
        traversalCount++
        return when (traversalCount) {
          1 -> firstValues.iterator()
          2 -> secondValues.iterator()
          else -> error("Unexpected traversal: $traversalCount")
        }
      }
    }
    val operation = modelCase.observeBulkUpdate(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(traversalCount).isEqualTo(1)
    assertRows(
      table = modelCase.table,
      expected = firstValues.map(modelCase::expectedAfterUpdate)
    )

    operation
      .test()
      .assertResult()
    assertThat(traversalCount).isEqualTo(2)
    assertRows(
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
    var traversalCount = 0
    val values = object : Iterable<T> {
      override fun iterator(): Iterator<T> {
        traversalCount++
        return when (traversalCount) {
          1 -> firstValues.iterator()
          2 -> secondValues.iterator()
          else -> error("Unexpected traversal: $traversalCount")
        }
      }
    }
    val operation = modelCase.observeBulkUpdate(
      values = values,
      conflictAlgorithm = conflictAlgorithm
    )

    operation
      .test()
      .assertResult()
    assertThat(traversalCount).isEqualTo(1)
    val firstExpectedValues = firstValues.map(modelCase::expectedAfterUpdate)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = firstExpectedValues,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = firstExpectedValues
      )
    )

    operation
      .test()
      .assertResult()
    assertThat(traversalCount).isEqualTo(2)
    val secondExpectedValues = secondValues.map(modelCase::expectedAfterUpdate)
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = secondExpectedValues,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = secondExpectedValues
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

    assertRows(
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
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = expectedValues,
      expectedRelated = relatedRows(
        modelCase = modelCase,
        values = expectedValues
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

  private fun <T> seedRows(
    modelCase: BulkUpdateModelCase<T>,
    count: Int
  ): List<T> {
    List(size = count, init = modelCase::newValue).forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
      }
    }
    return captureRows(table = modelCase.table)
  }

  private fun <T> assertSnapshot(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    assertRows(
      table = modelCase.table,
      expected = expectedParents
    )
    assertThat(captureRows(table = modelCase.relatedTable))
      .containsExactlyElementsIn(expectedRelated)
      .inOrder()
  }

  private fun <T> assertRows(
    table: Table<T>,
    expected: List<T>
  ) = assertThat(captureRows(table = table))
    .containsExactlyElementsIn(expected)
    .inOrder()

  private fun <T> relatedRows(
    modelCase: RecursiveBulkUpdateModelCase<T>,
    values: List<T>
  ) = values.flatMap(modelCase::relatedValues)

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private fun <T> disposeAfterFirst(
    values: List<T>,
    observer: TestObserver<Void>
  ): List<T> = object : AbstractList<T>() {
    override val size get() = values.size

    override fun get(index: Int) = values[index]

    override fun iterator() = object : Iterator<T> {
      private var index = 0

      override fun hasNext(): Boolean {
        if (index == 1) {
          observer.dispose()
        }
        return index < values.size
      }

      override fun next() = values[index++]
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.bulkUpdateCases
  }
}
