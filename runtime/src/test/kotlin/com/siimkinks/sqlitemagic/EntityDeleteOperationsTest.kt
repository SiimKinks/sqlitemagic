package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class EntityDeleteOperationsTest {
  @Test
  fun `single and bulk delete select identity, count rows, and deliver triggers`() {
    val recording = newConnection()
    recording.recordingDatabase.updateResults.addAll(listOf(1, 2, 0))
    val handler = TestAdapter()

    assertThat(
      handler
        .delete(TestEntity("id-1", "key-1", "name"))
        .usingConnection(recording.connection)
        .execute()
    ).isEqualTo(1)
    assertThat(
      handler
        .bulkDeleteByColumn(
          listOf(TestEntity("id-1", "key-1", "name"), TestEntity("id-2", "key-2", "name"))
        )
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(2)
    assertThat(
      handler
        .bulkDelete(emptyList())
        .usingConnection(recording.connection)
        .execute()
    ).isEqualTo(0)
    assertThat(recording.statementSql).contains("DELETE FROM books WHERE id IN (?)")
    assertThat(recording.statementSql).contains("DELETE FROM books WHERE key IN (?,?)")
    assertThat(recording.triggers).hasSize(2)
    assertThat(recording.triggers).containsExactlyElementsIn(listOf(setOf("books"), setOf("books")))
  }

  @Test
  fun `delete table reports deleted rows and only publishes a trigger when rows changed`() {
    data class DeleteTableCase(
      val label: String,
      val deletedRows: Int,
      val expectedTriggers: List<Set<String>>
    )

    listOf(
      DeleteTableCase(
        label = "rows deleted",
        deletedRows = 3,
        expectedTriggers = listOf(setOf("books"))
      ),
      DeleteTableCase(
        label = "table already empty",
        deletedRows = 0,
        expectedTriggers = emptyList()
      )
    ).forEach { case ->
      val recording = newConnection()
      recording.recordingDatabase.updateResults += case.deletedRows

      val deletedRows = TestAdapter()
        .deleteTable()
        .usingConnection(recording.connection)
        .execute()

      assertWithMessage(case.label)
        .that(deletedRows)
        .isEqualTo(case.deletedRows)
      assertWithMessage(case.label)
        .that(recording.statementSql)
        .containsExactly("DELETE FROM books WHERE 1")
      assertWithMessage(case.label)
        .that(recording.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }

  @Test
  fun `observed delete table is cold and emits the deleted row count`() {
    val recording = newConnection()
    recording.recordingDatabase.updateResults += 4
    val terminal = TestAdapter()
      .deleteTable()
      .usingConnection(recording.connection)
      .observe()

    assertThat(recording.recordingDatabase.compiledStatements).isEmpty()

    terminal
      .test()
      .assertResult(4)
    assertThat(recording.statementSql).containsExactly("DELETE FROM books WHERE 1")
    assertThat(recording.triggers).containsExactly(setOf("books"))
  }
}
