package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Test

internal class RecursiveEntityReactiveOperationsTest {
  @Test
  fun `observed insert is cold and publishes success after subscription`() {
    val scenario = RecursiveScenario()
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
    val scenario = RecursiveScenario()
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
}
