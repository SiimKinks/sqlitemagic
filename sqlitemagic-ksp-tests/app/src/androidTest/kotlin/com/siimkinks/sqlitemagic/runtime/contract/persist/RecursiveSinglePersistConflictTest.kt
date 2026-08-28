package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
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
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithParentConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  @Test
  fun executeWithParentConflictAndConflictIgnoreReturnsIgnored() {
    assertParentConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun observeWithParentConflictAndConflictIgnoreReturnsIgnored() {
    assertParentConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun executeWithChildConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithChildConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  @Test
  fun executeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertChildConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun observeWithChildConflictAndConflictIgnoreReturnsIgnored() {
    assertChildConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityPersistResult.Ignored)
    }
  }

  private fun <T> assertParentConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
    operation: (EntityPersistBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    candidate = { existing ->
      modelCase.valueWithParentConflict(
        existing = existing,
        sequence = 2
      )
    },
    operation = operation
  )

  private fun <T> assertChildConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
    operation: (EntityPersistBuilder) -> Unit
  ) = assertConflict(
    modelCase = modelCase,
    candidate = { existing ->
      modelCase.valueWithChildConflict(
        existing = existing,
        sequence = 2
      )
    },
    operation = operation
  )

  private fun <T> assertConflict(
    modelCase: RecursivePersistConflictModelCase<T>,
    candidate: (T) -> T,
    operation: (EntityPersistBuilder) -> Unit
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

    operation(modelCase.persist(value = candidate(existing)))

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

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.recursivePersistConflictCases
  }
}
