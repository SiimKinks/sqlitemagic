package com.siimkinks.sqlitemagic.view

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.BuildConfig
import com.siimkinks.sqlitemagic.PersistentEmailReadbackViewTable.Companion.PERSISTENT_EMAIL_READBACK_VIEW
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class GeneratedDatabaseSchemaFailureTest {
  @TestFactory
  fun `schema creation propagates local and delegated failures`() = listOf(
    FailureCase(
      label = "delegated submodule table",
      sqlPrefix = """CREATE TABLE IF NOT EXISTS "submodule_persistent_value" ("""
    ),
    FailureCase(
      label = "local table",
      sqlPrefix = """CREATE TABLE IF NOT EXISTS "no_id_entity" ("""
    ),
    FailureCase(
      label = "local persistent view",
      sqlPrefix = """CREATE VIEW IF NOT EXISTS "persistent_email_readback" AS """
    ),
    FailureCase(
      label = "local index",
      sqlPrefix = """CREATE UNIQUE INDEX IF NOT EXISTS main."no_id_value_unique_index" ON "no_id_entity""""
    )
  ).map { case ->
    dynamicTest(case.label) {
      assertSchemaFailure(case)
    }
  }

  @Test
  fun `manager and persistent view table load without a default connection`() {
    val manager = SqliteMagicDatabase()
    val view = PERSISTENT_EMAIL_READBACK_VIEW

    assertThat(manager.dbName).isEqualTo(BuildConfig.DB_NAME)
    assertThat(manager.submoduleNames?.toList()).containsExactly("Submodule")
    assertThat(view.name).isEqualTo("persistent_email_readback")
  }

  private fun assertSchemaFailure(case: FailureCase) {
    val failure = IllegalStateException("Injected ${case.label} schema failure")
    val executed = mutableListOf<String>()
    val connection = mock<SQLiteConnection>()
    whenever(connection.inTransaction())
      .thenAnswer { executed.lastOrNull { it in TRANSACTION_SQL } == "BEGIN IMMEDIATE" }
    whenever(connection.prepare(any()))
      .thenAnswer { invocation ->
        val sql = invocation.getArgument<String>(0)
        mock<SQLiteStatement>().also { statement ->
          whenever(statement.step())
            .thenAnswer {
              executed += sql
              if (sql.startsWith(case.sqlPrefix)) {
                throw failure
              }
              false
            }
        }
      }

    val actualFailure = runCatching {
      SqliteMagicDatabase().createSchema(connection)
    }.exceptionOrNull()

    assertWithMessage(case.label)
      .that(executed.filter { it in TRANSACTION_SQL || it.startsWith(case.sqlPrefix) })
      .containsExactly("BEGIN IMMEDIATE", executed.single { it.startsWith(case.sqlPrefix) }, "ROLLBACK")
      .inOrder()
    assertWithMessage(case.label)
      .that(actualFailure)
      .isSameInstanceAs(failure)
  }

  private data class FailureCase(
    val label: String,
    val sqlPrefix: String
  )
}

private val TRANSACTION_SQL = setOf("BEGIN IMMEDIATE", "COMMIT", "ROLLBACK")
