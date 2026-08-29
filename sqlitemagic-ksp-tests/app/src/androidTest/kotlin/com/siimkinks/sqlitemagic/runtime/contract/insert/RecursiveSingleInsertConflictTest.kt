package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
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
class RecursiveSingleInsertConflictTest(
  private val modelCase: RecursiveInsertConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun executeWithParentConflictWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
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
    val parentBefore = captureRows(modelCase.table)
    val relatedBefore = captureRows(modelCase.relatedTable)
    val builder = modelCase
      .insert(
        value = modelCase.valueWithConflict(
          existing = existing,
          conflict = conflict,
          sequence = 2
        )
      )
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
      expected = parentBefore
    )
    assertRowsInOrder(
      table = modelCase.relatedTable,
      expected = relatedBefore
    )
  }

  private fun <T> RecursiveInsertConflictModelCase<T>.valueWithConflict(
    existing: T,
    conflict: RecursiveConflictTarget,
    sequence: Int
  ) = when (conflict) {
    RecursiveConflictTarget.PARENT -> valueWithParentConflict(
      existing = existing,
      sequence = sequence
    )
    RecursiveConflictTarget.CHILD -> valueWithChildConflict(
      existing = existing,
      sequence = sequence
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveInsertConflictCases
  }
}
