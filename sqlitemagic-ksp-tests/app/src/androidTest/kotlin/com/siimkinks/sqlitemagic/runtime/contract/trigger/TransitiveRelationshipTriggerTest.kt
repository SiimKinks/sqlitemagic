package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipModelCase
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipTableRows
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import io.reactivex.observers.TestObserver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TransitiveRelationshipTriggerTest(
  private val modelCase: TransitiveRelationshipModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun recursiveInsertRefreshesEveryDistinctTableOnce() {
    assertRecursiveInsert(
      modelCase = modelCase
    )
  }

  private fun <T> assertRecursiveInsert(
    modelCase: TransitiveRelationshipModelCase<T>
  ) {
    val value = modelCase.newValue(sequence = 1)
    val observers = observeAllTables(
      modelCase = modelCase,
      value = value
    )

    try {
      val inserted = insertedResult(
        result = modelCase
          .insert(value = value)
          .execute(),
        modelName = modelCase.name
      )
      val expected = expectedRows(
        modelCase = modelCase,
        value = modelCase.expectedAfterInsert(
          value = value,
          result = inserted
        )
      )

      assertRefreshes(
        observers = observers,
        before = expected.map { it.copy(rows = emptyList<Any?>()) },
        after = expected
      )
      expected.forEach { expectedRows ->
        assertRowsIgnoringOrder(
          table = expectedRows.table,
          expected = expectedRows.rows
        )
      }
    } finally {
      observers.forEach(ObservedRows::dispose)
    }
  }

  @Test
  fun recursiveUpdateWithConflictIgnoreAndMissingImmutableLeafReturnsFalseWithoutRefresh() {
    assertMissingImmutableLeafUpdate(
      modelCase = modelCase
    )
  }

  @Test
  fun recursiveInsertInRolledBackTransactionDoesNotRefreshOrPersistRows() {
    assertRolledBackRecursiveInsert(
      modelCase = modelCase
    )
  }

  private fun <T> assertMissingImmutableLeafUpdate(
    modelCase: TransitiveRelationshipModelCase<T>
  ) {
    val seed = modelCase.newValue(sequence = 1)
    val inserted = insertedResult(
      result = modelCase
        .insert(value = seed)
        .execute(),
      modelName = modelCase.name
    )
    val persisted = modelCase.expectedAfterInsert(
      value = seed,
      result = inserted
    )
    val expected = expectedRows(
      modelCase = modelCase,
      value = persisted
    )
    val candidate = modelCase.withMissingRelationshipIdentity(
      value = modelCase.updatedValue(
        value = persisted,
        sequence = 2
      )
    )
    val observers = observeAllTables(
      modelCase = modelCase,
      value = persisted
    )

    try {
      assertThat(
        modelCase
          .update(value = candidate)
          .withConflictAlgorithm(CONFLICT_IGNORE)
          .execute()
      ).isFalse()
      assertNoRefresh(
        observers = observers,
        expected = expected
      )
    } finally {
      observers.forEach(ObservedRows::dispose)
    }
  }

  private fun <T> assertRolledBackRecursiveInsert(
    modelCase: TransitiveRelationshipModelCase<T>
  ) {
    val value = modelCase.newValue(sequence = 1)
    val expected = expectedRows(
      modelCase = modelCase,
      value = value
    ).map { it.copy(rows = emptyList<Any?>()) }
    val observers = observeAllTables(
      modelCase = modelCase,
      value = value
    )

    try {
      val transaction = SqliteMagic.newTransaction()
      try {
        insertedResult(
          result = modelCase
            .insert(value = value)
            .execute(),
          modelName = modelCase.name
        )
      } finally {
        transaction.end()
      }
      assertNoRefresh(
        observers = observers,
        expected = expected
      )
      expected.forEach { expectedRows ->
        assertRowsIgnoringOrder(
          table = expectedRows.table,
          expected = expectedRows.rows
        )
      }
    } finally {
      observers.forEach(ObservedRows::dispose)
    }
  }

  private fun <T> expectedRows(
    modelCase: TransitiveRelationshipModelCase<T>,
    value: T
  ) = listOf(
    TransitiveRelationshipTableRows(
      table = modelCase.table,
      rows = listOf(value)
    )
  ) + modelCase.transitiveRelatedTableRows(value)

  private fun <T> observeAllTables(
    modelCase: TransitiveRelationshipModelCase<T>,
    value: T
  ): List<ObservedRows> {
    val expectedRows = expectedRows(
      modelCase = modelCase,
      value = value
    )
    assertThat(expectedRows.map(TransitiveRelationshipTableRows::table).toSet())
      .hasSize(expectedRows.size)
    return expectedRows.map { expected ->
      ObservedRows(
        table = expected.table,
        observer = observeRows(table = expected.table)
      )
    }
  }

  private fun assertRefreshes(
    observers: List<ObservedRows>,
    before: List<TransitiveRelationshipTableRows>,
    after: List<TransitiveRelationshipTableRows>
  ) {
    observers.zip(before.zip(after)).forEach { (observed, expected) ->
      val (beforeRows, afterRows) = expected
      assertThat(observed.observer.values()).hasSize(2)
      assertThat(observed.observer.values()[0]).containsExactlyElementsIn(beforeRows.rows)
      assertThat(observed.observer.values()[1]).containsExactlyElementsIn(afterRows.rows)
      observed.observer.assertNoErrors()
    }
  }

  private fun assertNoRefresh(
    observers: List<ObservedRows>,
    expected: List<TransitiveRelationshipTableRows>
  ) {
    observers.zip(expected).forEach { (observed, expectedRows) ->
      assertThat(observed.observer.values()).hasSize(1)
      assertThat(observed.observer.values().single()).containsExactlyElementsIn(expectedRows.rows)
      observed.observer.assertNoErrors()
      assertRowsIgnoringOrder(
        table = observed.table,
        expected = expectedRows.rows
      )
    }
  }

  private data class ObservedRows(
    val table: Table<*>,
    val observer: TestObserver<out List<*>>
  ) {
    fun dispose() = observer.dispose()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.transitiveRelationshipCases
  }
}
