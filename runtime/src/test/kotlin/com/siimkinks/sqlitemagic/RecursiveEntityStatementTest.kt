package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Test

internal class RecursiveEntityStatementTest {
  @Test
  fun `insert uses each adapter statement and binding in dependency order`() {
    val scenario = recursiveScenario()
    scenario.database.insertResults.addAll(listOf(11L, 12L, 13L))

    assertThat(
      scenario.parent
        .insert(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isEqualTo(EntityInsertResult.Inserted(13L))
    assertThat(scenario.statementRecords)
      .containsExactlyElementsIn(
        listOf(
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
      )
      .inOrder()
  }

  @Test
  fun `update uses each adapter statement and binding in traversal order`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 1, 1))

    assertThat(
      scenario.parent
        .update(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isTrue()
    assertThat(scenario.statementRecords)
      .containsExactlyElementsIn(
        listOf(
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
      )
      .inOrder()
  }

  @Test
  fun `persist update path uses each adapter statement and binding in dependency order`() {
    val scenario = recursiveScenario()
    scenario.database.updateResults.addAll(listOf(1, 1, 1))

    assertThat(
      scenario.parent
        .persist(recursiveGraph())
        .usingConnection(scenario.connection)
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(scenario.statementRecords)
      .containsExactlyElementsIn(
        listOf(
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
      .inOrder()
  }
}

private data class StatementRecord(
  val sql: String,
  val bindings: Map<Int, Any?>
)

private val RecursiveScenario.statementRecords
  get() = database.compiledStatements.map(RecordingStatement::toStatementRecord)

private fun RecordingStatement.toStatementRecord() = StatementRecord(
  sql = sql,
  bindings = bindings.toMap()
)
