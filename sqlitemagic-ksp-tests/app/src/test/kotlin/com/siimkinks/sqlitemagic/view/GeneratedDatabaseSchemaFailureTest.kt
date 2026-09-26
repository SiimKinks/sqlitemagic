package com.siimkinks.sqlitemagic.view

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.BuildConfig
import com.siimkinks.sqlitemagic.PersistentEmailReadbackViewTable.Companion.PERSISTENT_EMAIL_READBACK
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

internal class GeneratedDatabaseSchemaFailureTest {
  @TestFactory
  fun `schema creation propagates local and delegated failures`() = listOf(
    FailureCase(
      label = "delegated submodule table",
      sqlPrefix = "CREATE TABLE IF NOT EXISTS submodule_persistent_value (",
      successfulTransactions = 0
    ),
    FailureCase(
      label = "local table",
      sqlPrefix = "CREATE TABLE IF NOT EXISTS no_id_entity (",
      successfulTransactions = 1
    ),
    FailureCase(
      label = "local persistent view",
      sqlPrefix = "CREATE VIEW IF NOT EXISTS \"persistent_email_readback\" AS ",
      successfulTransactions = 1
    ),
    FailureCase(
      label = "local index",
      sqlPrefix = "CREATE UNIQUE INDEX IF NOT EXISTS main.\"no_id_value_unique_index\" ON \"no_id_entity\"",
      successfulTransactions = 1
    )
  ).map { case ->
    DynamicTest.dynamicTest(case.label) {
      assertSchemaFailure(case)
    }
  }

  @Test
  fun `manager and persistent view table load without a default connection`() {
    val manager = SqliteMagicDatabase()
    val view = PERSISTENT_EMAIL_READBACK

    assertThat(manager.getDbName()).isEqualTo(BuildConfig.DB_NAME)
    assertThat(manager.getSubmoduleNames()?.toList()).containsExactly("Submodule")
    assertThat(view.name).isEqualTo("persistent_email_readback")
    assertThat(view.nrOfColumns).isEqualTo(1)
  }

  private fun assertSchemaFailure(case: FailureCase) {
    val database = mock<SupportSQLiteDatabase>()
    val failure = IllegalStateException("Injected ${case.label} schema failure")
    var matchedSql: String? = null
    doAnswer { invocation ->
      val sql = invocation.getArgument<String>(0)
      if (sql.startsWith(case.sqlPrefix)) {
        matchedSql = sql
        throw failure
      }
      null
    }.`when`(database).execSQL(any<String>())

    val actualFailure = runCatching {
      SqliteMagicDatabase().createSchema(database)
    }.exceptionOrNull()

    assertWithMessage(case.label).that(matchedSql).startsWith(case.sqlPrefix)
    verify(database, times(2)).beginTransaction()
    verify(database, times(2)).endTransaction()
    verify(database, times(case.successfulTransactions)).setTransactionSuccessful()
    assertWithMessage(case.label).that(actualFailure).isSameInstanceAs(failure)
  }

  private data class FailureCase(
    val label: String,
    val sqlPrefix: String,
    val successfulTransactions: Int
  )
}
