package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSuppressedTriggerInvalidationTest(
  private val modelCase: TriggerConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeInsertConflictWithConflictIgnoreDoesNotRefresh() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeInsertConflictWithConflictIgnoreDoesNotRefresh() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executePersistInsertConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observePersistInsertConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executePersistUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observePersistUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertInsertConflict(
    modelCase: TriggerConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val persisted = captureRows(table = modelCase.table).single()
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(table = modelCase.table)
    try {
      val candidate = modelCase.valueWithInsertConflict(
        existing = persisted,
        sequence = 2
      )

      assertThat(
        runInsertConflict(
          modelCase = modelCase,
          value = candidate,
          terminal = terminal
        )
      ).isEqualTo(EntityInsertResult.Ignored)
      assertNoRefresh(
        observer = observer,
        expected = before.parents
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      observer.dispose()
    }
  }

  private fun <T> assertUpdateConflict(
    modelCase: TriggerConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val persisted = seedConflictRows(modelCase = modelCase)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(table = modelCase.table)
    try {
      val candidate = modelCase.valueWithUpdateConflict(
        existing = persisted[0],
        conflicting = persisted[1],
        sequence = 3
      )

      runUpdateConflict(
        modelCase = modelCase,
        value = candidate,
        terminal = terminal
      )
      assertNoRefresh(
        observer = observer,
        expected = before.parents
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      observer.dispose()
    }
  }

  private fun <T> assertPersistInsertConflict(
    modelCase: TriggerConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val persisted = captureRows(table = modelCase.table).single()
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(table = modelCase.table)
    try {
      val candidate = modelCase.valueWithInsertConflict(
        existing = persisted,
        sequence = 2
      )

      assertThat(
        runPersistConflict(
          modelCase = modelCase,
          value = candidate,
          terminal = terminal
        )
      ).isEqualTo(EntityPersistResult.Ignored)
      assertNoRefresh(
        observer = observer,
        expected = before.parents
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      observer.dispose()
    }
  }

  private fun <T> assertPersistUpdateConflict(
    modelCase: TriggerConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val persisted = seedConflictRows(modelCase = modelCase)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(table = modelCase.table)
    try {
      val candidate = modelCase.valueWithUpdateConflict(
        existing = persisted[0],
        conflicting = persisted[1],
        sequence = 3
      )

      assertThat(
        runPersistConflict(
          modelCase = modelCase,
          value = candidate,
          terminal = terminal
        )
      ).isEqualTo(EntityPersistResult.Ignored)
      assertNoRefresh(
        observer = observer,
        expected = before.parents
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      observer.dispose()
    }
  }

  private fun <T> seedConflictRows(
    modelCase: TriggerConflictModelCase<T>
  ): List<T> {
    val values = listOf(
      modelCase.newValue(sequence = 1),
      modelCase.newValue(sequence = 2)
    )
    values.forEach { value ->
      assertSeedInserted(
        result = modelCase
          .insert(value = value)
          .execute(),
        modelName = modelCase.name
      )
    }
    return captureRows(table = modelCase.table)
  }

  private fun <T> runInsertConflict(
    modelCase: TriggerConflictModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .insert(value = value)
      .withConflictAlgorithm(CONFLICT_IGNORE)
      .execute()
    OperationTerminal.OBSERVE -> modelCase
      .insert(value = value)
      .withConflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .blockingGet()
  }

  private fun <T> runUpdateConflict(
    modelCase: TriggerConflictModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) {
    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        modelCase
          .update(value = value)
          .withConflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> modelCase
        .update(value = value)
        .withConflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .blockingAwait()
    }
  }

  private fun <T> runPersistConflict(
    modelCase: TriggerConflictModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = when (terminal) {
    OperationTerminal.EXECUTE -> modelCase
      .persist(value = value)
      .withConflictAlgorithm(CONFLICT_IGNORE)
      .execute()
    OperationTerminal.OBSERVE -> modelCase
      .persist(value = value)
      .withConflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .blockingGet()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.triggerConflictCases
  }
}
