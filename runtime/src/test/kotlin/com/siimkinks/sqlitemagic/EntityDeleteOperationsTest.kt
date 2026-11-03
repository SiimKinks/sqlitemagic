package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class EntityDeleteOperationsTest {
  @Test
  fun `single and bulk delete select identity, count rows, and deliver triggers without a transaction`() {
    val recording = newConnection()
    recording.sqliteConnection.updateResults += listOf(1, 2, 0)
    val handler = TestAdapter()

    assertThat(
      recording.transactionResult(
        handler
          .delete(TestEntity())
          .usingConnection(recording.dbConnection)
          .execute()
      )
    ).isEqualTo(
      TransactionResult(
        result = 1,
        committed = 0,
        rolledBack = 0
      )
    )
    assertThat(
      handler
        .bulkDeleteByColumn(
          listOf(
            TestEntity(
              id = "id-1",
              key = "key-1",
              name = "name"
            ),
            TestEntity(
              id = "id-2",
              key = "key-2",
              name = "name"
            )
          )
        )
        .usingConnection(recording.dbConnection)
        .execute(TestSchema.key)
    ).isEqualTo(2)
    assertThat(
      recording.transactionResult(
        handler
          .bulkDelete(emptyList())
          .usingConnection(recording.dbConnection)
          .execute()
      )
    ).isEqualTo(
      TransactionResult(
        result = 0,
        committed = 0,
        rolledBack = 0
      )
    )
    assertThat(recording.statementSql)
      .containsExactly("""DELETE FROM "books" WHERE "id" IN (?)""", """DELETE FROM "books" WHERE "key" IN (?,?)""")
    assertThat(recording.triggers).containsExactly(setOf("books"), setOf("books"))
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
      recording.sqliteConnection.updateResults += case.deletedRows

      val deletedRows = TestAdapter()
        .deleteTable()
        .usingConnection(recording.dbConnection)
        .execute()

      assertWithMessage(case.label)
        .that(deletedRows)
        .isEqualTo(case.deletedRows)
      assertWithMessage(case.label)
        .that(recording.statementSql)
        .containsExactly("""DELETE FROM "books" WHERE 1""")
      assertWithMessage(case.label)
        .that(recording.triggers)
        .containsExactlyElementsIn(case.expectedTriggers)
    }
  }
}
