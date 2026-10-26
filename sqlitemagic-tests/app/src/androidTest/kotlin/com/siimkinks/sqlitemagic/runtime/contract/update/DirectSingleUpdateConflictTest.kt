package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectSingleUpdateConflictTest(
  private val modelCase: UpdateConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeWithConflictIgnoreReturnsFalse() = assertConflict(
    modelCase = modelCase,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithConflictIgnoreCompletes() = assertConflict(
    modelCase = modelCase,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  private fun <T> assertConflict(
    modelCase: UpdateConflictModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.newValue(sequence = 1)
    val conflicting = modelCase.newValue(sequence = 2)
    insertedResult(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    insertedResult(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val persistedValues = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithConflict(
      existing = persistedValues[0],
      conflicting = persistedValues[1],
      sequence = 3
    )

    val builder = modelCase
      .update(value = candidate)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(SQLiteConstraintException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isFalse()
      }
      OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(SQLiteConstraintException::class.java)
        else -> builder
          .observe()
          .test()
          .assertComplete()
      }
    }

    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = persistedValues
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateConflictCases
  }
}
