package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveConflictTarget
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsInOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.relatedRows
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecursiveBulkPersistConflictTest(
  private val modelCase: RecursivePersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithParentConflictAndDefaultAlgorithmReturnsFalseAndRollsBackWholeBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRollsBackWholeBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmReturnsFalseAndRollsBackWholeBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRollsBackWholeBatch() {
    assertDefaultConflict(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() {
    assertConflictIgnore(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() {
    assertConflictIgnore(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.PARENT,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsTrueAndCommitsGraphsAroundConflict() {
    assertConflictIgnore(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreCompletesAndCommitsGraphsAroundConflict() {
    assertConflictIgnore(
      modelCase = modelCase,
      conflict = RecursiveConflictTarget.CHILD,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeWithAllParentAndChildConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeWithAllParentAndChildConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflicts(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  private fun <T> assertDefaultConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = defaultConflictScenario(
      modelCase = modelCase,
      conflict = conflict
    )

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values
        ).execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      )
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertConflictIgnore(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget,
    terminal: OperationTerminal
  ) {
    val scenario = mixedConflictScenario(
      modelCase = modelCase,
      conflict = conflict
    )

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isTrue()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> assertAllConflicts(
    modelCase: RecursivePersistConflictModelCase<T>,
    terminal: OperationTerminal
  ) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    when (terminal) {
      OperationTerminal.EXECUTE -> assertThat(
        bulkPersist(
          modelCase = modelCase,
          values = scenario.values,
          conflictAlgorithm = CONFLICT_IGNORE
        ).execute()
      ).isFalse()
      OperationTerminal.OBSERVE -> bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      )
        .observe()
        .test()
        .assertComplete()
    }
    assertSnapshot(
      modelCase = modelCase,
      expectedParents = scenario.expectedParents,
      expectedRelated = scenario.expectedRelated
    )
  }

  private fun <T> bulkPersist(
    modelCase: RecursivePersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> defaultConflictScenario(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkPersistScenario(
        values = listOf(
          modelCase.newValue(sequence = 2),
          modelCase.valueWithConflict(
            existing = seed,
            conflict = conflict,
            sequence = 3
          ),
          modelCase.newValue(sequence = 4)
        ),
        expectedParents = listOf(seed),
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = listOf(seed)
        )
      )
    }

  private fun <T> mixedConflictScenario(
    modelCase: RecursivePersistConflictModelCase<T>,
    conflict: RecursiveConflictTarget
  ) = modelCase
    .seed()
    .let { seed ->
      val firstFresh = modelCase.newValue(sequence = 2)
      val secondFresh = modelCase.newValue(sequence = 4)
      val expectedParents = listOf(seed, firstFresh, secondFresh)
      RecursiveBulkPersistScenario(
        values = listOf(
          firstFresh,
          modelCase.valueWithConflict(
            existing = seed,
            conflict = conflict,
            sequence = 3
          ),
          secondFresh
        ),
        expectedParents = expectedParents,
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = expectedParents
        )
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

  private fun <T> allConflictsScenario(
    modelCase: RecursivePersistConflictModelCase<T>
  ) = modelCase
    .seed()
    .let { seed ->
      RecursiveBulkPersistScenario(
        values = listOf(
          modelCase.valueWithParentConflict(
            existing = seed,
            sequence = 2
          ),
          modelCase.valueWithChildConflict(
            existing = seed,
            sequence = 3
          )
        ),
        expectedParents = listOf(seed),
        expectedRelated = relatedRows(
          modelCase = modelCase,
          values = listOf(seed)
        )
      )
    }

  private fun <T> RecursivePersistConflictModelCase<T>.seed(): T {
    val value = newValue(sequence = 1)
    when (insert(value = value).execute()) {
      is EntityInsertResult.Inserted -> Unit
      EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $name")
    }
    return captureRows(table = table).single()
  }

  private fun <T> assertSnapshot(
    modelCase: RecursivePersistConflictModelCase<T>,
    expectedParents: List<T>,
    expectedRelated: List<Any?>
  ) {
    assertRowsInOrder(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsInOrder(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  private data class RecursiveBulkPersistScenario<T>(
    val values: List<T>,
    val expectedParents: List<T>,
    val expectedRelated: List<Any?>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
