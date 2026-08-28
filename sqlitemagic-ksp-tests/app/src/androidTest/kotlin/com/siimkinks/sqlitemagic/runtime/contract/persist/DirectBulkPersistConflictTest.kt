package com.siimkinks.sqlitemagic.runtime.contract.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DirectBulkPersistConflictTest(
  private val modelCase: PersistConflictModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeWithInsertFallbackConflictAndDefaultAlgorithmReturnsFalseAndRestoresSnapshot() {
    assertInsertConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithInsertFallbackConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRestoresSnapshot() {
    assertInsertConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithUpdatePathConflictAndDefaultAlgorithmReturnsFalseAndRestoresSnapshot() {
    assertUpdateConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithUpdatePathConflictAndDefaultAlgorithmEmitsOperationFailedExceptionAndRestoresSnapshot() {
    assertUpdateConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithMixedConflictsAndConflictIgnoreReturnsTrueAndCommitsSuccessfulOperations() {
    assertMixedConflictExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithMixedConflictsAndConflictIgnoreCompletesAndCommitsSuccessfulOperations() {
    assertMixedConflictObserve(modelCase = modelCase)
  }

  @Test
  fun executeWithAllConflictsAndConflictIgnoreReturnsFalseAndLeavesSeedUnchanged() {
    assertAllConflictsExecute(modelCase = modelCase)
  }

  @Test
  fun observeWithAllConflictsAndConflictIgnoreCompletesAndLeavesSeedUnchanged() {
    assertAllConflictsObserve(modelCase = modelCase)
  }

  private fun <T> assertInsertConflictExecute(modelCase: PersistConflictModelCase<T>) {
    val scenario = insertConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      ).execute()
    ).isFalse()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertInsertConflictObserve(modelCase: PersistConflictModelCase<T>) {
    val scenario = insertConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values
    )
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertUpdateConflictExecute(modelCase: PersistConflictModelCase<T>) {
    val scenario = updateConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values
      ).execute()
    ).isFalse()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertUpdateConflictObserve(modelCase: PersistConflictModelCase<T>) {
    val scenario = updateConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values
    )
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertMixedConflictExecute(modelCase: PersistConflictModelCase<T>) {
    val scenario = mixedConflictScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isTrue()
    assertRows(
      table = modelCase.table,
      expected = scenario.expected
    )
  }

  private fun <T> assertMixedConflictObserve(modelCase: PersistConflictModelCase<T>) {
    val scenario = mixedConflictScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertRows(
      table = modelCase.table,
      expected = scenario.expected
    )
  }

  private fun <T> assertAllConflictsExecute(modelCase: PersistConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    assertThat(
      bulkPersist(
        modelCase = modelCase,
        values = scenario.values,
        conflictAlgorithm = CONFLICT_IGNORE
      ).execute()
    ).isFalse()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> assertAllConflictsObserve(modelCase: PersistConflictModelCase<T>) {
    val scenario = allConflictsScenario(modelCase = modelCase)

    bulkPersist(
      modelCase = modelCase,
      values = scenario.values,
      conflictAlgorithm = CONFLICT_IGNORE
    )
      .observe()
      .test()
      .assertComplete()
    assertRows(
      table = modelCase.table,
      expected = scenario.before
    )
  }

  private fun <T> bulkPersist(
    modelCase: PersistConflictModelCase<T>,
    values: List<T>,
    conflictAlgorithm: Int? = null
  ) = modelCase
    .bulkPersist(values = values)
    .withConflictAlgorithm(conflictAlgorithm)

  private fun <T> insertConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 1
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        modelCase.newValue(sequence = 2),
        modelCase.valueWithInsertConflict(
          existing = before.single(),
          sequence = 3
        ),
        modelCase.newValue(sequence = 4)
      ),
      expected = before
    )
  }

  private fun <T> updateConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        modelCase.valueWithUpdateConflict(
          existing = before[0],
          conflicting = before[0],
          sequence = 3
        ),
        modelCase.valueWithUpdateConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 4
        ),
        modelCase.newValue(sequence = 5)
      ),
      expected = before
    )
  }

  private fun <T> mixedConflictScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    val successfulUpdate = modelCase.valueWithUpdateConflict(
      existing = before[0],
      conflicting = before[0],
      sequence = 3
    )
    val successfulInsert = modelCase.newValue(sequence = 6)
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        successfulUpdate,
        modelCase.valueWithInsertConflict(
          existing = before[1],
          sequence = 4
        ),
        modelCase.valueWithUpdateConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 5
        ),
        successfulInsert
      ),
      expected = listOf(
        successfulUpdate,
        before[1],
        successfulInsert
      )
    )
  }

  private fun <T> allConflictsScenario(
    modelCase: PersistConflictModelCase<T>
  ): DirectBulkPersistScenario<T> {
    val before = seedRows(
      modelCase = modelCase,
      count = 2
    )
    return DirectBulkPersistScenario(
      before = before,
      values = listOf(
        modelCase.valueWithInsertConflict(
          existing = before[0],
          sequence = 3
        ),
        modelCase.valueWithUpdateConflict(
          existing = before[1],
          conflicting = before[0],
          sequence = 4
        )
      ),
      expected = before
    )
  }

  private fun <T> seedRows(
    modelCase: PersistConflictModelCase<T>,
    count: Int
  ): List<T> {
    List(size = count, init = modelCase::newValue).forEach { value ->
      when (modelCase.insert(value = value).execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for ${modelCase.name}")
      }
    }
    return captureRows(table = modelCase.table)
  }

  private fun <T> assertRows(
    table: Table<T>,
    expected: List<T>
  ) = assertThat(captureRows(table = table))
    .containsExactlyElementsIn(expected)
    .inOrder()

  private fun <T> captureRows(table: Table<T>) = Select
    .from(table)
    .queryDeep()
    .execute()

  private data class DirectBulkPersistScenario<T>(
    val before: List<T>,
    val values: List<T>,
    val expected: List<T>
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.persistConflictCases
  }
}
