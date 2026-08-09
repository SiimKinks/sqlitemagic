package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Test

internal class RecursiveEntityReactiveOperationsTest {
  @Test
  fun `observed insert is cold and publishes success after subscription`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(221L, 222L))
    val terminal = scenario.parent
      .insert(parentWithChild())
      .usingConnection(scenario.connection)
      .observe()

    assertThat(scenario.database.compiledStatements).isEmpty()

    terminal
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 222L))
    assertThat(scenario.triggers).hasSize(1)
  }

  @Test
  fun `observed failed insert emits operation failed and rolls back`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(231L, -1L))

    scenario.parent
      .insert(parentWithChild())
      .usingConnection(scenario.connection)
      .observe()
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `observed ignored parent insert publishes ignored and rolls back the complete graph`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(251L, 252L, -1L))
    scenario.parent
      .insert(recursiveGraph())
      .usingConnection(scenario.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)
      .observe()
      .test()
      .assertResult(EntityInsertResult.Ignored)
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }
}
