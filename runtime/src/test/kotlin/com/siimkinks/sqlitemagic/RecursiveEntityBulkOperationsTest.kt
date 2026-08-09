package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.reactivex.observers.TestObserver
import org.junit.Test

internal class RecursiveEntityBulkOperationsTest {
  @Test
  fun `bulk insert publishes one aggregate trigger`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(141L, 142L, 151L, 152L))

    assertThat(
      scenario.parent
        .bulkInsert(twoParentBulkGraphs())
        .usingConnection(scenario.connection)
        .execute()
    ).isTrue()
    assertThat(scenario.triggers)
        .containsExactly(RECURSIVE_TABLES)
  }

  @Test
  fun `bulk insert rolls back the failed graph without publishing a trigger`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(181L, 182L, 191L, -1L))

    assertThat(
      scenario.parent
        .bulkInsert(twoParentBulkGraphs())
        .usingConnection(scenario.connection)
        .execute()
    ).isFalse()
    assertThat(scenario.database.rolledBackTransactions)
        .isEqualTo(1)
    assertThat(scenario.triggers)
        .isEmpty()
  }

  @Test
  fun `ignored bulk operations isolate each graph transaction`() {
    listOf(
      IgnoredBulkCase(
        label = "all inserts ignored",
        operation = RecursiveBulkOperation.INSERT,
        insertResults = listOf(161L, -1L, 171L, -1L),
        expectedResult = false,
        expectedCommitted = 0,
        expectedRolledBack = 2
      ),
      IgnoredBulkCase(
        label = "one insert ignored",
        operation = RecursiveBulkOperation.INSERT,
        insertResults = listOf(181L, -1L, 191L, 192L),
        expectedResult = true,
        expectedCommitted = 1,
        expectedRolledBack = 1
      ),
      IgnoredBulkCase(
        label = "all updates ignored",
        operation = RecursiveBulkOperation.UPDATE,
        updateResults = listOf(0, 0, 0, 0),
        expectedResult = false,
        expectedCommitted = 0,
        expectedRolledBack = 2
      ),
      IgnoredBulkCase(
        label = "one update ignored",
        operation = RecursiveBulkOperation.UPDATE,
        updateResults = listOf(1, 0, 1, 1),
        expectedResult = true,
        expectedCommitted = 1,
        expectedRolledBack = 1
      ),
      IgnoredBulkCase(
        label = "all persists ignored",
        operation = RecursiveBulkOperation.PERSIST,
        insertResults = listOf(-1L, -1L),
        updateResults = listOf(0, 0, 0, 0),
        expectedResult = false,
        expectedCommitted = 0,
        expectedRolledBack = 2
      ),
      IgnoredBulkCase(
        label = "one persist ignored",
        operation = RecursiveBulkOperation.PERSIST,
        insertResults = listOf(301L, -1L, 311L, 312L),
        updateResults = listOf(0, 0, 0, 0),
        expectedResult = true,
        expectedCommitted = 1,
        expectedRolledBack = 1
      )
    ).forEach { case ->
      val scenario = recursiveScenario()
      scenario.database.insertResults.addAll(case.insertResults)
      scenario.database.updateResults.addAll(case.updateResults)

      assertWithMessage(case.label)
        .that(
          executeIgnoredBulkOperation(
            operation = case.operation,
            adapter = scenario.parent,
            connection = scenario.recordingConnection
          )
        )
        .isEqualTo(case.expectedResult)
      assertWithMessage(case.label)
        .that(scenario.database.successfulTransactions)
        .isEqualTo(case.expectedCommitted)
      assertWithMessage(case.label)
        .that(scenario.database.rolledBackTransactions)
        .isEqualTo(case.expectedRolledBack)
      when (case.expectedCommitted) {
        0 -> assertWithMessage(case.label)
            .that(scenario.triggers)
            .isEmpty()
        else -> assertWithMessage(case.label)
          .that(scenario.triggers)
          .containsExactly(RECURSIVE_TABLES)
      }
    }
  }

  @Test
  fun `disposing bulk insert rolls back the active graph`() {
    val scenario = recursiveScenario()
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
    val scenario = recursiveScenario()
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

  private fun executeIgnoredBulkOperation(
    operation: RecursiveBulkOperation,
    adapter: RecursiveParentAdapter,
    connection: RecordingConnection
  ) = when (operation) {
    RecursiveBulkOperation.INSERT -> adapter
      .bulkInsert(twoParentBulkGraphs())
      .usingConnection(connection.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .execute()
    RecursiveBulkOperation.UPDATE -> adapter
      .bulkUpdate(twoParentBulkGraphs())
      .usingConnection(connection.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .execute()
    RecursiveBulkOperation.PERSIST -> adapter
      .bulkPersist(twoParentBulkGraphs())
      .usingConnection(connection.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .execute()
  }
}

private enum class RecursiveBulkOperation {
  INSERT,
  UPDATE,
  PERSIST
}

private data class IgnoredBulkCase(
  val label: String,
  val operation: RecursiveBulkOperation,
  val insertResults: List<Long> = emptyList(),
  val updateResults: List<Int> = emptyList(),
  val expectedResult: Boolean,
  val expectedCommitted: Int,
  val expectedRolledBack: Int
)
