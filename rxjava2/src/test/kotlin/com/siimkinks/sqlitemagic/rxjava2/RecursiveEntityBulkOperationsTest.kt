package com.siimkinks.sqlitemagic.rxjava2

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.RECURSIVE_TABLES
import com.siimkinks.sqlitemagic.RecursiveParent
import com.siimkinks.sqlitemagic.RecursiveScenario
import com.siimkinks.sqlitemagic.bulkInsert
import com.siimkinks.sqlitemagic.twoParentBulkGraphs
import io.reactivex.observers.TestObserver
import org.junit.Test

internal class RecursiveEntityBulkOperationsTest {
  @Test
  fun `disposing bulk insert rolls back the active graph`() {
    val scenario = RecursiveScenario()
    scenario.database.insertResults.addAll(listOf(201L, 202L, 211L, 212L))
    val observer = TestObserver<Void>()
    val entities = disposeAfterFirst(
      values = twoParentBulkGraphs(),
      observer = observer
    )

    scenario.parent
      .bulkInsert(entities)
      .usingConnection(scenario.connection)
      .observe()
      .subscribe(observer)

    observer.assertEmpty()
    assertThat(scenario.database.rolledBackTransactions)
      .isEqualTo(1)
    assertThat(scenario.triggers)
      .isEmpty()
  }

  @Test
  fun `disposing ignored bulk insert retains completed graphs and publishes their trigger`() {
    val scenario = RecursiveScenario()
    scenario.database.insertResults.addAll(listOf(211L, 212L, 221L, 222L))
    val observer = TestObserver<Void>()
    val entities = disposeAfterFirst(
      values = twoParentBulkGraphs(),
      observer = observer
    )

    scenario.parent
      .bulkInsert(entities)
      .usingConnection(scenario.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .subscribe(observer)

    observer.assertEmpty()
    assertThat(scenario.database.successfulTransactions)
      .isEqualTo(1)
    assertThat(scenario.database.rolledBackTransactions)
      .isEqualTo(0)
    assertThat(scenario.triggers)
      .containsExactly(RECURSIVE_TABLES)
  }

  private fun disposeAfterFirst(
    values: List<RecursiveParent>,
    observer: TestObserver<Void>
  ) = object : Iterable<RecursiveParent> {
    override fun iterator() = object : Iterator<RecursiveParent> {
      private var index = 0

      override fun hasNext(): Boolean {
        if (index == 1) {
          observer.dispose()
        }
        return index < values.size
      }

      override fun next() = values[index++]
    }
  }
}
