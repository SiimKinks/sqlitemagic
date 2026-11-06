package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

internal class CascadeTableTriggersTest {
  private data class Case(
    val label: String,
    val write: (DbConnectionImpl) -> Unit,
    val expectedTables: Set<String>
  )

  @Test
  fun `writes that delete rows notify the tables their deletes cascade into`() {
    val books = setOf("books")
    val booksWithCascade = setOf("books", "chapters", "notes")
    val recursiveTables = setOf(RECURSIVE_PARENT_TABLE, RECURSIVE_CHILD_TABLE, RECURSIVE_GRANDCHILD_TABLE)
    listOf(
      Case(
        label = "entity delete",
        write = { connection ->
          TestAdapter()
            .delete(TestEntity())
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity bulk delete by column",
        write = { connection ->
          TestAdapter()
            .bulkDeleteByColumn(listOf(TestEntity()))
            .usingConnection(connection)
            .execute(TestSchema.key)
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity delete table",
        write = { connection ->
          TestAdapter()
            .deleteTable()
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity insert or replace",
        write = { connection ->
          TestAdapter()
            .insert(TestEntity())
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity update or replace",
        write = { connection ->
          TestAdapter()
            .update(TestEntity())
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity persist or replace",
        write = { connection ->
          TestAdapter()
            .persist(TestEntity())
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity bulk insert or replace",
        write = { connection ->
          TestAdapter()
            .bulkInsert(listOf(TestEntity()))
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "entity insert",
        write = { connection ->
          TestAdapter()
            .insert(TestEntity())
            .usingConnection(connection)
            .execute()
        },
        expectedTables = books
      ),
      Case(
        label = "entity update or ignore",
        write = { connection ->
          TestAdapter()
            .update(TestEntity())
            .conflictAlgorithm(CONFLICT_IGNORE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = books
      ),
      Case(
        label = "DELETE FROM",
        write = { connection ->
          DELETE
            .FROM(TestSchema.table)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "UPDATE OR REPLACE",
        write = { connection ->
          UPDATE
            .OR(CONFLICT_REPLACE)
            .TABLE(TestSchema.table)
            .SET(TestSchema.key, "key-2")
            .usingConnection(connection)
            .execute()
        },
        expectedTables = booksWithCascade
      ),
      Case(
        label = "UPDATE",
        write = { connection ->
          UPDATE
            .TABLE(TestSchema.table)
            .SET(TestSchema.key, "key-2")
            .usingConnection(connection)
            .execute()
        },
        expectedTables = books
      ),
      Case(
        label = "recursive delete",
        write = { connection ->
          RecursiveScenario()
            .parent
            .delete(recursiveGraph())
            .usingConnection(connection)
            .execute()
        },
        expectedTables = setOf(RECURSIVE_PARENT_TABLE, "parent_tags")
      ),
      Case(
        label = "recursive insert or replace",
        write = { connection ->
          RecursiveScenario()
            .parent
            .insert(recursiveGraph())
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(connection)
            .execute()
        },
        expectedTables = recursiveTables + setOf("parent_tags", "grandchild_tags")
      ),
      Case(
        label = "recursive insert or replace in a transaction",
        write = { connection ->
          val transaction = connection.newTransaction()
          try {
            RecursiveScenario()
              .parent
              .insert(recursiveGraph())
              .conflictAlgorithm(CONFLICT_REPLACE)
              .usingConnection(connection)
              .execute()
            transaction.markSuccessful()
          } finally {
            transaction.end()
          }
        },
        expectedTables = recursiveTables + setOf("parent_tags", "grandchild_tags")
      ),
      Case(
        label = "recursive insert",
        write = { connection ->
          RecursiveScenario()
            .parent
            .insert(recursiveGraph())
            .usingConnection(connection)
            .execute()
        },
        expectedTables = recursiveTables
      )
    ).forEach { case ->
      val recording = RecordingDbConnection(
        sqliteConnection = RecordingSQLiteConnection(),
        tableCount = 3,
        cascadeTables = mapOf(
          "books" to arrayOf("books", "chapters", "notes"),
          RECURSIVE_PARENT_TABLE to arrayOf(RECURSIVE_PARENT_TABLE, "parent_tags"),
          RECURSIVE_GRANDCHILD_TABLE to arrayOf(RECURSIVE_GRANDCHILD_TABLE, "grandchild_tags")
        )
      )

      case.write(recording.dbConnection)

      assertWithMessage(case.label)
        .that(recording.triggers)
        .containsExactly(case.expectedTables)
    }
  }
}
