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
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSinglePersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithInsertConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertInsertConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithInsertConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertInsertConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  @Test
  fun executeWithInsertConflictAndConflictIgnoreReturnsIgnored() {
    assertInsertConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun observeWithInsertConflictAndConflictIgnoreReturnsIgnored() {
    assertInsertConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun executeWithUpdateConflictAndDefaultAlgorithmThrowsOperationFailedException() {
    assertUpdateConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithUpdateConflictAndDefaultAlgorithmEmitsOperationFailedException() {
    assertUpdateConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  @Test
  fun executeWithUpdateConflictAndConflictIgnoreReturnsIgnored() {
    assertUpdateConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityPersistResult.Ignored)
    }
  }

  @Test
  fun observeWithUpdateConflictAndConflictIgnoreReturnsIgnored() {
    assertUpdateConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityPersistResult.Ignored)
    }
  }

  private fun <T> assertInsertConflict(
    modelCase: PersistConflictModelCase<T>,
    operation: (EntityPersistBuilder) -> Unit
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithInsertConflict(
      existing = existing,
      sequence = 2
    )

    operation(modelCase.persist(value = candidate))

    assertRowsUnchanged(
      table = modelCase.table,
      expected = before
    )
  }

  private fun <T> assertUpdateConflict(
    modelCase: PersistConflictModelCase<T>,
    operation: (EntityPersistBuilder) -> Unit
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val conflicting = modelCase.newValue(sequence = 2)
    assertSeedInserted(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val before = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithUpdateConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = 3
    )

    operation(modelCase.persist(value = candidate))

    assertRowsUnchanged(
      table = modelCase.table,
      expected = before
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

  private fun <T> assertRowsUnchanged(
    table: Table<T>,
    expected: List<T>
  ) = assertThat(captureRows(table = table))
    .containsExactlyElementsIn(expected)
    .inOrder()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
