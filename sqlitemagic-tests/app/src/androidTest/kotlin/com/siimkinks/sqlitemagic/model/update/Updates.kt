@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.TestUtil.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun <T> assertBasicUpdateSuccess(success: Boolean, model: TestModel<T>) {
  assertThat(success).isTrue()
  assertTableCount(1L, model.table)
}

fun <T> assertUpdateSuccess(): (TestModel<T>, T, Boolean) -> Unit = { model, testVal, success ->
  assertBasicUpdateSuccess(success, model)
  assertValueEqualsWithDb<T>(testVal, model.table)
}

fun <T> assertBulkUpdateSuccess(): (TestModel<T>, List<T>, Boolean) -> Unit = { model, testVals, success ->
  assertThat(success).isTrue()
  assertValuesEqualWithDb(testVals, model.table)
}

fun <T> assertUpdateSuccessForNullableColumns(): (TestModel<T>, T, Boolean) -> Unit = { model, testVal, success ->
  assertBasicUpdateSuccess(success, model)
  val dbValue = getDbValue(model.table)
  (model as NullableColumns<T>).assertAllExceptNulledColumnsAreUpdated(dbValue, testVal)
}

fun <T> assertUpdateSuccessForComplexNullableColumns(): (TestModel<T>, T, Boolean) -> Unit = { model, testVal, success ->
  assertBasicUpdateSuccess(success, model)
  val dbValue = getDbValue(model.table)
  (model as ComplexNullableColumns<T>).assertAllExceptNulledComplexColumnsAreUpdated(dbValue, testVal)
}

fun <T> assertBulkUpdateSuccessForNullableColumns(): (TestModel<T>, List<T>, Boolean) -> Unit = { model, testVals, success ->
  assertThat(success).isTrue()
  val dbValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(dbValues.size).isEqualTo(testVals.size)
  with(model as NullableColumns<T>) {
    testVals.zip(dbValues)
        .forEach { (testVal, dbVal) ->
          assertAllExceptNulledColumnsAreUpdated(dbVal, testVal)
        }
  }
}

fun <T> assertBulkUpdateSuccessForComplexNullableColumns(): (TestModel<T>, List<T>, Boolean) -> Unit = { model, testVals, success ->
  assertThat(success).isTrue()
  val dbValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(dbValues.size).isEqualTo(testVals.size)
  with(model as ComplexNullableColumns<T>) {
    testVals.zip(dbValues)
        .forEach { (testVal, dbVal) ->
          assertAllExceptNulledComplexColumnsAreUpdated(dbVal, testVal)
        }
  }
}

fun <T> assertEarlyUnsubscribeFromUpdateRollbackedAllValues(): (TestModel<T>, List<T>, AtomicInteger) -> Unit = { model, testVals, eventsCount ->
  val dbValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(dbValues).containsNoneIn(testVals)
  assertThat(eventsCount.get()).isEqualTo(0)
}

fun <T> assertEarlyUnsubscribeFromUpdateStoppedAnyFurtherWork(): (TestModel<T>, List<T>, AtomicInteger) -> Unit = { model, testVals, eventsCount ->
  val dbValues = Select
      .from(model.table)
      .queryDeep()
      .execute()
  assertThat(dbValues).containsAnyIn(testVals)
  assertThat(eventsCount.get()).isEqualTo(0)
}

fun <T> newUpdatableRandom(): (TestModel<T>) -> T = {
  val (newRandom, id) = insertNewRandom(it)
  it.updateAllVals(newRandom, id)
}

class UpdateDualOperation<T>(
    private val updateBuilderCallback: (model: TestModel<T>, testVal: T) -> EntityUpdateBuilder
) : DualOperation<T, T, Boolean> {
  override fun executeTest(model: TestModel<T>, testVal: T): Boolean =
      updateBuilderCallback(model, testVal).execute()

  override fun observeTest(model: TestModel<T>, testVal: T): Boolean =
      updateBuilderCallback(model, testVal)
          .observe()
          .blockingGet(1, TimeUnit.SECONDS) == null
}

class BulkUpdateDualOperation<T>(
    private val updateBuilderCallback: (model: TestModel<T>, testVal: List<T>) -> EntityBulkUpdateBuilder
) : DualOperation<List<T>, T, Boolean> {
  override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean =
      updateBuilderCallback(model, testVal)
          .execute()

  override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean =
      updateBuilderCallback(model, testVal)
          .observe()
          .blockingGet(1, TimeUnit.SECONDS) == null
}