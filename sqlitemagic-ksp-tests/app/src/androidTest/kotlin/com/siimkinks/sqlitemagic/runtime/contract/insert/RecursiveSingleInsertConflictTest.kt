package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreReturnsIgnored() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun executeWithParentConflictWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithParentConflictWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  @Test
  fun executeWithChildConflictWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithChildConflictWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  private fun <T> assertParentConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    operation: (EntityInsertBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    candidate = { existing -> modelCase.valueWithParentConflict(existing = existing, sequence = 2) },
    operation = operation
  )

  private fun <T> assertChildConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    operation: (EntityInsertBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    candidate = { existing -> modelCase.valueWithChildConflict(existing = existing, sequence = 2) },
    operation = operation
  )

  private fun <T> assertConflict(
    modelCase: RecursiveInsertConflictModelCase<T>,
    candidate: (T) -> T,
    operation: (EntityInsertBuilder) -> Unit
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
    operation(modelCase.insert(value = candidate(existing)))

    assertThat(captureRows(modelCase.table))
      .containsExactlyElementsIn(parentBefore)
      .inOrder()
    assertThat(captureRows(modelCase.relatedTable))
      .containsExactlyElementsIn(relatedBefore)
      .inOrder()
  }

  private fun assertSeedInserted(
    result: EntityInsertResult,
    modelName: String
  ) = when (result) {
    is EntityInsertResult.Inserted -> Unit
    EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $modelName")
  }

  private fun <T> captureRows(table: Table<T>): List<T> = Select
    .from(table)
    .queryDeep()
    .execute()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursiveInsertConflictCases
  }
}
