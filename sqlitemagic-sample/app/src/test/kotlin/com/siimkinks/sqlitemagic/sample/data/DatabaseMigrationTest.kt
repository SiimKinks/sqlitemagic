package com.siimkinks.sqlitemagic.sample.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.siimkinks.sqlitemagic.MigrationTestResult
import com.siimkinks.sqlitemagic.SqliteMagicMigrationDatabase
import com.siimkinks.sqlitemagic.SqliteMagicMigrationTestHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DatabaseMigrationTest {
  private val migrationHelper = SqliteMagicMigrationTestHelper(
    descriptor = SqliteMagicMigrationDatabase,
    driver = BundledSQLiteDriver()
  )

  @Test
  fun `all retained releases migrate to the current schema`() {
    migrationHelper.use(
      SqliteMagicMigrationTestHelper::validateAllMigrations
    )
  }

  @Test
  fun `migrations preserve data completion defaults summary counts and cascade`() {
    migrationHelper.use { helper ->
      helper
        .createDatabase(
          name = "todos",
          version = 1
        )
        .use { database ->
          database.execute(
            sql = "INSERT INTO todo_list(id,name) VALUES (?,?)",
            bindArgs = listOf(7L, "O'Brien's list")
          )
          database.execute(
            sql = "INSERT INTO todo_item(id,title,list) VALUES (?,?,?)",
            bindArgs = listOf(11L, "Buy 'quoted' milk", 7L)
          )
        }
      helper
        .runMigrationsAndValidate(
          name = "todos",
          fromVersion = 1
        )
        .use { database ->
          assertEquals(
            MigrationTestResult(
              columns = listOf("id", "title", "list", "completed", "name"),
              rows = listOf(listOf(11L, "Buy 'quoted' milk", 7L, 0L, "O'Brien's list"))
            ),
            database.query(
              "SELECT i.id,i.title,i.list,i.completed,l.name FROM todo_item i JOIN todo_list l ON l.id=i.list"
            )
          )
          assertEquals(
            MigrationTestResult(
              columns = listOf("items_count"),
              rows = listOf(listOf(1L))
            ),
            database.query("SELECT items_count FROM todo_list_summary")
          )
          database.execute(
            sql = "UPDATE todo_item SET completed=? WHERE id=?",
            bindArgs = listOf(1L, 11L)
          )
          assertEquals(
            MigrationTestResult(
              columns = listOf("items_count"),
              rows = listOf(listOf(0L))
            ),
            database.query("SELECT items_count FROM todo_list_summary")
          )
          database.execute(
            sql = "DELETE FROM todo_list WHERE id=?",
            bindArgs = listOf(7L)
          )
          assertEquals(
            MigrationTestResult(
              columns = listOf("id", "title", "list", "completed"),
              rows = emptyList()
            ),
            database.query("SELECT id,title,list,completed FROM todo_item")
          )
        }
    }
  }

  @Test
  fun `upgrade rebuilds the summary view that pre-rewrite releases created`() {
    migrationHelper.use { helper ->
      helper
        .createDatabase(
          name = "todos",
          version = 3
        )
        .use { database ->
          database.execute(
            sql = """
              CREATE VIEW todo_list_summary AS SELECT todo_list.*, (SELECT count(*) FROM todo_item
              WHERE todo_item.completed = 0 AND todo_item.list = todo_list.id) AS items_count
              FROM todo_list ORDER BY todo_list.name
            """.trimIndent()
          )
          database.execute(
            sql = "INSERT INTO todo_list(id,name) VALUES (?,?)",
            bindArgs = listOf(7L, "O'Brien's list")
          )
          database.execute(
            sql = "INSERT INTO todo_item(id,title,list) VALUES (?,?,?)",
            bindArgs = listOf(11L, "Buy 'quoted' milk", 7L)
          )
        }
      helper
        .runMigrationsAndValidate(
          name = "todos",
          fromVersion = 3
        )
        .use { database ->
          assertEquals(
            MigrationTestResult(
              columns = listOf("todo_list.id", "todo_list.name", "items_count"),
              rows = listOf(listOf(7L, "O'Brien's list", 1L))
            ),
            database.query("""SELECT "todo_list.id","todo_list.name",items_count FROM todo_list_summary""")
          )
        }
    }
  }
}
