package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
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
class RecursiveSingleUpdateConflictTest(
  private val modelCase: RecursiveUpdateConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithParentConflictIgnoreReturnsFalse() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithParentConflictIgnoreCompletes() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictIgnoreReturnsFalse() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  @Test
  fun observeWithChildConflictIgnoreCompletes() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE,
      conflictAlgorithm = CONFLICT_IGNORE
    )
  }

  private fun <T> assertConflict(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val target = modelCase.newValue(sequence = 1)
    val conflicting = modelCase.newValue(sequence = 2)
    assertSeedInserted(
      result = modelCase
        .insert(value = target)
        .execute(),
      modelName = modelCase.name
    )
    assertSeedInserted(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val expectedParents = captureRows(table = modelCase.table)
    val expectedRelated = captureRows(table = modelCase.relatedTable)
    val candidateValue = when (conflict) {
      RecursiveConflictTarget.PARENT -> modelCase.valueWithParentConflict(
        existing = expectedParents[0],
        conflicting = expectedParents[1],
        sequence = 3
      )
      RecursiveConflictTarget.CHILD -> modelCase.valueWithChildConflict(
        existing = expectedParents[0],
        conflicting = expectedParents[1],
        sequence = 3
      )
    }

    val builder = modelCase
      .update(value = candidateValue)
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      OperationTerminal.EXECUTE -> when (conflictAlgorithm) {
        null -> assertThrows(SQLiteConstraintException::class.java, builder::execute)
        else -> assertThat(builder.execute()).isFalse()
      }
      OperationTerminal.OBSERVE -> when (conflictAlgorithm) {
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

    assertRowsInOrder(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsInOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveUpdateConflictCases
  }
}
