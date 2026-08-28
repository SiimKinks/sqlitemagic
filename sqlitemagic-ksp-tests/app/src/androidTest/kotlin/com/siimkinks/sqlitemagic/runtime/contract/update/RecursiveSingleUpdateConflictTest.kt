package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThrows(SQLiteConstraintException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
  }

  @Test
  fun executeWithParentConflictIgnoreReturnsFalse() {
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
    }
  }

  @Test
  fun observeWithParentConflictIgnoreCompletes() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertComplete()
    }
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThrows(SQLiteConstraintException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
  }

  @Test
  fun executeWithChildConflictIgnoreReturnsFalse() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
    }
  }

  @Test
  fun observeWithChildConflictIgnoreCompletes() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertComplete()
    }
  }

  private fun <T> assertParentConflict(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    operation: (EntityUpdateBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflict.PARENT,
    operation = operation
  )

  private fun <T> assertChildConflict(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    operation: (EntityUpdateBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    conflict = RecursiveConflict.CHILD,
    operation = operation
  )

  private fun <T> assertConflict(
    modelCase: RecursiveUpdateConflictModelCase<T>,
    conflict: RecursiveConflict,
    operation: (EntityUpdateBuilder) -> Unit
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
      RecursiveConflict.PARENT -> modelCase.valueWithParentConflict(
        existing = expectedParents[0],
        conflicting = expectedParents[1],
        sequence = 3
      )
      RecursiveConflict.CHILD -> modelCase.valueWithChildConflict(
        existing = expectedParents[0],
        conflicting = expectedParents[1],
        sequence = 3
      )
    }

    operation(modelCase.update(value = candidateValue))

    assertRowsUnchanged(
      table = modelCase.table,
      expected = expectedParents
    )
    assertRowsUnchanged(
      table = modelCase.relatedTable,
      expected = expectedRelated
    )
  }

  private fun assertSeedInserted(
    result: EntityInsertResult,
    modelName: String
  ) = when (result) {
    is EntityInsertResult.Inserted -> Unit
    EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $modelName")
  }

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private fun assertRowsUnchanged(
    table: Table<*>,
    expected: List<*>
  ) = assertThat(
    Select
      .from(table)
      .queryDeep()
      .execute()
  )
    .containsExactlyElementsIn(expected)
    .inOrder()

  private enum class RecursiveConflict {
    PARENT,
    CHILD
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveUpdateConflictCases
  }
}
