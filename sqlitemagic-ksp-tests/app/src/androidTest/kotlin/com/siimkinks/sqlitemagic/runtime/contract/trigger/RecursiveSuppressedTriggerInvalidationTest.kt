package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerConflictModelCase
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
class RecursiveSuppressedTriggerInvalidationTest(
  private val modelCase: RecursiveTriggerConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeParentUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeParentUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeChildUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeChildUpdateConflictWithConflictIgnoreDoesNotRefresh() {
    assertUpdateConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeParentPersistConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeParentPersistConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeChildPersistConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeChildPersistConflictWithConflictIgnoreDoesNotRefresh() {
    assertPersistConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: RecursiveTriggerConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val persisted = seedConflictRows(modelCase = modelCase)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)
    try {
      val candidate = modelCase.valueWithUpdateConflict(
        existing = persisted[0],
        conflicting = persisted[1],
        conflict = conflict,
        sequence = 3
      )

      runUpdateConflict(
        modelCase = modelCase,
        value = candidate,
        terminal = terminal
      )
      assertNoRefresh(
        observer = parentObserver,
        expected = before.parents
      )
      assertNoRefresh(
        observer = relatedObserver,
        expected = checkNotNull(before.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> assertPersistConflict(
    modelCase: RecursiveTriggerConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val persisted = seedConflictRows(modelCase = modelCase)
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val parentObserver = observeRows(table = modelCase.table)
    val relatedObserver = observeRows(table = modelCase.relatedTable)
    try {
      val candidate = modelCase.valueWithUpdateConflict(
        existing = persisted[0],
        conflicting = persisted[1],
        conflict = conflict,
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
        observer = parentObserver,
        expected = before.parents
      )
      assertNoRefresh(
        observer = relatedObserver,
        expected = checkNotNull(before.related)
      )
      assertDatabaseSnapshotIgnoringOrder(
        modelCase = modelCase,
        expected = before
      )
    } finally {
      parentObserver.dispose()
      relatedObserver.dispose()
    }
  }

  private fun <T> seedConflictRows(
    modelCase: RecursiveTriggerConflictModelCase<T>
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

  private fun <T> runUpdateConflict(
    modelCase: RecursiveTriggerConflictModelCase<T>,
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
    modelCase: RecursiveTriggerConflictModelCase<T>,
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
    fun modelCases() = ModelCatalog.recursiveTriggerConflictCases
  }
}
