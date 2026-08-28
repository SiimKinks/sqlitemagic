package com.siimkinks.sqlitemagic.runtime.contract.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSingleUpdateConflictTest(
  private val modelCase: UpdateConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithConflictAndDefaultAlgorithmThrowsSQLiteConstraintException() {
    assertConflict(modelCase = modelCase) { builder ->
      assertThrows(SQLiteConstraintException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithConflictAndDefaultAlgorithmEmitsSQLiteConstraintException() {
    assertConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(SQLiteConstraintException::class.java)
    }
  }

  @Test
  fun executeWithConflictIgnoreReturnsFalse() {
    assertConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
    }
  }

  @Test
  fun observeWithConflictIgnoreCompletes() {
    assertConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertComplete()
    }
  }

  private fun <T> assertConflict(
    modelCase: UpdateConflictModelCase<T>,
    operation: (EntityUpdateBuilder) -> Unit
  ) {
    val existing = modelCase.newValue(sequence = 1)
    val conflicting = modelCase.newValue(sequence = 2)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    assertSeedInserted(
      result = modelCase
        .insert(value = conflicting)
        .execute(),
      modelName = modelCase.name
    )
    val persistedValues = captureRows(table = modelCase.table)
    val candidate = modelCase.valueWithConflict(
      existing = persistedValues[0],
      conflicting = persistedValues[1],
      sequence = 3
    )

    operation(modelCase.update(value = candidate))

    assertThat(captureRows(table = modelCase.table))
      .containsExactlyElementsIn(persistedValues)
      .inOrder()
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

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.updateConflictCases
  }
}
