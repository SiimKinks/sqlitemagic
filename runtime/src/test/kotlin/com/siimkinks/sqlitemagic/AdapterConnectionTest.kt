package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import org.junit.Test
import org.mockito.kotlin.mock

internal class AdapterConnectionTest {
  @Test
  fun `adapter connections resolve the configured connection or else the current default connection`() {
    val previousConnection = SqliteMagic.defaultConnection
    try {
      val configured = testDbConnection(mock<SupportSQLiteDatabase>())
      val defaults = List(2) { testDbConnection(mock<SupportSQLiteDatabase>()) }
      val queries = listOf(
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .usingConnection(configured),
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
      ).map { select ->
        select
          .compile()
          .execution()
      }
      val resolvers = mapOf(
        "query" to queries.map { query -> { ReactiveAdapterSupport.connection(query) } },
        "query execution" to queries.map { query -> { ReactiveAdapterSupport.executionConnection(query) } },
        "deferred operation" to listOf(
          TestAdapter()
            .insert(TestEntity())
            .usingConnection(configured),
          TestAdapter().insert(TestEntity())
        )
          .map(ReactiveAdapterSupport::deferred)
          .map { operation -> { ReactiveAdapterSupport.connection(operation) } },
        "compiled mutation" to listOf(
          (DELETE FROM PERSON).usingConnection(configured),
          DELETE FROM PERSON
        )
          .map(MutationQuery::compile)
          .map { mutation -> { ReactiveAdapterSupport.connection(mutation) } }
      )

      for ((label, resolve) in resolvers) {
        val resolved = defaults.map { default ->
          SqliteMagic.defaultConnection = default
          resolve.map { it() }
        }

        assertWithMessage(label)
          .that(resolved)
          .isEqualTo(defaults.map { default -> listOf(configured, default) })
      }
    } finally {
      SqliteMagic.defaultConnection = previousConnection
    }
  }
}
