package com.siimkinks.sqlitemagic.migration.testing

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationDatabase
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import org.junit.Assert.assertThrows
import org.junit.Test

class PersistentViewsTest {
  @Test
  fun `target views create in dependency order`() {
    val events = mutableListOf<String>()
    val database = object : MigrationDatabase {
      override fun execute(sql: String) {
        events += sql
      }

      override fun persistentViewNames() = emptyList<String>()
    }
    database.createPersistentViews(
      listOf(
        MigrationViewSchema(
          name = "source\"view",
          createSql = "CREATE VIEW \"source\"\"view\" AS SELECT 1"
        ),
        MigrationViewSchema(
          name = "dependent_view",
          createSql = "CREATE VIEW dependent_view AS SELECT * FROM \"source\"\"view\""
        ),
        MigrationViewSchema(
          name = "replaced_by_table",
          createSql = "CREATE VIEW replaced_by_table AS SELECT 2"
        )
      )
    )
    assertThat(events)
      .containsExactly(
        "CREATE VIEW \"source\"\"view\" AS SELECT 1",
        "CREATE VIEW dependent_view AS SELECT * FROM \"source\"\"view\"",
        "CREATE VIEW replaced_by_table AS SELECT 2"
      )
      .inOrder()
  }

  @Test
  fun `target creation failures name the view and preserve the cause`() {
    val failure = IllegalArgumentException("SQLite failure")
    val database = object : MigrationDatabase {
      override fun execute(sql: String): Unit = throw failure
      override fun persistentViewNames() = emptyList<String>()
    }
    val exception = assertThrows(IllegalStateException::class.java) {
      database.createPersistentViews(
        listOf(
          MigrationViewSchema(
            name = "target_view",
            createSql = "CREATE VIEW target_view AS SELECT 1"
          )
        )
      )
    }
    assertThat(exception.message).contains("creating target persistent view target_view")
    assertThat(exception.cause).isSameInstanceAs(failure)
  }
}
