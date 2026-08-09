package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class RecursiveEntityTransactionTest {
  @Test
  fun `ignored child insert rolls back the complete graph`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(121L, -1L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityInsertResult.Ignored)
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `failed child insert rolls back the complete graph`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(131L, -1L))

    assertSingleOperationFailure {
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    }
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `ignored parent insert rolls back preceding relationship writes`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(241L, 242L, -1L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityInsertResult.Ignored)
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `ignored child update rolls back the complete graph`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 0))

    assertThat(
      scenario.parent
        .update(parentWithChild())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isFalse()
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `ignored parent persist rolls back preceding relationship writes`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(0, 0))
    scenario.database.insertResults.addAll(listOf(201L, -1L))

    assertThat(
      scenario.parent
        .persist(parentWithChild())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityPersistResult.Ignored)
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `ignored parent persist with null omission rolls back preceding relationship writes`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(0, 0))
    scenario.database.insertResults.addAll(listOf(321L, -1L))

    assertThat(
      scenario.parent
        .persist(parentWithChild())
        .usingConnection(scenario.connection)
        .ignoreNullValues()
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityPersistResult.Ignored)
    assertThat(scenario.database.successfulTransactions).isEqualTo(0)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }
}
