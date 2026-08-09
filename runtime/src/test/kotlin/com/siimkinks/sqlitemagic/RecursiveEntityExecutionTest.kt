package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.CHILD_INSERT
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.CHILD_INSERT_RELATIONSHIPS
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.CHILD_PERSIST_RELATIONSHIPS
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.CHILD_UPDATE
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.CHILD_UPDATE_RELATIONSHIPS
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.GRANDCHILD_INSERT
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.GRANDCHILD_UPDATE
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_INSERT
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_INSERT_RELATIONSHIPS
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_PERSIST_RELATIONSHIPS
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_UPDATE
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_UPDATE_RELATIONSHIPS
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class RecursiveEntityExecutionTest {
  @Test
  fun `insert composes adapters in dependency order`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(11L, 12L, 13L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(13L))
    assertThat(scenario.state.events)
      .containsExactly(
        PARENT_INSERT_RELATIONSHIPS,
        CHILD_INSERT_RELATIONSHIPS,
        GRANDCHILD_INSERT,
        CHILD_INSERT,
        PARENT_INSERT
      )
      .inOrder()
    assertThat(scenario.database.successfulTransactions).isEqualTo(1)
    assertThat(scenario.triggers).containsExactly(RECURSIVE_TABLES)
  }

  @Test
  fun `update composes adapters in traversal order`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 1, 1))

    assertThat(
      scenario.parent
        .update(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isTrue()
    assertThat(scenario.state.events)
      .containsExactly(
        PARENT_UPDATE,
        PARENT_UPDATE_RELATIONSHIPS,
        CHILD_UPDATE,
        CHILD_UPDATE_RELATIONSHIPS,
        GRANDCHILD_UPDATE
      )
      .inOrder()
    assertThat(scenario.database.successfulTransactions).isEqualTo(1)
    assertThat(scenario.triggers).containsExactly(RECURSIVE_TABLES)
  }

  @Test
  fun `persist composes adapters in dependency order`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 1, 1))

    assertThat(
      scenario.parent
        .persist(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(scenario.state.events)
      .containsExactly(
        PARENT_PERSIST_RELATIONSHIPS,
        CHILD_PERSIST_RELATIONSHIPS,
        GRANDCHILD_UPDATE,
        CHILD_UPDATE,
        PARENT_UPDATE
      )
      .inOrder()
    assertThat(scenario.database.successfulTransactions).isEqualTo(1)
    assertThat(scenario.triggers).containsExactly(RECURSIVE_TABLES)
  }

  @Test
  fun `failed parent update skips relationships`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults += 0

    assertThat(
      scenario.parent
        .update(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isFalse()
    assertThat(scenario.state.events).containsExactly(PARENT_UPDATE)
    assertThat(scenario.database.rolledBackTransactions).isEqualTo(1)
    assertThat(scenario.triggers).isEmpty()
  }

  @Test
  fun `insert omits a null child relationship`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults += 23L

    assertThat(
      scenario.parent
        .insert(RecursiveParent(id = "parent", child = null))
        .usingConnection(scenario.connection)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(23L))
    assertThat(scenario.state.events)
      .containsExactly(
        PARENT_INSERT_RELATIONSHIPS,
        PARENT_INSERT
      )
      .inOrder()
  }

  @Test
  fun `relationship operations inherit the conflict algorithm`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(31L, 32L, 33L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(33L))
    val statements = scenario.database.compiledStatements.map(RecordingStatement::sql)

    assertThat(statements)
      .containsExactly(
        "INSERT OR IGNORE INTO recursive_grandchild (id) VALUES (?)",
        "INSERT OR IGNORE INTO recursive_child (id, grandchild) VALUES (?, ?)",
        "INSERT OR IGNORE INTO recursive_parent (id, child) VALUES (?, ?)"
      )
      .inOrder()
  }

  @Test
  fun `relationship persist inherits ignore null values`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 1, 1))

    assertThat(
      scenario.parent
        .persist(recursiveGraph())
        .usingConnection(scenario.connection)
        .ignoreNullValues()
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(scenario.state.childIgnoreNullValues).containsExactly(true)
  }
}
