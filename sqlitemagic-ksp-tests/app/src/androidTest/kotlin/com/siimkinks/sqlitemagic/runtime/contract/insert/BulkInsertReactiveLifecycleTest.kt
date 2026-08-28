package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import io.reactivex.observers.TestObserver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BulkInsertReactiveLifecycleTest(
  private val modelCase: BulkInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun disposeAfterFirstEntityOrGraphWithDefaultConflictRollsBack() {
    assertDisposedBulkInsert(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun disposeAfterFirstEntityOrGraphWithConflictIgnoreStopsFurtherWork() {
    assertDisposedBulkInsert(conflictAlgorithm = CONFLICT_IGNORE)
  }

  @Test
  fun chainedObserveBulkInsertsWithDefaultConflictCommitBothBatches() {
    assertChainedBulkInsert(conflictAlgorithm = CONFLICT_NONE)
  }

  @Test
  fun chainedObserveBulkInsertsWithConflictIgnoreCommitBothBatches() {
    assertChainedBulkInsert(conflictAlgorithm = CONFLICT_IGNORE)
  }

  private fun assertDisposedBulkInsert(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkInsertModelCase<*> -> assertRecursiveDisposedBulkInsert(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectDisposedBulkInsert(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectDisposedBulkInsert(
    modelCase: BulkInsertModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val values = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    val queryObserver = observeRows(modelCase.table)
    val operationObserver = TestObserver<Void>()

    modelCase
      .bulkInsert(
        values = disposeAfterFirst(
          values = values,
          observer = operationObserver
        )
      )
      .conflictAlgorithm(conflictAlgorithm)
      .observe()
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    queryObserver.assertValue(emptyList())
    assertThat(captureRows(table = modelCase.table)).isEmpty()
    queryObserver.dispose()
  }

  private fun <T> assertRecursiveDisposedBulkInsert(
    modelCase: RecursiveBulkInsertModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val values = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    val queryObserver = observeRows(modelCase.table)
    val operationObserver = TestObserver<Void>()

    modelCase
      .bulkInsert(
        values = disposeAfterFirst(
          values = values,
          observer = operationObserver
        )
      )
      .conflictAlgorithm(conflictAlgorithm)
      .observe()
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    when (conflictAlgorithm) {
      CONFLICT_NONE -> {
        queryObserver.assertValue(emptyList())
        assertThat(captureRows(table = modelCase.table)).isEmpty()
        assertThat(captureRows(table = modelCase.relatedTable)).isEmpty()
      }
      CONFLICT_IGNORE -> {
        val actualParents = captureRows(table = modelCase.table)
        assertThat(actualParents)
          .containsExactlyElementsIn(
            modelCase.expectedAfterBulkInsert(
              values = values.take(1),
              actual = actualParents
            )
          )
        assertThat(captureRows(table = modelCase.relatedTable))
          .containsExactlyElementsIn(modelCase.relatedValues(values.first()))
        assertThat(queryObserver.values()).hasSize(2)
        assertThat(queryObserver.values().first()).isEmpty()
        val observedParents = queryObserver.values()[1]
        assertThat(observedParents)
          .containsExactlyElementsIn(
            modelCase.expectedAfterBulkInsert(
              values = values.take(1),
              actual = observedParents
            )
          )
      }
      else -> error("Unsupported conflict algorithm: $conflictAlgorithm")
    }
    queryObserver.dispose()
  }

  private fun assertChainedBulkInsert(conflictAlgorithm: Int) = when (modelCase) {
    is RecursiveBulkInsertModelCase<*> -> assertRecursiveChainedBulkInsert(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
    else -> assertDirectChainedBulkInsert(
      modelCase = modelCase,
      conflictAlgorithm = conflictAlgorithm
    )
  }

  private fun <T> assertDirectChainedBulkInsert(
    modelCase: BulkInsertModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val firstBatch = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    val secondBatch = listOf(
      modelCase.newValue(sequence = 3),
      modelCase.newValue(sequence = 4)
    )
    modelCase
      .bulkInsert(values = firstBatch)
      .conflictAlgorithm(conflictAlgorithm)
      .observe()
      .andThen(
        modelCase
          .bulkInsert(values = secondBatch)
          .conflictAlgorithm(conflictAlgorithm)
          .observe()
      )
      .test()
      .assertResult()

    val actual = captureRows(table = modelCase.table)
    assertThat(actual)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = firstBatch + secondBatch,
          actual = actual
        )
      )
  }

  private fun <T> assertRecursiveChainedBulkInsert(
    modelCase: RecursiveBulkInsertModelCase<T>,
    conflictAlgorithm: Int
  ) {
    val firstBatch = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    val secondBatch = listOf(
      modelCase.newValue(sequence = 3),
      modelCase.newValue(sequence = 4)
    )
    modelCase
      .bulkInsert(values = firstBatch)
      .conflictAlgorithm(conflictAlgorithm)
      .observe()
      .andThen(
        modelCase
          .bulkInsert(values = secondBatch)
          .conflictAlgorithm(conflictAlgorithm)
          .observe()
      )
      .test()
      .assertResult()

    val allValues = firstBatch + secondBatch
    val actualParents = captureRows(table = modelCase.table)
    assertThat(actualParents)
      .containsExactlyElementsIn(
        modelCase.expectedAfterBulkInsert(
          values = allValues,
          actual = actualParents
        )
      )
    assertThat(captureRows(table = modelCase.relatedTable))
      .containsExactlyElementsIn(allValues.flatMap(modelCase::relatedValues))
  }

  private fun <T> observeRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .observe()
    .runQuery()
    .test()

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private fun <T> disposeAfterFirst(
    values: List<T>,
    observer: TestObserver<Void>
  ) = object : AbstractList<T>() {
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
    fun modelCases() = ModelCatalog.bulkInsertCases
  }
}
