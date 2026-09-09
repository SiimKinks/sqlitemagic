package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import android.content.Context
import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.reopenDefaultConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

private const val SQLITE_MASTER = "sqlite_master"
private const val SQLITE_TEMP_MASTER = "sqlite_temp_master"
private const val INDEX_MIGRATION_DATABASE_NAME = "index-migration-runtime-test.db"
private const val INDEX_FAILURE_DATABASE_NAME = "index-migration-failure-runtime-test.db"
private const val INDEX_MIGRATION_INITIAL_VERSION = 1_100_000
private const val INDEX_MIGRATION_VERSION = 1_100_001
private const val INDEX_FAILURE_VERSION = 1_100_002

class IndexRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun persistentIndexesExposePhysicalOrderAndUniqueEnforcement() {
    assertThat(
      indexColumns(
        master = SQLITE_MASTER,
        indexName = "mutable_nullable_details_index"
      )
    ).containsExactly("label", "count").inOrder()
    assertThat(
      indexIsUnique(
        tableName = "no_id_entity",
        indexName = "no_id_value_unique_index"
      )
    ).isTrue()

    val existing = NoIdEntity(value = "indexed-duplicate")
    assertSeedInserted(
      result = existing
        .insert()
        .execute(),
      modelName = "NoIdEntity"
    )
    assertThrows(OperationFailedException::class.java) {
      NoIdEntity(value = existing.value)
        .insert()
        .execute()
    }
  }

  @Test
  fun persistentIndexesRouteThroughSubmoduleManager() {
    val indexNames = indexNames(
      master = SQLITE_MASTER,
      tableName = "submodule_persistent_value"
    )
    assertThat(indexNames)
      .contains("submodule_persistent_value_index")
  }

  @Test
  fun temporaryIndexesRecreateAfterConnectionReopen() {
    assertThat(
      indexNames(
        master = SQLITE_TEMP_MASTER,
        tableName = "main_session_value"
      )
    ).contains("main_session_value_index")
    assertThat(
      indexNames(
        master = SQLITE_TEMP_MASTER,
        tableName = "submodule_session_value"
      )
    ).contains("submodule_session_value_index")

    reopenDefaultConnection()

    assertThat(
      indexNames(
        master = SQLITE_TEMP_MASTER,
        tableName = "main_session_value"
      )
    ).contains("main_session_value_index")
    assertThat(
      indexNames(
        master = SQLITE_TEMP_MASTER,
        tableName = "submodule_session_value"
      )
    ).contains("submodule_session_value_index")
  }

  @Test
  fun indexMigrationAddsDropsChangesAndRebuildsWhilePreservingRows() = withNamedDatabase(
    databaseName = INDEX_MIGRATION_DATABASE_NAME
  ) { application ->
    seedIndexMigrationDatabase(
      application = application,
      databaseName = INDEX_MIGRATION_DATABASE_NAME,
      version = INDEX_MIGRATION_INITIAL_VERSION
    )

    openNamedConnection(
      application = application,
      databaseName = INDEX_MIGRATION_DATABASE_NAME,
      database = IndexMigrationDatabase(version = INDEX_MIGRATION_VERSION)
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
      assertThat(rebuiltRows(connection = connection)).containsExactly(
        listOf("1", "preserved", "")
      )
    }
  }

  @Test
  fun failedUniqueIndexMigrationRollsBackEarlierIndexChanges() = withNamedDatabase(
    databaseName = INDEX_FAILURE_DATABASE_NAME
  ) { application ->
    seedIndexFailureDatabase(
      application = application,
      databaseName = INDEX_FAILURE_DATABASE_NAME,
      version = INDEX_MIGRATION_VERSION
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      openNamedConnection(
        application = application,
        databaseName = INDEX_FAILURE_DATABASE_NAME,
        database = IndexMigrationDatabase(version = INDEX_FAILURE_VERSION)
      ).use { connection ->
        Select
          .raw("SELECT 1")
          .from(ANONYMOUS_TABLE)
          .usingConnection(connection)
          .execute()
          .use { }
      }
    }
    assertThat(exception)
      .hasMessageThat()
      .contains("1100002.sql")

    application
      .openOrCreateDatabase(
        INDEX_FAILURE_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        assertThat(database.version)
          .isEqualTo(INDEX_MIGRATION_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
              arrayOf("rollback_added_index")
            )
            .use(Cursor::readStrings)
        ).isEmpty()
        assertThat(
          database
            .rawQuery(
              "SELECT value FROM index_migration_failure ORDER BY id",
              null
            )
            .use(Cursor::readStrings)
        ).containsExactly("duplicate", "duplicate").inOrder()
      }
  }
}

private fun indexNames(
  master: String,
  tableName: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = Select
  .raw("SELECT name FROM $master WHERE type = 'index' AND tbl_name = ?")
  .from(ANONYMOUS_TABLE)
  .withArgs(tableName)
  .usingConnection(connection)
  .execute()
  .use(Cursor::readStrings)
  .toSet()

private fun indexColumns(
  master: String,
  indexName: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = Select
  .raw(
    "PRAGMA ${
      when (master) {
        SQLITE_MASTER -> "main"
        SQLITE_TEMP_MASTER -> "temp"
        else -> error("Unsupported SQLite schema master: $master")
      }
    }.index_info('$indexName')"
  )
  .from(ANONYMOUS_TABLE)
  .usingConnection(connection)
  .execute()
  .use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.getString(2))
      }
    }
  }

private fun indexIsUnique(
  tableName: String,
  indexName: String,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = Select
  .raw("PRAGMA index_list('$tableName')")
  .from(ANONYMOUS_TABLE)
  .usingConnection(connection)
  .execute()
  .use { cursor ->
    while (cursor.moveToNext()) {
      if (cursor.getString(1) == indexName) {
        return@use cursor.getInt(2) == 1
      }
    }
    false
  }

private fun rebuiltRows(
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = Select
  .raw("SELECT id, value, rebuilt_value FROM index_rebuild ORDER BY id")
  .from(ANONYMOUS_TABLE)
  .usingConnection(connection)
  .execute()
  .use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          listOf(
            cursor.getString(0),
            cursor.getString(1),
            cursor.getString(2)
          )
        )
      }
    }
  }

private fun seedIndexMigrationDatabase(
  application: Application,
  databaseName: String,
  version: Int
) {
  application
    .openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
    .use { database ->
      database.execSQL(
        "CREATE TABLE index_add_drop_change " +
            "(id INTEGER PRIMARY KEY, added_value TEXT, changed_value TEXT)"
      )
      database.execSQL(
        "INSERT INTO index_add_drop_change (id, added_value, changed_value) " +
            "VALUES (1, 'added', 'changed')"
      )
      database.execSQL(
        "CREATE INDEX removed_index ON index_add_drop_change (added_value)"
      )
      database.execSQL(
        "CREATE INDEX changed_index ON index_add_drop_change (changed_value)"
      )
      database.execSQL(
        "CREATE TABLE index_rebuild (id INTEGER PRIMARY KEY, value TEXT, rebuilt_value TEXT)"
      )
      database.execSQL(
        "INSERT INTO index_rebuild (id, value, rebuilt_value) VALUES (1, 'preserved', '')"
      )
      database.execSQL(
        "CREATE INDEX unchanged_rebuild_index ON index_rebuild (value)"
      )
      database.version = version
    }
}

private fun seedIndexFailureDatabase(
  application: Application,
  databaseName: String,
  version: Int
) {
  application
    .openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
    .use { database ->
      database.execSQL(
        "CREATE TABLE index_migration_failure (id INTEGER PRIMARY KEY, value TEXT)"
      )
      database.execSQL(
        "INSERT INTO index_migration_failure (id, value) VALUES (1, 'duplicate')"
      )
      database.execSQL(
        "INSERT INTO index_migration_failure (id, value) VALUES (2, 'duplicate')"
      )
      database.version = version
    }
}

private class IndexMigrationDatabase(
  private val version: Int
) : GeneratedDatabase by SqliteMagicDatabase() {
  override fun getDbVersion() = version
}
