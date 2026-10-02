package com.siimkinks.sqlitemagic.runtime.contract.manager

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.GeneratedView
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.MainSessionViewTable.Companion.MAIN_SESSION_VIEW
import com.siimkinks.sqlitemagic.PersistentSourceSessionViewTable.Companion.PERSISTENT_SOURCE_SESSION_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqlUtil
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionViewTable.Companion.SUBMODULE_SESSION_VIEW
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.TemporaryDependencyOuterViewTable.Companion.TEMPORARY_DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.view.MainSessionView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSourceSessionView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.TemporaryDependencyOuterView
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionView
import com.siimkinks.sqlitemagic.runtime.support.PrefixFailingDatabase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.mainSchemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.temporarySchemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.withEmptyDatabase
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
  fun temporaryViewReadsPersistentViewThroughAnotherTemporaryView() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      ).use { connection ->
        assertThat(
          temporarySchemaObjectNames(
            connection = connection,
            type = "view"
          )
        ).containsAtLeast("a_temporary_dependency_outer", "z_temporary_dependency_middle")

        assertSeedInserted(
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
          temporarySchemaObjectNames(
            connection = connection,
            type = "view"
          )
        ).containsAtLeast("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          mainSchemaObjectNames(
            connection = connection,
            type = "view"
          )
        ).containsNoneOf("main_session_view", "persistent_source_session_view", "submodule_session_view")
        assertThat(
          temporarySchemaObjectNames(
            connection = connection,
            type = "table"
          )
        ).containsAtLeast("main_session_value", "submodule_session_value")
        assertThat(
          temporarySchemaObjectNames(
            connection = connection,
            type = "index"
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
        assertSeedInserted(
          result = main
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "MainSessionValue"
        )
        assertSeedInserted(
          result = submodule
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "SubmoduleSessionValue"
        )
        assertSeedInserted(
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
          temporarySchemaObjectNames(
            connection = connection,
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
        temporarySchemaObjectNames(
          database = database,
          type = "table"
        )
      ).contains("application_session_table")
      assertThat(
        temporarySchemaObjectNames(
          database = database,
          type = "view"
        )
      ).contains("application_session_view")
      assertThat(
        temporarySchemaObjectNames(
          database = database,
          type = "view"
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
        temporarySchemaObjectNames(
          database = database,
          type = "table"
        )
      ).containsNoneOf("main_session_value", "submodule_session_value")
      assertThat(
        temporarySchemaObjectNames(
          database = database,
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
          SqlUtil.createViews(
            db = database,
            views = listOf(
              GeneratedView(
                viewName = "invalid_persistent_view",
                temporary = false,
                definitionProvider = {
                  SqlUtil.viewDefinition(
                    query = definition,
                    viewName = "invalid_persistent_view"
                  )
                }
              )
            ),
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
        mainSchemaObjectNames(
          database = database,
          type = "table"
        )
      ).doesNotContain("earlier_persistent_object")
      assertThat(
        mainSchemaObjectNames(
          database = database,
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
          assertSeedInserted(
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
            temporarySchemaObjectNames(
              connection = connection,
              type = "view"
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
  ) = Select
    .raw(sql)
    .from(ANONYMOUS_TABLE)
    .usingConnection(connection)
    .execute()
    .use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }
}
