package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import io.reactivex.Completable
import org.junit.Assert.assertThrows
import org.junit.Test

internal class EntityOperationBuildersTest {
  @Test
  fun `observed terminal snapshots connection conflict and selected identity`() {
    val initialConnection = newConnection()
    initialConnection.recordingDatabase.insertResults += -1L
    val laterConnection = newConnection()
    laterConnection.recordingDatabase.insertResults += 7L
    val insertBuilder = TestAdapter()
      .insert(TestEntity())
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
      .update(TestEntity())
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
  fun `observed terminals resolve the default connection on subscription`() {
    val previousConnection = SqliteMagic.defaultConnection
    try {
      SqliteMagic.defaultConnection = null
      val insert = TestAdapter()
        .insert(TestEntity())
        .observe()
      val current = newConnection()
      current.recordingDatabase.insertResults += 7L
      SqliteMagic.defaultConnection = current.connection

      insert
        .test()
        .assertResult(EntityInsertResult.Inserted(rowId = 7L))
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }

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
  fun `deferred bulk by-column terminals capture each selected column`() {
    val entity = TestEntity()
    val cases = listOf<Pair<String, (DbConnection) -> (Unique<TestEntity>) -> Completable>>(
      "bulk update" to { connection ->
        val builder = TestAdapter()
          .bulkUpdateByColumn(listOf(entity))
          .usingConnection(connection)
        builder::observe
      },
      "bulk persist" to { connection ->
        val builder = TestAdapter()
          .bulkPersistByColumn(listOf(entity))
          .usingConnection(connection)
        builder::observe
      }
    )

    for ((label, terminals) in cases) {
      val connection = newConnection()
      connection.recordingDatabase.updateResults.addAll(listOf(1, 1))
      val observe = terminals(connection.connection)

      val first = observe(TestSchema.id)
      val second = observe(TestSchema.key)
      second
        .test()
        .assertResult()
      first
        .test()
        .assertResult()

      val statements = connection.recordingDatabase.compiledStatements.map { it.sql to it.bindings[2] }
      assertWithMessage(label)
        .that(statements)
        .containsExactly(
          "UPDATE books SET name=? WHERE key=?" to "key-1",
          "UPDATE books SET name=? WHERE id=?" to "id-1"
        )
        .inOrder()
    }
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
