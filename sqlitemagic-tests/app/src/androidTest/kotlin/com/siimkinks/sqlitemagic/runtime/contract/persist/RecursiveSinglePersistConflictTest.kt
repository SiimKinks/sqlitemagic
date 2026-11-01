package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult.Ignored
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursivePersistConflictCases
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.CHILD
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.PARENT
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.isConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveSinglePersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  private fun <T> assertConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
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
    val expectedParents = captureRows(table = modelCase.table)
    val expectedRelated = captureRows(table = modelCase.relatedTable)

    val builder = modelCase
      .persist(
        value = modelCase.valueWithInsertConflict(
          existing = existing,
          conflict = conflict,
          sequence = 2
        )
      )
      .withConflictAlgorithm(conflictAlgorithm)
    when (terminal) {
      EXECUTE -> when (conflictAlgorithm) {
        null -> assertConstraintFailure(builder::execute)
        else -> assertThat(builder.execute()).isEqualTo(Ignored)
      }
      OBSERVE -> when (conflictAlgorithm) {
        null -> builder
          .observe()
          .test()
          .assertFailure(Throwable::isConstraintFailure)
        else -> builder
          .observe()
          .test()
          .assertResult(Ignored)
      }
    }

    assertRowsIgnoringOrder(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = recursivePersistConflictCases
  }
}
