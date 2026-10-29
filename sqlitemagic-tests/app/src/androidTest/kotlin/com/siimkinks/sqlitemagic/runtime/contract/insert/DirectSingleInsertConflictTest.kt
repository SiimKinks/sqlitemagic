package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.seed
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class DirectSingleInsertConflictTest(
  private val modelCase: UniqueInsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun executeWithoutConflictAlgorithmThrowsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeWithoutConflictAlgorithmEmitsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> assertConflict(
    modelCase: UniqueInsertModelCase<T>,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.seed()
    val candidate = modelCase.conflictingValue(
      existing = existing,
      sequence = 2
    )
    val builder = modelCase
      .insert(value = candidate)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(SQLiteConstraintException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityInsertResult.Ignored)
      }
      OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(SQLiteConstraintException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityInsertResult.Ignored)
      }
    }

    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = listOf(existing)
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.uniqueInsertCases
  }
}
