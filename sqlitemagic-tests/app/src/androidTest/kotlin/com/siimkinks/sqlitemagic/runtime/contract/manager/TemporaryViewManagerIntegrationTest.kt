@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.manager

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.MainSessionViewTable.Companion.MAIN_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentSourceSessionViewTable.Companion.PERSISTENT_SOURCE_SESSION_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionViewTable.Companion.SUBMODULE_SESSION_VIEW
import com.siimkinks.sqlitemagic.TemporaryDependencyOuterViewTable.Companion.TEMPORARY_DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.view.MainSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSourceSessionView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.TemporaryDependencyOuterView
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.internal.GeneratedSchema
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.SqlUtil
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionView
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.PrefixFailingDatabase
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_TEMP_MASTER
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readableDatabase
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.withEmptyDatabase
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

private const val DATABASE_NAME = "temporary-view-manager-integration.db"
private const val WAL_DATABASE_NAME = "temporary-view-wal-integration.db"
private const val SCHEMA_DATABASE_NAME = "temporary-view-schema-integration.db"
private const val FAILURE_DATABASE_NAME = "temporary-view-failure-integration.db"
private const val LIFETIME_DATABASE_NAME = "temporary-view-lifetime-integration.db"

internal class TemporaryViewManagerIntegrationTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun temporaryViewReadsPersistentViewThroughAnotherTemporaryView() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      ).use { connection ->
        assertThat(
          schemaObjectNames(
            master = SQLITE_TEMP_MASTER,
            type = "view",
            database = connection.readableDatabase
          )
        ).containsAtLeast("a_temporary_dependency_outer", "z_temporary_dependency_middle")

        insertedResult(
          result = QueryCompositionAuthor(
            id = 93L,
            name = "Ada"
          )
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "QueryCompositionAuthor"
        )

        assertThat(
          captureRows(
            table = TEMPORARY_DEPENDENCY_OUTER_VIEW,
            connection = connection
          )
        ).containsExactly(TemporaryDependencyOuterView(name = "Ada"))
      }
    }

  @Test
  fun generatedTemporaryViewsShareTheTemporarySchemaWithTablesAndIndexes() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      ).use { connection ->
        assertThat(
          schemaObjectNames(
            master = SQLITE_TEMP_MASTER,
            type = "view",
            database = connection.readableDatabase
          )
        ).containsAtLeast("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          schemaObjectNames(
            master = SQLITE_MASTER,
            type = "view",
            database = connection.readableDatabase
          )
        ).containsNoneOf("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          schemaObjectNames(
            master = SQLITE_TEMP_MASTER,
            type = "table",
            database = connection.readableDatabase
          )
        ).containsAtLeast("main_session_value", "submodule_session_value")
        assertThat(
          schemaObjectNames(
            master = SQLITE_TEMP_MASTER,
            type = "index",
            database = connection.readableDatabase
          )
        ).containsAtLeast("main_session_value_index", "submodule_session_value_index")
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
        insertedResult(
          result = main
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "MainSessionValue"
        )
        insertedResult(
          result = submodule
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "SubmoduleSessionValue"
        )
        insertedResult(
          result = persistent
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "QueryCompositionAuthor"
        )

        assertThat(
          captureRows(
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
          captureRows(
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
          SELECT
            .FROM(MAIN_SESSION_VIEW)
            .execute()
        ).isEmpty()
        assertThat(
          SELECT
            .FROM(SUBMODULE_SESSION_VIEW)
            .execute()
        ).isEmpty()
        assertThat(
          SELECT
            .FROM(QUERY_COMPOSITION_AUTHOR)
            .execute()
        ).isEmpty()

        val version = connection.readableDatabase.version
        connection.close()
        connection = openNamedConnection(
          application = application,
          databaseName = DATABASE_NAME
        )

        assertThat(connection.readableDatabase.version).isEqualTo(version)
        assertThat(
          captureRows(
            table = MAIN_SESSION_VALUE,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          captureRows(
            table = SUBMODULE_SESSION_VALUE,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          captureRows(
            table = MAIN_SESSION_VIEW,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          captureRows(
            table = SUBMODULE_SESSION_VIEW,
            connection = connection
          )
        ).isEmpty()
        assertThat(
          captureRows(
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
          schemaObjectNames(
            master = SQLITE_TEMP_MASTER,
            type = "index",
            database = connection.readableDatabase
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
        schemaObjectNames(
          master = SQLITE_TEMP_MASTER,
          type = "table",
          database = database
        )
      ).contains("application_session_table")
      assertThat(
        schemaObjectNames(
          master = SQLITE_TEMP_MASTER,
          type = "view",
          database = database
        )
      ).contains("application_session_view")
      assertThat(
        schemaObjectNames(
          master = SQLITE_TEMP_MASTER,
          type = "view",
          database = database
        )
      ).containsAtLeast("main_session_view", "persistent_source_session_view", "submodule_session_view")
    }

  @Test
  fun generatedTemporaryViewDdlFailureRethrowsAndRollsBackEarlierTemporaryObjects() =
    withEmptyDatabase(databaseName = FAILURE_DATABASE_NAME) { database ->
      SqliteMagicDatabase().createSchema(database)
      val expected = IllegalStateException("Injected temporary view DDL failure")
      val failingDatabase = PrefixFailingDatabase(
        delegate = database,
        sqlPrefix = """CREATE TEMPORARY VIEW IF NOT EXISTS "persistent_source_session_view"""",
        failure = expected
      )

      val actual = assertThrows(IllegalStateException::class.java) {
        SqliteMagicDatabase().createTemporarySchema(failingDatabase)
      }

      assertThat(actual)
        .isSameInstanceAs(expected)
      assertThat(
        schemaObjectNames(
          master = SQLITE_TEMP_MASTER,
          type = "table",
          database = database
        )
      ).containsNoneOf("main_session_value", "submodule_session_value")
      assertThat(
        schemaObjectNames(
          master = SQLITE_TEMP_MASTER,
          type = "view",
          database = database
        )
      ).containsNoneOf("main_session_view", "persistent_source_session_view", "submodule_session_view")
    }

  @Test
  fun persistentViewOverTemporaryTableFailsBeforeDdlAndRollsBackEarlierWork() =
    withEmptyDatabase(databaseName = LIFETIME_DATABASE_NAME) { database ->
      val definition = SELECT
        .COLUMN(MAIN_SESSION_VALUE.VALUE)
        .FROM(MAIN_SESSION_VALUE)
      val schema = object : GeneratedSchema {
        override fun createSchemaTables(db: SupportSQLiteDatabase, temporary: Boolean) =
          db.execSQL("CREATE TABLE earlier_persistent_object (value TEXT)")

        override fun collectGeneratedViews(views: MutableList<GeneratedView>) {
          views += GeneratedView(
            viewName = "invalid_persistent_view",
            temporary = false,
            query = { definition },
            columns = listOf("main_session_value.value")
          )
        }

        override fun createSchemaIndexes(db: SupportSQLiteDatabase, temporary: Boolean) = Unit

        override fun clearTables(db: SupportSQLiteDatabase, changedTables: MutableScatterSet<String>) = Unit
      }

      val failure = assertThrows(IllegalArgumentException::class.java) {
        SqlUtil.createSchema(
          db = database,
          schema = schema,
          temporary = false
        )
      }
      assertThat(failure)
        .hasMessageThat()
        .contains("invalid_persistent_view")
      assertThat(failure)
        .hasMessageThat()
        .contains("main_session_value")

      assertThat(
        schemaObjectNames(
          master = SQLITE_MASTER,
          type = "table",
          database = database
        )
      ).doesNotContain("earlier_persistent_object")
      assertThat(
        schemaObjectNames(
          master = SQLITE_MASTER,
          type = "view",
          database = database
        )
      ).doesNotContain("invalid_persistent_view")
    }

  @Test
  fun walNamedWriterSessionReadsTemporaryTablesAndViewsAfterReopen() =
    withNamedDatabase(databaseName = WAL_DATABASE_NAME) { application ->
      val factory = SupportSQLiteOpenHelper.Factory { configuration ->
        FrameworkSQLiteOpenHelperFactory()
          .create(configuration)
          .apply { setWriteAheadLoggingEnabled(true) }
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
          insertedResult(
            result = MainSessionValue(
              id = "wal",
              value = "wal value"
            )
              .insert()
              .usingConnection(connection)
              .execute(),
            modelName = "MainSessionValue"
          )
          assertThat(
            captureRows(
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
            captureRows(
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
            captureRows(
              table = MAIN_SESSION_VALUE,
              connection = connection
            )
          ).isEmpty()
          assertThat(
            captureRows(
              table = MAIN_SESSION_VIEW,
              connection = connection
            )
          ).isEmpty()
          assertThat(
            schemaObjectNames(
              master = SQLITE_TEMP_MASTER,
              type = "view",
              database = connection.readableDatabase
            )
          ).contains("main_session_view")
        }
      } finally {
        connection.close()
      }
    }

  private fun scalar(
    connection: DbConnection,
    sql: String
  ) = connection
    .readableDatabase
    .query(sql)
    .use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }
}
