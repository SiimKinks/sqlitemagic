package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.LogLevel.DEBUG
import org.junit.Test

internal class EntityOperationLoggingTest {
  @Test
  fun `single operations log their CRUD events`() {
    withLogger(loggingEnabled = true) { logger ->
      val insertConnection = newConnection()
      insertConnection.sqliteConnection.insertResults += 41L
      TestAdapter()
        .insert(TestEntity())
        .usingConnection(insertConnection.dbConnection)
        .execute()

      val updateConnection = newConnection()
      updateConnection.sqliteConnection.updateResults += 1
      TestAdapter()
        .update(TestEntity())
        .usingConnection(updateConnection.dbConnection)
        .execute()

      val persistConnection = newConnection()
      persistConnection.sqliteConnection.updateResults += 1
      TestAdapter()
        .persist(TestEntity())
        .usingConnection(persistConnection.dbConnection)
        .execute()

      val persistInsertConnection = newConnection()
      persistInsertConnection.sqliteConnection.updateResults += 0
      persistInsertConnection.sqliteConnection.insertResults += 7L
      TestAdapter()
        .persist(
          TestEntity(
            id = "id-2",
            key = "key-2",
            name = "name"
          )
        )
        .usingConnection(persistInsertConnection.dbConnection)
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
              level = DEBUG,
              message = it
            )
          }
        )
        .inOrder()
    }
  }
}
