package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingAllNullPersistModelCase
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
internal class AllNullOmittingPersistTest(
  private val modelCase: NullOmittingAllNullPersistModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeSingleInsertUsesDefaultValues() = captureSingleInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeSingleInsertUsesDefaultValues() = captureSingleInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeSingleUpdateUsesIdentitySelfAssignment() = captureSingleUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeSingleUpdateUsesIdentitySelfAssignment() = captureSingleUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkInsertUsesDefaultValues() = captureBulkInsert(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkInsertUsesDefaultValues() = captureBulkInsert(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  @Test
  fun executeBulkUpdateUsesIdentitySelfAssignment() = captureBulkUpdate(
    modelCase = modelCase,
    terminal = EXECUTE
  )

  @Test
  fun observeBulkUpdateUsesIdentitySelfAssignment() = captureBulkUpdate(
    modelCase = modelCase,
    terminal = OBSERVE
  )

  private fun <T> captureSingleInsert(
    modelCase: NullOmittingAllNullPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val value = modelCase.allNullValueForMissingRow()
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
    modelCase: NullOmittingAllNullPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = seedRows(
      modelCase = modelCase,
      count = 1
    ).single()
    val value = modelCase.allNullValueForExistingRow(value = existing)
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
        parents = listOf(existing)
      )
    )
  }

  private fun <T> captureBulkInsert(
    modelCase: NullOmittingAllNullPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val values = List(size = 3) { modelCase.allNullValueForMissingRow() }
    executeBulkPersist(
      modelCase = modelCase,
      values = values,
      terminal = terminal
    )
    val actual = captureDatabaseSnapshot(modelCase = modelCase).parents
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = modelCase.expectedAfterAllNullBulkInsert(actual = actual)
      )
    )
  }

  private fun <T> captureBulkUpdate(
    modelCase: NullOmittingAllNullPersistModelCase<T>,
    terminal: OperationTerminal
  ) {
    val existing = seedRows(
      modelCase = modelCase,
      count = 3
    )
    val values = existing.map(modelCase::allNullValueForExistingRow)
    executeBulkPersist(
      modelCase = modelCase,
      values = values,
      terminal = terminal
    )
    assertDatabaseSnapshotIgnoringOrder(
      modelCase = modelCase,
      expected = nullOmittingSnapshot(
        modelCase = modelCase,
        parents = existing
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingAllNullPersistCases
  }
}
