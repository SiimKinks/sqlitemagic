package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult.Ignored
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog.recursiveInsertConflictCases
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.CHILD
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget.PARENT
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.isConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.seed
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class RecursiveSingleInsertConflictTest(
  private val modelCase: RecursiveInsertConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

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

  @Test
  fun executeWithParentConflictWithoutConflictAlgorithmThrowsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictWithoutConflictAlgorithmEmitsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictWithoutConflictAlgorithmThrowsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictWithoutConflictAlgorithmEmitsConstraintFailure() = assertConflict(
    modelCase = modelCase,
    conflict = CHILD,
    terminal = OBSERVE
  )

  private fun <T> assertConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal,
    conflictAlgorithm: Int? = null
  ) {
    val existing = modelCase.seed()
    val parentBefore = captureRows(table = modelCase.table)
    val relatedBefore = captureRows(table = modelCase.relatedTable)
    val builder = modelCase
      .insert(
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
      expected = parentBefore
    )
    assertRowsIgnoringOrder(
      table = modelCase.relatedTable,
      expected = relatedBefore
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = recursiveInsertConflictCases
  }
}
