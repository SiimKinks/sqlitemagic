package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.MainSessionViewTable.Companion.MAIN_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentSourceSessionViewTable.Companion.PERSISTENT_SOURCE_SESSION_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqlUtil
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionViewTable.Companion.SUBMODULE_SESSION_VIEW
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.view.MainSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSourceSessionView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionView
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DATABASE_NAME = "temporary-view-manager-integration.db"
private const val WAL_DATABASE_NAME = "temporary-view-wal-integration.db"
private const val SCHEMA_DATABASE_NAME = "temporary-view-schema-integration.db"
private const val FAILURE_DATABASE_NAME = "temporary-view-failure-integration.db"
private const val LIFETIME_DATABASE_NAME = "temporary-view-lifetime-integration.db"

class TemporaryViewManagerIntegrationTest : RuntimeDatabaseTest() {
  @Test
  fun generatedTemporaryViewsShareTheTemporarySchemaWithTablesAndIndexes() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      ).use { connection ->
        assertThat(
          names(
            connection = connection,
            master = "sqlite_temp_master",
            type = "view"
          )
        ).containsAtLeast("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          names(
            connection = connection,
            master = "sqlite_master",
            type = "view"
          )
        ).containsNoneOf("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          names(
            connection = connection,
            master = "sqlite_temp_master",
            type = "table"
          )
        ).containsAtLeast("main_session_value", "submodule_session_value")
        assertThat(
          names(
            connection = connection,
            master = "sqlite_temp_master",
            type = "index"
          )
        ).containsAtLeast("main_session_value_index", "submodule_session_value_index")

        val main = MainSessionValue(
          id = "main",
          value = "main value"
        )
        val submodule = SubmoduleSessionValue(
          id = "submodule",
          value = "submodule value"
        )
        val persistent = QueryCompositionAuthor(
          id = 41L,
          name = "persistent value"
        )
        main
          .insert()
          .usingConnection(connection)
          .execute()
        submodule
          .insert()
          .usingConnection(connection)
          .execute()
        persistent
          .insert()
          .usingConnection(connection)
          .execute()

        assertThat(
          rows(
            table = MAIN_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          MainSessionView(
            id = main.id,
            value = main.value
          )
        )
        assertThat(
          rows(
            table = SUBMODULE_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          SubmoduleSessionView(
            id = submodule.id,
            value = submodule.value
          )
        )
        assertThat(
          rows(
            table = PERSISTENT_SOURCE_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          PersistentSourceSessionView(
            id = persistent.id,
            name = persistent.name
          )
        )
      }
    }

  @Test
  fun namedConnectionReopenRecreatesViewsOverEmptyTemporaryTablesAndRetainsPersistentRows() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      var connection = openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      )
      try {
        val main = MainSessionValue(
          id = "reopen-main",
          value = "main value"
        )
        val submodule = SubmoduleSessionValue(
          id = "reopen-submodule",
          value = "submodule value"
        )
        val persistent = QueryCompositionAuthor(
          id = 42L,
          name = "reopen persistent"
        )
        main
          .insert()
          .usingConnection(connection)
          .execute()
        submodule
          .insert()
          .usingConnection(connection)
          .execute()
        persistent
          .insert()
          .usingConnection(connection)
          .execute()

        assertThat(
          rows(
            table = MAIN_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          MainSessionView(
            id = main.id,
            value = main.value
          )
        )
        assertThat(
          rows(
            table = SUBMODULE_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          SubmoduleSessionView(
            id = submodule.id,
            value = submodule.value
          )
        )
        assertThat(
          Select.from(MAIN_SESSION_VIEW)
            .execute()
        ).isEmpty()
        assertThat(
          Select.from(SUBMODULE_SESSION_VIEW)
            .execute()
        ).isEmpty()
        assertThat(
          Select.from(QUERY_COMPOSITION_AUTHOR)
            .execute()
        ).isEmpty()

        val version = scalar(
          connection = connection,
          sql = "PRAGMA user_version"
        )
        connection.close()
        connection = openNamedConnection(
          application = application,
          databaseName = DATABASE_NAME
        )

        assertThat(
          scalar(
            connection = connection,
            sql = "PRAGMA user_version"
          )
        ).isEqualTo(version)
        assertThat(
          rows(
            table = MAIN_SESSION_VALUE,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          rows(
            table = SUBMODULE_SESSION_VALUE,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          rows(
            table = MAIN_SESSION_VIEW,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          rows(
            table = SUBMODULE_SESSION_VIEW,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          rows(
            table = PERSISTENT_SOURCE_SESSION_VIEW,
            connection = connection
          )
        ).containsExactly(
          PersistentSourceSessionView(
            id = persistent.id,
            name = persistent.name
          )
        )
        assertThat(
          names(
            connection = connection,
            master = "sqlite_temp_master",
            type = "index"
          )
        ).containsAtLeast("main_session_value_index", "submodule_session_value_index")
      } finally {
        connection.close()
      }
    }

  @Test
  fun generatedTemporarySchemaPreservesApplicationOwnedTemporaryObjects() =
    withEmptyDatabase(databaseName = SCHEMA_DATABASE_NAME) { database ->
      SqliteMagicDatabase().createSchema(database)
      database.execSQL("CREATE TEMP TABLE application_session_table (value TEXT)")
      database.execSQL(
        "CREATE TEMP VIEW application_session_view AS SELECT value FROM application_session_table"
      )

      SqliteMagicDatabase().createTemporarySchema(database)

      assertThat(
        names(
          database = database,
          master = "sqlite_temp_master",
          type = "table"
        )
      ).contains("application_session_table")
      assertThat(
        names(
          database = database,
          master = "sqlite_temp_master",
          type = "view"
        )
      ).contains("application_session_view")
      assertThat(
        names(
          database = database,
          master = "sqlite_temp_master",
          type = "view"
        )
      ).containsAtLeast("main_session_view", "persistent_source_session_view", "submodule_session_view")
    }

  @Test
  fun generatedTemporaryViewDdlFailureRethrowsAndRollsBackEarlierTemporaryObjects() =
    withEmptyDatabase(databaseName = FAILURE_DATABASE_NAME) { database ->
      SqliteMagicDatabase().createSchema(database)
      val expected = IllegalStateException("Injected temporary view DDL failure")
      val failingDatabase = FailingDatabase(
        delegate = database,
        sqlPrefix = "CREATE TEMPORARY VIEW IF NOT EXISTS \"persistent_source_session_view\"",
        failure = expected
      )

      val actual = assertThrows(IllegalStateException::class.java) {
        SqliteMagicDatabase().createTemporarySchema(failingDatabase)
      }

      assertThat(actual)
        .isSameInstanceAs(expected)
      assertThat(
        names(
          database = database,
          master = "sqlite_temp_master",
          type = "table"
        )
      ).containsNoneOf("main_session_value", "submodule_session_value")
      assertThat(
        names(
          database = database,
          master = "sqlite_temp_master",
          type = "view"
        )
      ).containsNoneOf("main_session_view", "persistent_source_session_view", "submodule_session_view")
    }

  @Test
  fun persistentViewOverTemporaryTableFailsBeforeDdlAndRollsBackEarlierWork() =
    withEmptyDatabase(databaseName = LIFETIME_DATABASE_NAME) { database ->
      val definition = Select
        .column(MAIN_SESSION_VALUE.VALUE)
        .from(MAIN_SESSION_VALUE)
        .compile()

      database.beginTransaction()
      try {
        database.execSQL("CREATE TABLE earlier_persistent_object (value TEXT)")

        val failure = assertThrows(IllegalArgumentException::class.java) {
          SqlUtil.createView(
            db = database,
            query = definition,
            viewName = "invalid_persistent_view",
            temporary = false
          )
        }
        assertThat(failure)
          .hasMessageThat()
          .contains("invalid_persistent_view")
        assertThat(failure)
          .hasMessageThat()
          .contains("main_session_value")
      } finally {
        database.endTransaction()
      }

      assertThat(
        names(
          database = database,
          master = "sqlite_master",
          type = "table"
        )
      ).doesNotContain("earlier_persistent_object")
      assertThat(
        names(
          database = database,
          master = "sqlite_master",
          type = "view"
        )
      ).doesNotContain("invalid_persistent_view")
    }

  @Test
  fun walNamedWriterSessionReadsTemporaryTablesAndViewsAfterReopen() =
    withNamedDatabase(databaseName = WAL_DATABASE_NAME) { application ->
      val factory = SupportSQLiteOpenHelper.Factory { configuration ->
        FrameworkSQLiteOpenHelperFactory()
          .create(configuration)
          .also { helper -> helper.setWriteAheadLoggingEnabled(true) }
      }
      var connection = openNamedConnection(
        application = application,
        databaseName = WAL_DATABASE_NAME,
        sqliteFactory = factory
      )
      try {
        assertThat(
          scalar(
            connection = connection,
            sql = "PRAGMA journal_mode"
          )
        ).isEqualTo("wal")
        connection.inTransaction {
          MainSessionValue(
            id = "wal",
            value = "wal value"
          )
            .insert()
            .usingConnection(connection)
            .execute()
          assertThat(
            rows(
              table = MAIN_SESSION_VALUE,
              connection = connection
            )
          ).containsExactly(
            MainSessionValue(
              id = "wal",
              value = "wal value"
            )
          )
          assertThat(
            rows(
              table = MAIN_SESSION_VIEW,
              connection = connection
            )
          ).containsExactly(
            MainSessionView(
              id = "wal",
              value = "wal value"
            )
          )
        }

        connection.close()
        connection = openNamedConnection(
          application = application,
          databaseName = WAL_DATABASE_NAME,
          sqliteFactory = factory
        )
        connection.inTransaction {
          assertThat(
            rows(
              table = MAIN_SESSION_VALUE,
              connection = connection
            )
          ).isEmpty()
          assertThat(
            rows(
              table = MAIN_SESSION_VIEW,
              connection = connection
            )
          ).isEmpty()
          assertThat(
            names(
              connection = connection,
              master = "sqlite_temp_master",
              type = "view"
            )
          ).contains("main_session_view")
        }
      } finally {
        connection.close()
      }
    }

  private fun names(
    connection: DbConnection,
    master: String,
    type: String
  ) = Select
    .raw("SELECT name FROM $master WHERE type = ?")
    .from(ANONYMOUS_TABLE)
    .withArgs(type)
    .usingConnection(connection)
    .execute()
    .use(Cursor::readStrings)

  private fun names(
    database: SupportSQLiteDatabase,
    master: String,
    type: String
  ) = database
    .query("SELECT name FROM $master WHERE type = ?", arrayOf(type))
    .use(Cursor::readStrings)

  private fun scalar(
    connection: DbConnection,
    sql: String
  ) = Select
    .raw(sql)
    .from(ANONYMOUS_TABLE)
    .usingConnection(connection)
    .execute()
    .use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }

  private fun <T> rows(
    table: Table<T>,
    connection: DbConnection
  ) = Select
    .from(table)
    .usingConnection(connection)
    .execute()

  private class FailingDatabase(
    private val delegate: SupportSQLiteDatabase,
    private val sqlPrefix: String,
    private val failure: RuntimeException
  ) : SupportSQLiteDatabase by delegate {
    override fun execSQL(sql: String) {
      if (sql.startsWith(sqlPrefix)) {
        throw failure
      }
      delegate.execSQL(sql)
    }
  }

  private fun withEmptyDatabase(
    databaseName: String,
    block: (SupportSQLiteDatabase) -> Unit
  ) = withNamedDatabase(databaseName = databaseName) { application ->
    val configuration = SupportSQLiteOpenHelper.Configuration
      .builder(application)
      .name(databaseName)
      .callback(EMPTY_DATABASE_CALLBACK)
      .build()
    FrameworkSQLiteOpenHelperFactory()
      .create(configuration)
      .use { helper -> block(helper.writableDatabase) }
  }

  private companion object {
    val EMPTY_DATABASE_CALLBACK = object : SupportSQLiteOpenHelper.Callback(1) {
      override fun onCreate(db: SupportSQLiteDatabase) = Unit

      override fun onUpgrade(
        db: SupportSQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
      ) = Unit
    }
  }
}
