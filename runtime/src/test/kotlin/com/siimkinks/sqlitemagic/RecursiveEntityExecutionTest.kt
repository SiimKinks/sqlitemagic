package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.RecursiveOperationEvent.PARENT_UPDATE
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class RecursiveEntityExecutionTest {
  @Test
  fun `writes each adapter in operation order in one transaction`() {
    data class Case(
      val label: String,
      val insertResults: List<Long> = emptyList(),
      val updateResults: List<Int> = emptyList(),
      val operation: (RecursiveScenario) -> Any,
      val expectedResult: Any,
      val expectedStatements: List<StatementRecord>
    )

    listOf(
      Case(
        label = "insert in dependency order",
        insertResults = listOf(11L, 12L, 13L),
        operation = { scenario ->
          scenario.parent
            .insert(recursiveGraph())
            .usingConnection(scenario.connection)
            .execute()
        },
        expectedResult = EntityInsertResult.Inserted(13L),
        expectedStatements = listOf(
          StatementRecord(
            sql = "INSERT INTO recursive_grandchild (id) VALUES (?)",
            bindings = mapOf(1 to "grandchild")
          ),
          StatementRecord(
            sql = "INSERT INTO recursive_child (id, grandchild) VALUES (?, ?)",
            bindings = mapOf(1 to "child", 2 to "grandchild")
          ),
          StatementRecord(
            sql = "INSERT INTO recursive_parent (id, child) VALUES (?, ?)",
            bindings = mapOf(1 to "parent", 2 to "child")
          )
        )
      ),
      Case(
        label = "update in traversal order",
        updateResults = listOf(1, 1, 1),
        operation = { scenario ->
          scenario.parent
            .update(recursiveGraph())
            .usingConnection(scenario.connection)
            .execute()
        },
        expectedResult = true,
        expectedStatements = listOf(
          StatementRecord(
            sql = "UPDATE recursive_parent SET id=? WHERE id=?",
            bindings = mapOf(1 to "parent", 2 to "parent")
          ),
          StatementRecord(
            sql = "UPDATE recursive_child SET id=? WHERE id=?",
            bindings = mapOf(1 to "child", 2 to "child")
          ),
          StatementRecord(
            sql = "UPDATE recursive_grandchild SET id=? WHERE id=?",
            bindings = mapOf(1 to "grandchild", 2 to "grandchild")
          )
        )
      ),
      Case(
        label = "persist update path in dependency order",
        updateResults = listOf(1, 1, 1),
        operation = { scenario ->
          scenario.parent
            .persist(recursiveGraph())
            .usingConnection(scenario.connection)
            .execute()
        },
        expectedResult = EntityPersistResult.Updated,
        expectedStatements = listOf(
          StatementRecord(
            sql = "UPDATE recursive_grandchild SET id=? WHERE id=?",
            bindings = mapOf(1 to "grandchild", 2 to "grandchild")
          ),
          StatementRecord(
            sql = "UPDATE recursive_child SET id=? WHERE id=?",
            bindings = mapOf(1 to "child", 2 to "child")
          ),
          StatementRecord(
            sql = "UPDATE recursive_parent SET id=? WHERE id=?",
            bindings = mapOf(1 to "parent", 2 to "parent")
          )
        )
      )
    ).forEach { case ->
      val scenario = RecursiveScenario()
      scenario.database.insertResults.addAll(case.insertResults)
      scenario.database.updateResults.addAll(case.updateResults)

      assertWithMessage(case.label)
        .that(case.operation(scenario))
        .isEqualTo(case.expectedResult)
      assertWithMessage(case.label)
        .that(
          scenario.database.compiledStatements.map {
            StatementRecord(
              sql = it.sql,
              bindings = it.bindings.toMap()
            )
          }
        )
        .containsExactlyElementsIn(case.expectedStatements)
        .inOrder()
      assertWithMessage(case.label)
        .that(scenario.database.successfulTransactions)
        .isEqualTo(1)
      assertWithMessage(case.label)
        .that(scenario.triggers)
        .containsExactly(RECURSIVE_TABLES)
    }
  }

  @Test
  fun `failed parent update skips relationships`() {
    val scenario = RecursiveScenario()
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
  fun `relationship operations inherit the conflict algorithm`() {
    val scenario = RecursiveScenario()
    scenario.database.insertResults.addAll(listOf(31L, 32L, 33L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(33L))
    assertThat(scenario.recordingConnection.statementSql)
      .containsExactly(
        "INSERT OR IGNORE INTO recursive_grandchild (id) VALUES (?)",
        "INSERT OR IGNORE INTO recursive_child (id, grandchild) VALUES (?, ?)",
        "INSERT OR IGNORE INTO recursive_parent (id, child) VALUES (?, ?)"
      )
      .inOrder()
  }

  @Test
  fun `relationship persist inherits ignore null values`() {
    val scenario = RecursiveScenario()
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

private data class StatementRecord(
  val sql: String,
  val bindings: Map<Int, Any?>
)
