package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class EntityOperationLoggingTest {
  @Test
  fun `single operations log their CRUD events`() {
    withLogger(loggingEnabled = true) { logger ->
      val insertConnection = newConnection()
      insertConnection.recordingDatabase.insertResults += 41L
      TestAdapter()
        .insert(TestEntity())
        .usingConnection(insertConnection.connection)
        .execute()

      val updateConnection = newConnection()
      updateConnection.recordingDatabase.updateResults += 1
      TestAdapter()
        .update(TestEntity())
        .usingConnection(updateConnection.connection)
        .execute()

      val persistConnection = newConnection()
      persistConnection.recordingDatabase.updateResults += 1
      TestAdapter()
        .persist(TestEntity())
        .usingConnection(persistConnection.connection)
        .execute()

      val persistInsertConnection = newConnection()
      persistInsertConnection.recordingDatabase.updateResults += 0
      persistInsertConnection.recordingDatabase.insertResults += 7L
      TestAdapter()
        .persist(
          TestEntity(
            id = "id-2",
            key = "key-2",
            name = "name"
          )
        )
        .usingConnection(persistInsertConnection.connection)
        .execute()

      assertThat(logger.events.filterNot { it.message.startsWith("TRIGGER") })
        .containsExactlyElementsIn(
          listOf(
            "INSERT\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
            "INSERT id: 41",
            "UPDATE\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
            "UPDATE rows affected: 1",
            "PERSIST\n  table: books\n  object: TestEntity(id=id-1, key=key-1, name=name, generatedRowId=null)",
            "PERSIST\n  table: books\n  object: TestEntity(id=id-2, key=key-2, name=name, generatedRowId=null)",
            "PERSIST update failed; trying insertion",
            "PERSIST insert id: 7"
          ).map {
            LoggedMessage(
              level = LogLevel.DEBUG,
              message = it
            )
          }
        )
        .inOrder()
    }
  }
}
