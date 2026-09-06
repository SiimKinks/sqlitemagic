package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertThrows
import org.junit.Test

private const val MIGRATION_DATABASE_NAME = "schema-migration-runtime-test.db"
private const val SQLITE_MASTER = "sqlite_master"
private const val SQLITE_TEMP_MASTER = "sqlite_temp_master"
private const val GENERATED_DATABASE_VERSION = 1020
private const val VERSION_1019_BOOK_KEY = "migration-book-key"
private const val VERSION_1019_BOOK_TITLE = "migration-book-title"
private const val MIGRATION_FAILURE_MESSAGE = "intentional migration failure"

private val VERSION_1020_PERSISTENT_TABLES = setOf(
  "nested_model_container_nested_entity",
  "value_class_entity",
  "java_mutable_entity"
)

private val VERSION_1020_PERSISTENT_SCHEMA_FRAGMENTS = mapOf(
  "nested_model_container_nested_entity" to setOf(
    "id TEXT PRIMARY KEY",
    "value TEXT DEFAULT ''"
  ),
  "value_class_entity" to setOf(
    "value TEXT PRIMARY KEY"
  ),
  "java_mutable_entity" to setOf(
    "id TEXT PRIMARY KEY",
    "value TEXT DEFAULT NULL"
  )
)

private val GENERATED_TEMPORARY_TABLES = setOf(
  "temporary_account_entry",
  "temporary_without_row_id_entity",
  "main_session_value",
  "submodule_session_value"
)

private val REBUILT_CURRENT_TABLES = setOf(
  "simple_mutable_entity",
  "entity_with_relationship",
  "complex_object_with_same_leafs"
)

private val REBUILT_CURRENT_SCHEMA_FRAGMENTS = mapOf(
  "simple_mutable_entity" to setOf(
    "id INTEGER PRIMARY KEY AUTOINCREMENT",
    "value TEXT DEFAULT NULL",
    "boxed_boolean INTEGER DEFAULT NULL",
    "primitive_boolean INTEGER DEFAULT 0"
  ),
  "entity_with_relationship" to setOf(
    "id INTEGER PRIMARY KEY AUTOINCREMENT",
    "value TEXT DEFAULT NULL",
    "related_entity INTEGER DEFAULT NULL REFERENCES \"simple_mutable_entity\"(id) ON DELETE CASCADE",
    "count INTEGER DEFAULT NULL"
  ),
  "complex_object_with_same_leafs" to setOf(
    "id INTEGER PRIMARY KEY AUTOINCREMENT",
    "name TEXT DEFAULT ''",
    "simple_value INTEGER DEFAULT NULL",
    "entity_with_relationship INTEGER DEFAULT NULL",
    "simple_value_duplicate INTEGER DEFAULT NULL"
  )
)

private val REBUILT_OLD_TABLES = setOf(
  "author",
  "author_",
  "magazine",
  "magazine_",
  "complex_object_with_same_leafs_"
)

class SchemaMigrationRuntimeTest {
  @Test
  fun upgradesFromVersion1019AndPreservesLibraryBook() {
    val application = application()
    application.deleteDatabase(MIGRATION_DATABASE_NAME)
    try {
      val expectedBook = LibraryBook(
        id = VERSION_1019_BOOK_KEY,
        title = VERSION_1019_BOOK_TITLE
      )
      seedVersion1019Database(application = application)

      val connection = openMigrationConnection(application = application)
      try {
        assertThat(userVersion(connection = connection))
          .isEqualTo(GENERATED_DATABASE_VERSION)
        assertThat(
          Select
            .from(LIBRARY_BOOK)
            .usingConnection(connection)
            .execute()
        ).containsExactly(expectedBook)
        assertThat(
          tableNames(
            connection = connection,
            master = SQLITE_MASTER,
            names = VERSION_1020_PERSISTENT_TABLES
          )
        ).containsExactlyElementsIn(VERSION_1020_PERSISTENT_TABLES)
        assertSchemaFragments(
          connection = connection,
          expectedFragments = VERSION_1020_PERSISTENT_SCHEMA_FRAGMENTS
        )
        assertThat(
          tableNames(
            connection = connection,
            master = SQLITE_TEMP_MASTER,
            names = GENERATED_TEMPORARY_TABLES
          )
        ).containsExactlyElementsIn(GENERATED_TEMPORARY_TABLES)
        assertThat(
          tableNames(
            connection = connection,
            master = SQLITE_MASTER,
            names = GENERATED_TEMPORARY_TABLES
          )
        ).isEmpty()
      } finally {
        connection.close()
      }
    } finally {
      application.deleteDatabase(MIGRATION_DATABASE_NAME)
    }
  }

  @Test
  fun upgradesFromVersion1008RebuildsAndRenamesLegacyTables() {
    val application = application()
    application.deleteDatabase(MIGRATION_DATABASE_NAME)
    try {
      seedDatabase(
        application = application,
        version = 1008,
        statements = listOf(
          "CREATE TABLE author (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT NULL, " +
              "boxed_boolean INTEGER DEFAULT NULL, primitive_boolean INTEGER DEFAULT 0)",
          "CREATE TABLE magazine (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT NULL, " +
              "author INTEGER DEFAULT NULL REFERENCES author(id) ON DELETE CASCADE, " +
              "nr_of_releases INTEGER DEFAULT NULL)",
          "CREATE TABLE complex_object_with_same_leafs (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
              "name TEXT DEFAULT '', simple_value INTEGER DEFAULT NULL, magazine INTEGER DEFAULT NULL, " +
              "simple_value_duplicate INTEGER DEFAULT NULL)",
          "INSERT INTO author (id, name, boxed_boolean, primitive_boolean) " +
              "VALUES (7, 'legacy-author-name', 1, 0)",
          "INSERT INTO magazine (id, name, author, nr_of_releases) " +
              "VALUES (11, 'legacy-magazine-name', 7, 3)",
          "INSERT INTO complex_object_with_same_leafs " +
              "(id, name, simple_value, magazine, simple_value_duplicate) " +
              "VALUES (13, 'legacy-complex-name', 17, 11, 19)"
        )
      )

      val connection = openMigrationConnection(application = application)
      try {
        assertThat(userVersion(connection = connection))
          .isEqualTo(GENERATED_DATABASE_VERSION)
        assertThat(
          tableNames(
            connection = connection,
            master = SQLITE_MASTER,
            names = REBUILT_CURRENT_TABLES
          )
        ).containsExactlyElementsIn(REBUILT_CURRENT_TABLES)
        assertSchemaFragments(
          connection = connection,
          expectedFragments = REBUILT_CURRENT_SCHEMA_FRAGMENTS
        )
        assertThat(
          tableNames(
            connection = connection,
            master = SQLITE_MASTER,
            names = REBUILT_OLD_TABLES
          )
        ).isEmpty()
        assertThat(
          rawRows(
            connection = connection,
            table = SIMPLE_MUTABLE_ENTITY,
            sql = "SELECT id, value, boxed_boolean, primitive_boolean FROM simple_mutable_entity"
          )
        ).containsExactlyElementsIn(
          listOf(
            listOf("7", null, "1", "0")
          )
        )
        assertThat(
          rawRows(
            connection = connection,
            table = ENTITY_WITH_RELATIONSHIP,
            sql = "SELECT id, value, related_entity, count FROM entity_with_relationship"
          )
        ).containsExactlyElementsIn(
          listOf(
            listOf("11", null, null, null)
          )
        )
        assertThat(
          rawRows(
            connection = connection,
            table = COMPLEX_OBJECT_WITH_SAME_LEAFS,
            sql = "SELECT id, name, simple_value, entity_with_relationship, simple_value_duplicate " +
                "FROM complex_object_with_same_leafs"
          )
        ).containsExactlyElementsIn(
          listOf(
            listOf("13", "legacy-complex-name", "17", null, "19")
          )
        )
      } finally {
        connection.close()
      }
    } finally {
      application.deleteDatabase(MIGRATION_DATABASE_NAME)
    }
  }

  @Test
  fun failedMigrationRollsBackUpgradeTransaction() {
    val application = application()
    application.deleteDatabase(MIGRATION_DATABASE_NAME)
    try {
      seedVersion1019Database(application = application)

      var connection: DbConnection? = null
      try {
        val exception = assertThrows(IllegalStateException::class.java) {
          connection = openMigrationConnection(
            application = application,
            database = FailingMigrationDatabase()
          )
          userVersion(connection = checkNotNull(connection))
        }
        assertThat(exception)
          .hasMessageThat()
          .isEqualTo(MIGRATION_FAILURE_MESSAGE)
      } finally {
        connection?.close()
      }

      val database = application.openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      try {
        assertThat(database.version).isEqualTo(1019)
        assertThat(
          database
            .rawQuery(
              "SELECT book_key, title_text FROM library_books",
              null
            )
            .use(Cursor::readRows)
        ).containsExactlyElementsIn(
          listOf(
            listOf(VERSION_1019_BOOK_KEY, VERSION_1019_BOOK_TITLE)
          )
        )
        assertThat(
          database
            .rawQuery(
              "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                  "(${VERSION_1020_PERSISTENT_TABLES.joinToString(separator = ", ") { "?" }})",
              VERSION_1020_PERSISTENT_TABLES.toTypedArray()
            )
            .use(Cursor::readStrings)
        ).isEmpty()
      } finally {
        database.close()
      }
    } finally {
      application.deleteDatabase(MIGRATION_DATABASE_NAME)
    }
  }

  private fun seedVersion1019Database(application: Application) = seedDatabase(
    application = application,
    version = 1019,
    statements = listOf(
      "CREATE TABLE library_books (book_key TEXT PRIMARY KEY, title_text TEXT DEFAULT 'untitled')",
      "INSERT INTO library_books (book_key, title_text) VALUES ('$VERSION_1019_BOOK_KEY', '$VERSION_1019_BOOK_TITLE')"
    )
  )

  private fun seedDatabase(
    application: Application,
    version: Int,
    statements: List<String>
  ) {
    application
      .openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        statements.forEach(database::execSQL)
        database.version = version
      }
  }

  private fun application() = InstrumentationRegistry
    .getInstrumentation()
    .targetContext
    .applicationContext as Application

  private fun openMigrationConnection(
    application: Application,
    database: GeneratedDatabase = SqliteMagicDatabase()
  ) = SqliteMagic
    .builder(application)
    .name(MIGRATION_DATABASE_NAME)
    .database(database)
    .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
    .scheduleRxQueriesOn(Schedulers.trampoline())
    .openNewConnection()

  private fun userVersion(connection: DbConnection) = Select
    .raw("PRAGMA user_version")
    .from(Table.ANONYMOUS_TABLE)
    .usingConnection(connection)
    .execute()
    .use { cursor ->
      check(cursor.moveToFirst())
      cursor.getInt(0)
    }

  private fun tableNames(
    connection: DbConnection,
    master: String,
    names: Set<String>
  ) = Select
    .raw(
      "SELECT name FROM $master WHERE type = 'table' AND name IN " +
          "(${names.joinToString(separator = ", ") { "?" }})"
    )
    .from(Table.ANONYMOUS_TABLE)
    .withArgs(*names.toTypedArray())
    .usingConnection(connection)
    .execute()
    .use(Cursor::readStrings)
    .toSet()

  private fun assertSchemaFragments(
    connection: DbConnection,
    expectedFragments: Map<String, Set<String>>
  ) {
    expectedFragments.forEach { (tableName, fragments) ->
      val schema = tableSql(
        connection = connection,
        tableName = tableName
      )
      assertThat(schema).contains(tableName)
      fragments.forEach { fragment ->
        assertThat(schema).contains(fragment)
      }
    }
  }

  private fun tableSql(
    connection: DbConnection,
    tableName: String
  ) = Select
    .raw("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")
    .from(Table.ANONYMOUS_TABLE)
    .withArgs(tableName)
    .usingConnection(connection)
    .execute()
    .use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }

  private fun rawRows(
    connection: DbConnection,
    table: Table<*>,
    sql: String
  ) = Select
    .raw(sql)
    .from(table)
    .usingConnection(connection)
    .execute()
    .use(Cursor::readRows)
}

private class FailingMigrationDatabase : GeneratedDatabase by SqliteMagicDatabase() {
  override fun migrateViews(db: SupportSQLiteDatabase) {
    throw IllegalStateException(MIGRATION_FAILURE_MESSAGE)
  }
}

private fun Cursor.readStrings() = buildList {
  while (moveToNext()) {
    add(getString(0))
  }
}

private fun Cursor.readRows() = buildList {
  while (moveToNext()) {
    add(buildList {
      repeat(times = columnCount) { columnIndex ->
        add(if (isNull(columnIndex)) null else getString(columnIndex))
      }
    })
  }
}
