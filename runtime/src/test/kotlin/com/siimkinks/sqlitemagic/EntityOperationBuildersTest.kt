package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityOperationBuildersTest {
  @Test
  fun `by-column builders execute by the required column`() {
    val recording = newConnection()
    recording.recordingDatabase.updateResults.addAll(listOf(1, 1, 1, 1, 1))
    val adapter = TestAdapter()
    val entity = TestEntity()

    assertThat(
      adapter
        .updateByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
    assertThat(
      adapter
        .persistByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      adapter
        .deleteByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(1)
    assertThat(
      adapter
        .bulkUpdateByColumn(listOf(entity))
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
    assertThat(
      adapter
        .bulkPersistByColumn(listOf(entity))
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
  }

  @Test
  fun `byColumn identifies rows by the physical column of an aliased output`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults += 1

    TestAdapter()
      .update(TestEntity())
      .usingConnection(connection.connection)
      .byColumn(TestSchema.key.AS("book_key"))
      .execute()

    assertThat(connection.statementSql).containsExactly("UPDATE books SET name=? WHERE key=?")
  }

  @Test
  fun `byColumn rejects a unique column of another table`() {
    val authorKey = testStringKey(
      table = testTable<TestEntity>(name = "authors"),
      name = "key"
    )

    val exception = assertThrows(IllegalArgumentException::class.java) {
      TestAdapter()
        .update(TestEntity())
        .byColumn(authorKey)
    }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("Column does not identify an entity property")
  }
}
