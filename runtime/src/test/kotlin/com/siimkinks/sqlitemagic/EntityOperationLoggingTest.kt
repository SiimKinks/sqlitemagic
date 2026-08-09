package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class EntityOperationLoggingTest {
  @Test
  fun `single operations emit the APT CRUD logging events`() {
    val logger = OperationLoggingLogger()
    SqliteMagic.setLogger(logger)
    SqliteMagic.setLoggingEnabled(true)

    try {
      val insertConnection = newConnection()
      insertConnection.recordingDatabase.insertResults += 41L
      TestAdapter()
        .insert(TestEntity("id-1", "key-1", "name"))
        .usingConnection(insertConnection.connection)
        .execute()

      val updateConnection = newConnection()
      updateConnection.recordingDatabase.updateResults += 1
      TestAdapter()
        .update(TestEntity("id-1", "key-1", "name"))
        .usingConnection(updateConnection.connection)
        .execute()

      val persistConnection = newConnection()
      persistConnection.recordingDatabase.updateResults += 1
      TestAdapter()
        .persist(TestEntity("id-1", "key-1", "name"))
        .usingConnection(persistConnection.connection)
        .execute()

      val persistInsertConnection = newConnection()
      persistInsertConnection.recordingDatabase.updateResults += 0
      persistInsertConnection.recordingDatabase.insertResults += 7L
      TestAdapter()
        .persist(TestEntity("id-2", "key-2", "name"))
        .usingConnection(persistInsertConnection.connection)
        .execute()

      assertThat(logger.debugMessages.filterNot { it.startsWith("TRIGGER") })
        .containsExactly(
          "INSERT\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
          "INSERT id: 41",
          "UPDATE\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
          "UPDATE rows affected: 1",
          "PERSIST\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
          "PERSIST\n  table: books\n  object: TestEntity(id=id-2, key=key-2, name=name, generatedRowId=null)",
          "PERSIST update failed; trying insertion",
          "PERSIST insert id: 7"
        )
        .inOrder()
    } finally {
      SqliteMagic.setLoggingEnabled(false)
    }
  }
}
