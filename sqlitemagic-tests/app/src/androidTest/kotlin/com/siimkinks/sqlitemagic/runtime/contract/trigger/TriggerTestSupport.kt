package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import io.reactivex.observers.TestObserver

internal fun <T> observeRows(table: Table<T>) = Select
  .from(table)
  .queryDeep()
  .observe()
  .runQuery()
  .test()

internal fun insertedResult(
  result: EntityInsertResult,
  modelName: String
) = when (result) {
  is EntityInsertResult.Inserted -> result
  EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for $modelName")
}

internal fun <T, R> assertOneQueryInvalidation(
  modelCase: RuntimeModelCase<T>,
  before: DatabaseSnapshot<T>,
  operation: () -> R,
  expected: (R) -> DatabaseSnapshot<T>
) {
  val observer = observeRows(table = modelCase.table)
  try {
    assertObservedRows(
      observer = observer,
      expected = before.parents
    )
    val result = operation()
    val after = expected(result)
    observer.assertNoErrors()
    assertThat(observer.values()).hasSize(2)
    assertObservedRows(
      observer = observer,
      expected = after.parents
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = after
    )
  } finally {
    observer.dispose()
  }
}

internal fun <T> assertNoQueryInvalidation(
  modelCase: RuntimeModelCase<T>,
  expected: DatabaseSnapshot<T>,
  operation: () -> Unit
) {
  val observer = observeRows(table = modelCase.table)
  try {
    assertObservedRows(
      observer = observer,
      expected = expected.parents
    )
    operation()
    observer.assertNoErrors()
    assertThat(observer.values()).hasSize(1)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = expected
    )
  } finally {
    observer.dispose()
  }
}

internal fun assertNoRefresh(
  observer: TestObserver<out List<*>>,
  expected: List<*>
) {
  assertThat(observer.values()).hasSize(1)
  assertThat(observer.values().single()).containsExactlyElementsIn(expected)
}

private fun <T> assertObservedRows(
  observer: TestObserver<List<T>>,
  expected: List<T>
) {
  assertThat(observer.values().last())
    .containsExactlyElementsIn(expected)
}
