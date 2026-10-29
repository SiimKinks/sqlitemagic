package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TriggerConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.observeRows
import com.siimkinks.sqlitemagic.runtime.support.seed
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.runtime.support.withReactiveAdapters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectSuppressedTriggerInvalidationTest(
  private val modelCase: TriggerConflictModelCase<*>,
  private val adapter: ReactiveAdapter
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeInsertConflictWithConflictIgnoreDoesNotRefresh() = assertInsertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeInsertConflictWithConflictIgnoreDoesNotRefresh() = assertInsertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executePersistInsertConflictWithConflictIgnoreDoesNotRefresh() = assertPersistInsertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistInsertConflictWithConflictIgnoreDoesNotRefresh() = assertPersistInsertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executePersistUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertPersistUpdateConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observePersistUpdateConflictWithConflictIgnoreDoesNotRefresh() = assertPersistUpdateConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertInsertConflict(
    modelCase: TriggerConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = modelCase.seed()
    val persisted = captureRows(table = modelCase.table).single()
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
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
    val persisted = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
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
    val existing = modelCase.seed()
    val persisted = captureRows(table = modelCase.table).single()
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
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
    val persisted = seedRows(
      modelCase = modelCase,
      count = 2,
      firstSequence = 1
    )
    val before = captureDatabaseSnapshot(modelCase = modelCase)
    val observer = observeRows(
      adapter = adapter,
      table = modelCase.table
    )
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

  private fun <T> runInsertConflict(
    modelCase: TriggerConflictModelCase<T>,
    value: T,
    terminal: OperationTerminal
  ) = terminal.select(
    execute = {
      modelCase
        .insert(value = value)
        .withConflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    },
    observe = {
      adapter
        .observe(
          modelCase
            .insert(value = value)
            .withConflictAlgorithm(CONFLICT_IGNORE)
        )
    }
  )

  private fun <T> runUpdateConflict(
    modelCase: TriggerConflictModelCase<T>,
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
      OBSERVE -> adapter
        .observe(
          modelCase
            .update(value = value)
            .withConflictAlgorithm(CONFLICT_IGNORE)
        )
        .await()
    }
  }

  private fun <T> runPersistConflict(
    modelCase: TriggerConflictModelCase<T>,
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
      adapter
        .observe(
          modelCase
            .persist(value = value)
            .withConflictAlgorithm(CONFLICT_IGNORE)
        )
    }
  )

  companion object {
    @JvmStatic
    @Parameters(name = "{0} {1}")
    fun modelCases() = withReactiveAdapters(ModelCatalog.triggerConflictCases)
  }
}
