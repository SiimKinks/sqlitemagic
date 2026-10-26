package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.EXECUTE
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.OBSERVE
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.insertedPersistResult
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class NullOmittingPersistTest(
  private val modelCase: NullOmittingPersistModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeSingleInsertOmitsNullValues() = captureSingleInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeSingleInsertOmitsNullValues() = captureSingleInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeSingleUpdateOmitsNullValues() = captureSingleUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeSingleUpdateOmitsNullValues() = captureSingleUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkInsertOmitsNullValues() = captureBulkInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkInsertOmitsNullValues() = captureBulkInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkUpdateOmitsNullValues() = captureBulkUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkUpdateOmitsNullValues() = captureBulkUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> captureSingleInsert(
    modelCase: NullOmittingPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.partialNullValue(sequence = 1)
    val result = executeSinglePersist(
      modelCase = modelCase,
      value = value,
      terminal = terminal
    )
    val inserted = insertedPersistResult(
      result = result,
      modelName = modelCase.name
    )
    val insertResult = EntityInsertResult.Inserted(rowId = inserted.rowId)
    modelCase.verifyAfterInsert(
      value = value,
      result = insertResult
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = listOf(
          modelCase.expectedAfterInsert(
            value = value,
            result = insertResult
          )
        )
      )
    )
  }

  private fun <T> captureSingleUpdate(
    modelCase: NullOmittingPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = seedRows(
      modelCase = modelCase,
      count = 1
    ).single()
    val value = modelCase.partialNullUpdatedValue(
      value = existing,
      sequence = 2
    )
    val result = executeSinglePersist(
      modelCase = modelCase,
      value = value,
      terminal = terminal
    )
    assertThat(result).isEqualTo(EntityPersistResult.Updated)
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = listOf(
          modelCase.expectedAfterNullOmittingUpdate(
            existing = existing,
            value = value
          )
        )
      )
    )
  }

  private fun <T> captureBulkInsert(
    modelCase: NullOmittingPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(size = 3) { modelCase.partialNullValue(sequence = it + 1) }
    executeBulkPersist(
      modelCase = modelCase,
      values = values,
      terminal = terminal
    )
    val actual = captureDatabaseSnapshot(modelCase = modelCase).parents
    val expected = modelCase.expectedAfterBulkInsert(
      values = values,
      actual = actual
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = expected
      )
    )
  }

  private fun <T> captureBulkUpdate(
    modelCase: NullOmittingPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = seedRows(
      modelCase = modelCase,
      count = 3
    )
    val values = existing.mapIndexed { index, value ->
      modelCase.partialNullUpdatedValue(
        value = value,
        sequence = index + 4
      )
    }
    executeBulkPersist(
      modelCase = modelCase,
      values = values,
      terminal = terminal
    )
    val expected = existing.zip(
      other = values,
      transform = modelCase::expectedAfterNullOmittingUpdate
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = expected
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistCases
  }
}
