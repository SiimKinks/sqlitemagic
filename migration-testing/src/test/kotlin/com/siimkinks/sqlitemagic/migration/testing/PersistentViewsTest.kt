package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import org.junit.Assert.assertThrows
import org.junit.Test

internal class PersistentViewsTest {
  @Test
  fun `target views create in dependency order`() {
    val events = mutableListOf<String>()
    BundledSQLiteDriver()
      .open(":memory:")
      .use { connection ->
        val recording = object : SQLiteConnection by connection {
          override fun prepare(sql: String) = connection
            .prepare(sql)
            .also { events += sql }
        }
        recording.createPersistentViews(
          listOf(
            MigrationViewSchema(
              name = """source"view""",
              createSql = """CREATE VIEW "source""view" AS SELECT 1"""
            ),
            MigrationViewSchema(
              name = "dependent_view",
              createSql = """CREATE VIEW dependent_view AS SELECT * FROM "source""view""""
            ),
            MigrationViewSchema(
              name = "replaced_by_table",
              createSql = "CREATE VIEW replaced_by_table AS SELECT 2"
            )
          )
        )
      }
    assertThat(events)
      .containsExactly(
        """CREATE VIEW "source""view" AS SELECT 1""",
        """CREATE VIEW dependent_view AS SELECT * FROM "source""view"""",
        "CREATE VIEW replaced_by_table AS SELECT 2"
      )
      .inOrder()
  }

  @Test
  fun `target creation failures name the view and preserve the cause`() {
    val failure = IllegalArgumentException("SQLite failure")
    val exception = BundledSQLiteDriver()
      .open(":memory:")
      .use { connection ->
        val failing = object : SQLiteConnection by connection {
          override fun prepare(sql: String) = throw failure
        }
        assertThrows(IllegalStateException::class.java) {
          failing.createPersistentViews(
            listOf(
              MigrationViewSchema(
                name = "target_view",
                createSql = "CREATE VIEW target_view AS SELECT 1"
              )
            )
          )
        }
      }
    assertThat(exception.message).contains("creating target persistent view target_view")
    assertThat(exception.cause).isSameInstanceAs(failure)
  }
}
