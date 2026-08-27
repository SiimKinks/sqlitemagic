package com.siimkinks.sqlitemagic.runtime.contract.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectSingleInsertConflictTest(
  private val modelCase: UniqueInsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithConflictIgnoreReturnsIgnored() {
    assertConflict(modelCase = modelCase) { builder ->
      assertThat(
        builder
          .conflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isEqualTo(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun observeWithConflictIgnoreReturnsIgnored() {
    assertConflict(modelCase = modelCase) { builder ->
      builder
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .test()
        .assertResult(EntityInsertResult.Ignored)
    }
  }

  @Test
  fun executeWithoutConflictAlgorithmThrowsOperationFailedException() {
    assertConflict(modelCase = modelCase) { builder ->
      assertThrows(OperationFailedException::class.java) {
        builder.execute()
      }
    }
  }

  @Test
  fun observeWithoutConflictAlgorithmEmitsOperationFailedException() {
    assertConflict(modelCase = modelCase) { builder ->
      builder
        .observe()
        .test()
        .assertFailure(OperationFailedException::class.java)
    }
  }

  private fun <T> assertConflict(
    modelCase: UniqueInsertModelCase<T>,
    operation: (EntityInsertBuilder) -> Unit
  ) {
    val existing = modelCase.newValue(sequence = 1)
    assertSeedInserted(
      result = modelCase
        .insert(value = existing)
        .execute(),
      modelName = modelCase.name
    )
    val candidate = modelCase.conflictingValue(
      existing = existing,
      sequence = 2
    )
    operation(modelCase.insert(value = candidate))

    assertThat(captureRows(modelCase.table))
      .containsExactly(existing)
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
    fun modelCases() = ModelCatalog.uniqueInsertCases
  }
}
