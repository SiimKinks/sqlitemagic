package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
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
class RecursiveSinglePersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

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
  fun executeWithChildConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
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

  private fun <T> assertConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
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
    val expectedParents = captureRows(table = modelCase.table)
    val expectedRelated = captureRows(table = modelCase.relatedTable)

    val builder = modelCase
      .persist(
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
      expected = expectedParents
    )
    assertRowsInOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  private fun <T> RecursivePersistConflictModelCase<T>.valueWithConflict(
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
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
