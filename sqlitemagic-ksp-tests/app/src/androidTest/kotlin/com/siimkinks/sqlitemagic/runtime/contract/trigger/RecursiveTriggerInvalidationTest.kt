package com.siimkinks.sqlitemagic.runtime.contract.trigger

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import io.reactivex.observers.TestObserver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveTriggerInvalidationTest(
  private val modelCase: RecursiveTriggerModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeSingleInsertRefreshesEachObservedTableOnce() {
    assertSingleInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeSingleInsertRefreshesEachObservedTableOnce() {
    assertSingleInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeSingleUpdateRefreshesEachObservedTableOnce() {
    assertSingleUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeSingleUpdateRefreshesEachObservedTableOnce() {
    assertSingleUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeSinglePersistRefreshesEachObservedTableOnce() {
    assertSinglePersist(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeSinglePersistRefreshesEachObservedTableOnce() {
    assertSinglePersist(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeBulkInsertRefreshesEachObservedTableOnce() {
    assertBulkInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeBulkInsertRefreshesEachObservedTableOnce() {
    assertBulkInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertSingleInsert(
    modelCase: RecursiveTriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.newValue(sequence = 1)
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)
    try {
      val inserted = insertedResult(
        result = runSingleInsert(
          modelCase = modelCase,
          value = value,
          terminal = terminal
        ),
        modelName = modelCase.name
      )
      val expected = DatabaseSnapshot(
        parents = listOf(
          modelCase.expectedAfterInsert(
            value = value,
            result = inserted
          )
        ),
        related = relatedRows(
          modelCase = modelCase,
          values = listOf(value)
        )
      )

      assertOneRefresh(
        observer = parentObserver,
        before = emptyList<Any?>(),
        after = expected.parents
      )
      assertOneRefresh(
        observer = relatedObserver,
        before = emptyList<Any?>(),
        after = checkNotNull(expected.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = expected
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> assertSingleUpdate(
    modelCase: RecursiveTriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val persisted = captureRows(table = modelCase.table).single()
    val value = modelCase.updatedValue(
      value = persisted,
      sequence = 2
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val expected = DatabaseSnapshot(
      parents = listOf(modelCase.expectedAfterUpdate(value = value)),
      related = relatedRows(
        modelCase = modelCase,
        values = listOf(value)
      )
    )
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)

    try {
      assertThat(
        runSingleUpdate(
          modelCase = modelCase,
          value = value,
          terminal = terminal
        )
      ).isTrue()

      assertOneRefresh(
        observer = parentObserver,
        before = before.parents,
        after = expected.parents
      )
      assertOneRefresh(
        observer = relatedObserver,
        before = checkNotNull(before.related),
        after = checkNotNull(expected.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = expected
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> assertSinglePersist(
    modelCase: RecursiveTriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    seedRows(
      modelCase = modelCase,
      count = 1
    )
    val persisted = captureRows(table = modelCase.table).single()
    val value = modelCase.updatedValue(
      value = persisted,
      sequence = 2
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val expected = DatabaseSnapshot(
      parents = listOf(modelCase.expectedAfterUpdate(value = value)),
      related = relatedRows(
        modelCase = modelCase,
        values = listOf(value)
      )
    )
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)

    try {
      assertThat(
        runSinglePersist(
          modelCase = modelCase,
          value = value,
          terminal = terminal
        )
      ).isEqualTo(EntityPersistResult.Updated)

      assertOneRefresh(
        observer = parentObserver,
        before = before.parents,
        after = expected.parents
      )
      assertOneRefresh(
        observer = relatedObserver,
        before = checkNotNull(before.related),
        after = checkNotNull(expected.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = expected
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> assertBulkInsert(
    modelCase: RecursiveTriggerModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)

    try {
      assertThat(
        runBulkInsert(
          modelCase = modelCase,
          values = values,
          terminal = terminal
        )
      ).isTrue()

      val actual = captureDatabaseSnapshot(modelCase = modelCase)
      val expected = DatabaseSnapshot(
        parents = modelCase.expectedAfterBulkInsert(
          values = values,
          actual = actual.parents
        ),
        related = relatedRows(
          modelCase = modelCase,
          values = values
        )
      )
      assertOneRefresh(
        observer = parentObserver,
        before = emptyList<Any?>(),
        after = expected.parents
      )
      assertOneRefresh(
        observer = relatedObserver,
        before = emptyList<Any?>(),
        after = checkNotNull(expected.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = expected
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> runSingleInsert(
    modelCase: RecursiveTriggerModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .insert(value = value)
      .execute()
    OperationTerminal.OBSERVE -> modelCase
      .insert(value = value)
      .observe()
      .blockingGet()
  }

  private fun <T> runSingleUpdate(
    modelCase: RecursiveTriggerModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .update(value = value)
      .execute()
    OperationTerminal.OBSERVE -> {
      modelCase
        .update(value = value)
        .observe()
        .blockingAwait()
      true
    }
  }

  private fun <T> runSinglePersist(
    modelCase: RecursiveTriggerModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .persist(value = value)
      .execute()
    OperationTerminal.OBSERVE -> modelCase
      .persist(value = value)
      .observe()
      .blockingGet()
  }

  private fun <T> runBulkInsert(
    modelCase: RecursiveTriggerModelCase<T>,
    values: List<T>,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .bulkInsert(values = values)
      .execute()
    OperationTerminal.OBSERVE -> {
      modelCase
        .bulkInsert(values = values)
        .observe()
        .blockingAwait()
      true
    }
  }

  private fun assertOneRefresh(
    observer: TestObserver<out List<*>>,
    before: List<*>,
    after: List<*>
  ) {
    assertThat(observer.values()).hasSize(2)
    assertThat(observer.values()[0]).containsExactlyElementsIn(before)
    assertThat(observer.values()[1]).containsExactlyElementsIn(after)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveTriggerCases
  }
}
