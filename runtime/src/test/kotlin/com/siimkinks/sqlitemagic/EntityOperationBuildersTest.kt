package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import org.junit.Test

internal class EntityOperationBuildersTest {
  @Test
  fun `observed terminal snapshots connection conflict and selected identity`() {
    val initialConnection = newConnection()
    initialConnection.recordingDatabase.insertResults += -1L
    val laterConnection = newConnection()
    laterConnection.recordingDatabase.insertResults += 7L
    val insertBuilder = TestAdapter()
      .insert(TestEntity(id = "id-1", key = "key-1", name = "name"))
      .usingConnection(initialConnection.connection)
    val insert = insertBuilder.observe()

    insertBuilder
      .usingConnection(laterConnection.connection)
      .conflictAlgorithm(CONFLICT_IGNORE)

    insert
      .test()
      .assertFailure(OperationFailedException::class.java)
    assertThat(initialConnection.recordingDatabase.compiledStatements).hasSize(1)
    assertThat(laterConnection.recordingDatabase.compiledStatements).isEmpty()
    insertBuilder
      .observe()
      .test()
      .assertResult(EntityInsertResult.Inserted(rowId = 7L))

    val updateConnection = newConnection()
    updateConnection.recordingDatabase.updateResults.addAll(listOf(1, 1))
    val updateBuilder = TestAdapter()
      .update(TestEntity(id = "id-1", key = "key-1", name = "name"))
      .usingConnection(updateConnection.connection)
      .byColumn(TestSchema.id)
    val update = updateBuilder.observe()

    updateBuilder.byColumn(TestSchema.key)
    update
      .test()
      .assertResult()
    updateBuilder
      .observe()
      .test()
      .assertResult()

    assertThat(updateConnection.statementSql)
      .containsExactly(
        "UPDATE books SET name=? WHERE id=?",
        "UPDATE books SET name=? WHERE key=?"
      )
      .inOrder()
  }

  @Test
  fun `no-ID handler uses required by-column terminals`() {
    val recording = newConnection()
    recording.recordingDatabase.updateResults.addAll(listOf(1, 1, 1, 1, 1, 1))
    val handler = TestAdapter()
    val entity = TestEntity("id-1", "key-1", "name")

    assertThat(
      handler
        .updateByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
    assertThat(
      handler
        .persistByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      handler
        .deleteByColumn(entity)
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(1)
    assertThat(
      handler
        .bulkUpdateByColumn(listOf(entity))
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
    assertThat(
      handler
        .bulkPersistByColumn(listOf(entity))
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isTrue()
    assertThat(
      handler
        .bulkDeleteByColumn(listOf(entity))
        .usingConnection(recording.connection)
        .execute(TestSchema.key)
    ).isEqualTo(1)
  }

  @Test
  fun `deferred bulk update by-column terminals capture each selected column`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
    val entity = TestEntity(id = "id-1", key = "key-1", name = "name")
    val builder = TestAdapter()
      .bulkUpdateByColumn(listOf(entity))
      .usingConnection(connection.connection)

    val first = builder.observe(TestSchema.id)
    val second = builder.observe(TestSchema.key)
    second
      .test()
      .assertResult()
    first
      .test()
      .assertResult()

    val statements = connection.recordingDatabase.compiledStatements
    assertThat(statements.map(RecordingStatement::sql))
      .containsExactly(
        "UPDATE books SET name=? WHERE key=?",
        "UPDATE books SET name=? WHERE id=?"
      )
      .inOrder()
    assertThat(statements.map { it.bindings[2] })
      .containsExactly(
        "key-1",
        "id-1"
      )
      .inOrder()
  }

  @Test
  fun `deferred bulk persist by-column terminals capture each selected column`() {
    val connection = newConnection()
    connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
    val entity = TestEntity(id = "id-1", key = "key-1", name = "name")
    val builder = TestAdapter()
      .bulkPersistByColumn(listOf(entity))
      .usingConnection(connection.connection)

    val first = builder.observe(TestSchema.id)
    val second = builder.observe(TestSchema.key)
    second
      .test()
      .assertResult()
    first
      .test()
      .assertResult()

    val statements = connection.recordingDatabase.compiledStatements
    assertThat(statements.map(RecordingStatement::sql))
      .containsExactly(
        "UPDATE books SET name=? WHERE key=?",
        "UPDATE books SET name=? WHERE id=?"
      )
      .inOrder()
    assertThat(statements.map { it.bindings[2] })
      .containsExactly(
        "key-1",
        "id-1"
      )
      .inOrder()
  }
}
