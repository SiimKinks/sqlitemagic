package com.siimkinks.sqlitemagic.runtime.contract.persist

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.support.DatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertDatabaseSnapshotIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureDatabaseSnapshot
import com.siimkinks.sqlitemagic.runtime.support.seedRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NullOmittingPersistTest(
  private val modelCase: NullOmittingPersistModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeSingleInsertOmitsNullValues() {
    captureSingleInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeSingleInsertOmitsNullValues() {
    captureSingleInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeSingleUpdateOmitsNullValues() {
    captureSingleUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeSingleUpdateOmitsNullValues() {
    captureSingleUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeBulkInsertOmitsNullValues() {
    captureBulkInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeBulkInsertOmitsNullValues() {
    captureBulkInsert(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

  @Test
  fun executeBulkUpdateOmitsNullValues() {
    captureBulkUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.EXECUTE
    )
  }

  @Test
  fun observeBulkUpdateOmitsNullValues() {
    captureBulkUpdate(
      modelCase = modelCase,
      terminal = OperationTerminal.OBSERVE
    )
  }

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
    val inserted = result as? EntityPersistResult.Inserted
      ?: throw AssertionError("Persist did not insert for ${modelCase.name}: $result")
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
    val values = List(
      size = 3,
      init = { index -> modelCase.partialNullValue(sequence = index + 1) }
    )
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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.nullOmittingPersistCases
  }
}

internal fun <T> executeSinglePersist(
  modelCase: NullOmittingPersistModelCase<T>,
  value: T,
  terminal: OperationTerminal
) = when (terminal) {
  OperationTerminal.EXECUTE -> modelCase.executePersistIgnoringNullValues(value = value)
  OperationTerminal.OBSERVE -> modelCase
    .observePersistIgnoringNullValues(value = value)
    .blockingGet()
}

internal fun <T> executeBulkPersist(
  modelCase: NullOmittingPersistModelCase<T>,
  values: List<T>,
  terminal: OperationTerminal
) = when (terminal) {
  OperationTerminal.EXECUTE -> assertThat(
    modelCase.executeBulkPersistIgnoringNullValues(values = values)
  ).isTrue()
  OperationTerminal.OBSERVE -> modelCase
    .observeBulkPersistIgnoringNullValues(values = values)
    .blockingAwait()
}

internal fun <T> nullOmittingSnapshot(
  modelCase: NullOmittingPersistModelCase<T>,
  parents: List<T>
) = DatabaseSnapshot(
  parents = parents,
  related = modelCase.expectedRelatedValues(values = parents)
)
