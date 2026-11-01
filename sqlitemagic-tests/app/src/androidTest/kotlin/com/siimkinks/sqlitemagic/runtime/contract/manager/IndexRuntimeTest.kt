@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_TEMP_MASTER
import com.siimkinks.sqlitemagic.runtime.support.assertConstraintFailure
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openDatabaseFile
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.queryRows
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.reopenDefaultConnection
import com.siimkinks.sqlitemagic.runtime.support.seedDatabaseFile
import com.siimkinks.sqlitemagic.runtime.support.userVersion
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

private const val INDEX_MIGRATION_DATABASE_NAME = "index-migration-runtime-test.db"
private const val INDEX_FAILURE_DATABASE_NAME = "index-migration-failure-runtime-test.db"
private const val INDEX_MIGRATION_INITIAL_VERSION = 1_100_000
private const val INDEX_MIGRATION_VERSION = 1_100_001
private const val INDEX_FAILURE_VERSION = 1_100_002

internal class IndexRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun persistentCompositeIndexExposesPhysicalColumnOrder() {
    assertThat(
      queryRows(sql = "PRAGMA main.index_info('mutable_nullable_details_index')")
        .map { it[2] }
    )
      .containsExactly("label", "count")
      .inOrder()
  }

  @Test
  fun persistentUniqueIndexReportsMetadataAndRejectsDuplicates() {
    assertThat(
      indexIsUnique(
        tableName = "no_id_entity",
        indexName = "no_id_value_unique_index"
      )
    ).isTrue()

    val existing = NoIdEntity(value = "indexed-duplicate")
    insertedResult(
      result = existing
        .insert()
        .execute(),
      modelName = "NoIdEntity"
    )
    assertConstraintFailure {
      NoIdEntity(value = existing.value)
        .insert()
        .execute()
    }
  }

  @Test
  fun persistentIndexesRouteThroughSubmoduleManager() {
    assertThat(
      indexNames(
        master = SQLITE_MASTER,
        tableName = "submodule_persistent_value"
      )
    ).contains("submodule_persistent_value_index")
  }

  @Test
  fun temporaryIndexesRecreateAfterConnectionReopen() = listOf(
    "main_session_value" to "main_session_value_index",
    "submodule_session_value" to "submodule_session_value_index"
  ).forEach { (tableName, indexName) ->
    assertWithMessage(tableName)
      .that(
        indexNames(
          master = SQLITE_TEMP_MASTER,
          tableName = tableName
        )
      )
      .contains(indexName)

    reopenDefaultConnection()

    assertWithMessage(tableName)
      .that(
        indexNames(
          master = SQLITE_TEMP_MASTER,
          tableName = tableName
        )
      )
      .contains(indexName)
  }

  @Test
  fun indexMigrationAddsDropsChangesAndRebuildsWhilePreservingRows() = withNamedDatabase(
    databaseName = INDEX_MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabaseFile(
      application = application,
      databaseName = INDEX_MIGRATION_DATABASE_NAME,
      version = INDEX_MIGRATION_INITIAL_VERSION,
      statements = listOf(
        "CREATE TABLE index_add_drop_change (id INTEGER PRIMARY KEY, added_value TEXT, changed_value TEXT)",
        "INSERT INTO index_add_drop_change (id, added_value, changed_value) VALUES (1, 'added', 'changed')",
        "CREATE INDEX removed_index ON index_add_drop_change (added_value)",
        "CREATE INDEX changed_index ON index_add_drop_change (changed_value)",
        "CREATE TABLE index_rebuild (id INTEGER PRIMARY KEY, value TEXT, rebuilt_value TEXT)",
        "INSERT INTO index_rebuild (id, value, rebuilt_value) VALUES (1, 'preserved', '')",
        "CREATE INDEX unchanged_rebuild_index ON index_rebuild (value)"
      )
    )

    openNamedConnection(
      application = application,
      databaseName = INDEX_MIGRATION_DATABASE_NAME,
      database = IndexMigrationDatabase(dbVersion = INDEX_MIGRATION_VERSION)
    ).use { connection ->
      assertThat(
        indexNames(
          connection = connection,
          master = SQLITE_MASTER,
          tableName = "index_add_drop_change"
        )
      ).containsAtLeast("added_index", "changed_index")
      assertThat(
        indexNames(
          connection = connection,
          master = SQLITE_MASTER,
          tableName = "index_add_drop_change"
        )
      ).doesNotContain("removed_index")
      assertThat(
        indexIsUnique(
          connection = connection,
          tableName = "index_add_drop_change",
          indexName = "changed_index"
        )
      ).isTrue()
      assertThat(
        indexNames(
          connection = connection,
          master = SQLITE_MASTER,
          tableName = "index_rebuild"
        )
      ).containsExactly("unchanged_rebuild_index", "rebuilt_new_index")
      assertThat(
        queryRows(
          sql = "SELECT id, value, rebuilt_value FROM index_rebuild ORDER BY id",
          connection = connection
        )
      ).containsExactly(listOf("1", "preserved", ""))
    }
  }

  @Test
  fun failedUniqueIndexMigrationRollsBackEarlierIndexChanges() = withNamedDatabase(
    databaseName = INDEX_FAILURE_DATABASE_NAME
  ) { application ->
    seedDatabaseFile(
      application = application,
      databaseName = INDEX_FAILURE_DATABASE_NAME,
      version = INDEX_MIGRATION_VERSION,
      statements = listOf(
        "CREATE TABLE index_migration_failure (id INTEGER PRIMARY KEY, value TEXT)",
        "INSERT INTO index_migration_failure (id, value) VALUES (1, 'duplicate')",
        "INSERT INTO index_migration_failure (id, value) VALUES (2, 'duplicate')"
      )
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      openNamedConnection(
        application = application,
        databaseName = INDEX_FAILURE_DATABASE_NAME,
        database = IndexMigrationDatabase(dbVersion = INDEX_FAILURE_VERSION)
      ).use { connection ->
        SELECT
          .RAW("SELECT 1")
          .readAs(LONG)
          .usingConnection(connection)
          .execute()
      }
    }
    assertThat(exception)
      .hasMessageThat()
      .contains("1100002.sql")

    openDatabaseFile(
      application = application,
      databaseName = INDEX_FAILURE_DATABASE_NAME
    ).use { connection ->
      assertThat(connection.userVersion())
        .isEqualTo(INDEX_MIGRATION_VERSION)
      assertThat(
        connection.readStrings(
          "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
          "rollback_added_index"
        )
      ).isEmpty()
      assertThat(connection.readStrings("SELECT value FROM index_migration_failure ORDER BY id"))
        .containsExactly("duplicate", "duplicate")
        .inOrder()
    }
  }
}

private fun indexNames(
  master: String,
  tableName: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = SELECT
  .RAW(
    "SELECT name FROM $master WHERE type = 'index' AND tbl_name = ?",
    tableName
  )
  .readAs(STRING)
  .usingConnection(connection)
  .execute()
  .toSet()

private fun indexIsUnique(
  tableName: String,
  indexName: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = queryRows(
  sql = "PRAGMA index_list('$tableName')",
  connection = connection
).any { it[1] == indexName && it[2] == "1" }

private class IndexMigrationDatabase(
  override val dbVersion: Int
) : GeneratedDatabase by SqliteMagicDatabase()
