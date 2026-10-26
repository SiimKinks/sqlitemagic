package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.observeRows
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveSuppressedTriggerInvalidationTest(
  private val modelCase: RecursiveTriggerConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeParentUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeParentUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeChildUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeChildUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeParentPersistConflictWithConflictIgnoreDoesNotRefresh() = assertPersistConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeParentPersistConflictWithConflictIgnoreDoesNotRefresh() = assertPersistConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeChildPersistConflictWithConflictIgnoreDoesNotRefresh() = assertPersistConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeChildPersistConflictWithConflictIgnoreDoesNotRefresh() = assertPersistConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  private fun <T> assertUpdateConflict(
    modelCase: RecursiveTriggerConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val persisted = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
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
    val persisted = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
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

  private fun <T> runUpdateConflict(
    modelCase: RecursiveTriggerConflictModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) {
    when (terminal) {
      EXECUTE -> assertThat(
        modelCase
          .update(value = value)
          .withConflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      OBSERVE -> modelCase
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
  ) = terminal.select(
    execute = {
      modelCase
        .persist(value = value)
        .withConflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    },
    observe = {
      modelCase
        .persist(value = value)
        .withConflictAlgorithm(CONFLICT_IGNORE)
        .observe()
    }
  )

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveTriggerConflictCases
  }
}
