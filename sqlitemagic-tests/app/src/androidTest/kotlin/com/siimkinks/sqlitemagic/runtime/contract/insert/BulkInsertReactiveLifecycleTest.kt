package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.Recording
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.disposeAfterFirst
import com.siimkinks.sqlitemagic.runtime.support.observeRows
import com.siimkinks.sqlitemagic.runtime.support.record
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class BulkInsertReactiveLifecycleTest(
  private val modelCase: BulkInsertModelCase<*>,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun disposeAfterFirstEntityOrGraphWithDefaultConflictRollsBack() =
    assertDisposedBulkInsert(conflictAlgorithm = CONFLICT_NONE)

  @Test
  fun disposeAfterFirstEntityOrGraphWithConflictIgnoreStopsFurtherWork() =
    assertDisposedBulkInsert(conflictAlgorithm = CONFLICT_IGNORE)

  @Test
  fun chainedObserveBulkInsertsWithDefaultConflictCommitBothBatches() =
    assertChainedBulkInsert(conflictAlgorithm = CONFLICT_NONE)

  @Test
  fun chainedObserveBulkInsertsWithConflictIgnoreCommitBothBatches() =
    assertChainedBulkInsert(conflictAlgorithm = CONFLICT_IGNORE)

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
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)
    val queryObserver = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
    val operationObserver = Recording<Unit>()

    adapter
      .observe(
        modelCase
          .bulkInsert(
            values = disposeAfterFirst(
              values = values,
              recording = operationObserver
            )
          )
          .conflictAlgorithm(conflictAlgorithm)
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    queryObserver.assertValuesOnly(emptyList())
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = snapshotBefore
    )
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
    val queryObserver = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
    val operationObserver = Recording<Unit>()
    val snapshotBefore = captureDatabaseSnapshot(modelCase = modelCase)

    adapter
      .observe(
        modelCase
          .bulkInsert(
            values = disposeAfterFirst(
              values = values,
              recording = operationObserver
            )
          )
          .conflictAlgorithm(conflictAlgorithm)
      )
      .subscribe(operationObserver)

    operationObserver.assertEmpty()
    when (conflictAlgorithm) {
      CONFLICT_NONE -> {
        queryObserver.assertValuesOnly(emptyList())
        assertDatabaseSnapshotIgnoringOrder(
          modelCase = modelCase,
          expected = snapshotBefore
        )
      }
      CONFLICT_IGNORE -> {
        val actualSnapshot = captureDatabaseSnapshot(modelCase = modelCase)
        val expectedParents = modelCase.expectedAfterBulkInsert(
          values = values.take(1),
          actual = actualSnapshot.parents
        )
        assertDatabaseSnapshotIgnoringOrder(
          modelCase = modelCase,
          expected = DatabaseSnapshot(
            parents = expectedParents,
            related = modelCase.relatedValues(values.first())
          )
        )
        assertThat(queryObserver.values).hasSize(2)
        assertThat(queryObserver.values.first()).isEmpty()
        val observedParents = queryObserver.values[1]
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
    adapter
      .observe(
        modelCase
          .bulkInsert(values = firstBatch)
          .conflictAlgorithm(conflictAlgorithm)
      )
      .andThen(
        adapter
          .observe(
            modelCase
              .bulkInsert(values = secondBatch)
              .conflictAlgorithm(conflictAlgorithm)
          )
      )
      .record()
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
    adapter
      .observe(
        modelCase
          .bulkInsert(values = firstBatch)
          .conflictAlgorithm(conflictAlgorithm)
      )
      .andThen(
        adapter
          .observe(
            modelCase
              .bulkInsert(values = secondBatch)
              .conflictAlgorithm(conflictAlgorithm)
          )
      )
      .record()
      .assertResult()

    val allValues = firstBatch + secondBatch
    val actualSnapshot = captureDatabaseSnapshot(modelCase = modelCase)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = DatabaseSnapshot(
        parents = modelCase.expectedAfterBulkInsert(
          values = allValues,
          actual = actualSnapshot.parents
        ),
        related = allValues.flatMap(modelCase::relatedValues)
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0} {1}")
    fun modelCases() = withReactiveAdapters(ModelCatalog.bulkInsertCases)
  }
}
