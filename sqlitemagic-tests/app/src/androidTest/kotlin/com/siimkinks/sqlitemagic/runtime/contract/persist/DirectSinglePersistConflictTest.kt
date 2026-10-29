package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectSinglePersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithInsertConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() = assertInsertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithInsertConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() = assertInsertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithInsertConflictAndConflictIgnoreReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithInsertConflictAndConflictIgnoreReturnsIgnored() = assertInsertConflict(
    modelCase = modelCase,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun executeWithUpdateConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithUpdateConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithUpdateConflictAndConflictIgnoreReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithUpdateConflictAndConflictIgnoreReturnsIgnored() = assertUpdateConflict(
    modelCase = modelCase,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  private fun <T> assertInsertConflict(
    modelCase: PersistConflictModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.newValue(sequence = 1)
    insertedResult(
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
      EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(SQLiteConstraintException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityPersistResult.Ignored)
      }
      OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(SQLiteConstraintException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityPersistResult.Ignored)
      }
    }

    assertRowsIgnoringOrder(
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
    insertedResult(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val conflicting = modelCase.newValue(sequence = 2)
    insertedResult(
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
      EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(SQLiteConstraintException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityPersistResult.Ignored)
      }
      OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(SQLiteConstraintException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityPersistResult.Ignored)
      }
    }

    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = before
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
