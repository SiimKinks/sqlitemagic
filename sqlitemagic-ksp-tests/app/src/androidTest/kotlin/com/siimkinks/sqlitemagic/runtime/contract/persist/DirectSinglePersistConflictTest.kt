package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSinglePersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithInsertConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithInsertConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithInsertConflictAndConflictIgnoreReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithInsertConflictAndConflictIgnoreReturnsIgnored() {
    assertInsertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun executeWithUpdateConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithUpdateConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithUpdateConflictAndConflictIgnoreReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithUpdateConflictAndConflictIgnoreReturnsIgnored() {
    assertUpdateConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  private fun <T> assertInsertConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithInsertConflict(
      existing = existing,
      sequence = 2
    )

    val builder = modelCase
      .persist(value = candidate)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      OperationTerminal.EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(OperationFailedException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityPersistResult.Ignored)
      }
      OperationTerminal.OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(OperationFailedException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityPersistResult.Ignored)
      }
    }

    assertRowsInOrder(
      table = modelCase.table,
      expected = before
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val conflicting = modelCase.newValue(sequence = 2)
    assertSeedInserted(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithUpdateConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = 3
    )

    val builder = modelCase
      .persist(value = candidate)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      OperationTerminal.EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(OperationFailedException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityPersistResult.Ignored)
      }
      OperationTerminal.OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(OperationFailedException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityPersistResult.Ignored)
      }
    }

    assertRowsInOrder(
      table = modelCase.table,
      expected = before
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
