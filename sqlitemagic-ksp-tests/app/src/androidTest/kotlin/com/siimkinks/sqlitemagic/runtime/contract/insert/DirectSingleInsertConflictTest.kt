package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSingleInsertConflictTest(
  private val modelCase: UniqueInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun executeWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertConflict(
    modelCase: UniqueInsertModelCase<T>,
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
    val candidate = modelCase.conflictingValue(
      existing = existing,
      sequence = 2
    )
    val builder = modelCase
      .insert(value = candidate)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      OperationTerminal.EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(OperationFailedException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(EntityInsertResult.Ignored)
      }
      OperationTerminal.OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(OperationFailedException::class.java)
        else -> builder
          .observe()
          .test()
          .assertResult(EntityInsertResult.Ignored)
      }
    }

    assertRowsInOrder(
      table = modelCase.table,
      expected = listOf(existing)
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.uniqueInsertCases
  }
}
