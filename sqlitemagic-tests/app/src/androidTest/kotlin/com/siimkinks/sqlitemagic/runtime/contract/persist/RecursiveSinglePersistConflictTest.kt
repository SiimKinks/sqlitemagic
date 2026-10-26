package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
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
internal class RecursiveSinglePersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeWithParentConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE
  )

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE
  )

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithParentConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.PARENT,
    terminal = OBSERVE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE
  )

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = OBSERVE
  )

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
    terminal = EXECUTE,
    conflictAlgorithm = CONFLICT_IGNORE
  )

  @Test
  fun observeWithChildConflictAndConflictIgnoreReturnsIgnored() = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflictTarget.CHILD,
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
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
