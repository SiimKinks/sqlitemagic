package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readRows
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

private const val MIGRATION_DATABASE_NAME = "schema-migration-runtime-test.db"
private const val SQLITE_MASTER = "sqlite_master"
private const val SQLITE_TEMP_MASTER = "sqlite_temp_master"
private const val MIGRATION_VERSION_OFFSET = 1_000_000
private const val INVALID_MIGRATION_INITIAL_VERSION = MIGRATION_VERSION_OFFSET
private const val INVALID_MIGRATION_VERSION = MIGRATION_VERSION_OFFSET + 1
private const val MISSING_MIGRATION_VERSION = MIGRATION_VERSION_OFFSET + 2
private const val LIBRARY_MIGRATION_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 100
private const val LIBRARY_MIGRATION_VERSION = MIGRATION_VERSION_OFFSET + 101
private const val REBUILD_MIGRATION_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200
private const val REBUILD_MIGRATION_VERSION = MIGRATION_VERSION_OFFSET + 201
private const val FAILING_MIGRATION_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 300
private const val FAILING_MIGRATION_VERSION = MIGRATION_VERSION_OFFSET + 301
private const val MIGRATION_BOOK_KEY = "migration-book-key"
private const val MIGRATION_BOOK_TITLE = "migration-book-title"
private const val MIGRATION_FAILURE_MESSAGE = "intentional migration failure"
private const val INVALID_MIGRATION_MESSAGE = "Error executing migration script 1000001.sql at line 2"
private const val ROLLED_BACK_TABLE = "migration_should_rollback"

private val VERSION_101_PERSISTENT_TABLES = setOf(
  "nested_model_container_nested_entity",
  "value_class_entity",
  "java_mutable_entity"
)

private val VERSION_101_PERSISTENT_SCHEMA_FRAGMENTS = mapOf(
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
  fun missingMigrationAssetsAreOptional() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedLibraryDatabase(
      application = application,
      version = INVALID_MIGRATION_VERSION
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(version = MISSING_MIGRATION_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(MISSING_MIGRATION_VERSION)
      assertThat(
        Select
          .from(LIBRARY_BOOK)
          .usingConnection(connection)
          .execute()
      ).containsExactly(
        LibraryBook(
          id = MIGRATION_BOOK_KEY,
          title = MIGRATION_BOOK_TITLE
        )
      )
    }
  }

  @Test
  fun invalidMigrationSqlRollsBackUpgradeTransaction() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedLibraryDatabase(
      application = application,
      version = INVALID_MIGRATION_INITIAL_VERSION
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      openMigrationConnection(
        application = application,
        database = VersionedMigrationDatabase(version = INVALID_MIGRATION_VERSION)
      ).use(::userVersion)
    }
    assertThat(exception)
      .hasMessageThat()
      .isEqualTo(INVALID_MIGRATION_MESSAGE)

    application
      .openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        assertThat(database.version)
          .isEqualTo(INVALID_MIGRATION_INITIAL_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
              arrayOf(ROLLED_BACK_TABLE)
            )
            .use(Cursor::readStrings)
        ).isEmpty()
        assertThat(
          database
            .rawQuery(
              "SELECT book_key, title_text FROM library_books",
              null
            )
            .use(Cursor::readRows)
        ).containsExactly(
          listOf(MIGRATION_BOOK_KEY, MIGRATION_BOOK_TITLE)
        )
      }
  }

  @Test
  fun upgradesFromVersion100AndPreservesLibraryBook() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    val expectedBook = LibraryBook(
      id = MIGRATION_BOOK_KEY,
      title = MIGRATION_BOOK_TITLE
    )
    seedLibraryDatabase(
      application = application,
      version = LIBRARY_MIGRATION_INITIAL_VERSION
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(version = LIBRARY_MIGRATION_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(LIBRARY_MIGRATION_VERSION)
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
          names = VERSION_101_PERSISTENT_TABLES
        )
      ).containsExactlyElementsIn(VERSION_101_PERSISTENT_TABLES)
      assertSchemaFragments(
        connection = connection,
        expectedFragments = VERSION_101_PERSISTENT_SCHEMA_FRAGMENTS
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
    }
  }

  @Test
  fun upgradesFromVersion200RebuildsAndRenamesLegacyTables() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = REBUILD_MIGRATION_INITIAL_VERSION,
      statements = listOf(
        "CREATE TABLE author (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT NULL, " +
            "boxed_boolean INTEGER DEFAULT NULL, primitive_boolean INTEGER DEFAULT 0)",
        "CREATE TABLE magazine (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT NULL, " +
            "author INTEGER DEFAULT NULL REFERENCES author(id) ON DELETE CASCADE, " +
            "nr_of_releases INTEGER DEFAULT NULL)",
        "CREATE TABLE complex_object_with_same_leafs (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT DEFAULT '', simple_value INTEGER DEFAULT NULL, magazine INTEGER DEFAULT NULL, " +
            "simple_value_duplicate INTEGER DEFAULT NULL)",
        "CREATE TABLE submodule_persistent_values (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "value TEXT DEFAULT '')",
        "INSERT INTO author (id, name, boxed_boolean, primitive_boolean) " +
            "VALUES (7, 'legacy-author-name', 1, 0)",
        "INSERT INTO magazine (id, name, author, nr_of_releases) " +
            "VALUES (11, 'legacy-magazine-name', 7, 3)",
        "INSERT INTO complex_object_with_same_leafs " +
            "(id, name, simple_value, magazine, simple_value_duplicate) " +
            "VALUES (13, 'legacy-complex-name', 17, 11, 19)"
      )
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(version = REBUILD_MIGRATION_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(REBUILD_MIGRATION_VERSION)
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
    }
  }

  @Test
  fun failedMigrationRollsBackUpgradeTransaction() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedLibraryDatabase(
      application = application,
      version = FAILING_MIGRATION_INITIAL_VERSION
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      openMigrationConnection(
        application = application,
        database = FailingMigrationDatabase(version = FAILING_MIGRATION_VERSION)
      ).use(::userVersion)
    }
    assertThat(exception)
      .hasMessageThat()
      .isEqualTo(MIGRATION_FAILURE_MESSAGE)

    application
      .openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        assertThat(database.version).isEqualTo(FAILING_MIGRATION_INITIAL_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT book_key, title_text FROM library_books",
              null
            )
            .use(Cursor::readRows)
        ).containsExactlyElementsIn(
          listOf(
            listOf(MIGRATION_BOOK_KEY, MIGRATION_BOOK_TITLE)
          )
        )
        assertThat(
          database
            .rawQuery(
              "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                  "(${VERSION_101_PERSISTENT_TABLES.joinToString(separator = ", ") { "?" }})",
              VERSION_101_PERSISTENT_TABLES.toTypedArray()
            )
            .use(Cursor::readStrings)
        ).isEmpty()
      }
  }

  private fun seedLibraryDatabase(
    application: Application,
    version: Int
  ) = seedDatabase(
    application = application,
    version = version,
    statements = listOf(
      "CREATE TABLE library_books (book_key TEXT PRIMARY KEY, title_text TEXT DEFAULT 'untitled')",
      "INSERT INTO library_books (book_key, title_text) VALUES ('$MIGRATION_BOOK_KEY', '$MIGRATION_BOOK_TITLE')"
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

  private fun openMigrationConnection(
    application: Application,
    database: GeneratedDatabase
  ) = openNamedConnection(
    application = application,
    databaseName = MIGRATION_DATABASE_NAME,
    database = database
  )

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

private class FailingMigrationDatabase(
  private val version: Int,
  private val delegate: SqliteMagicDatabase = SqliteMagicDatabase()
) : GeneratedDatabase by delegate {
  override fun getDbVersion() = version

  override fun createTemporarySchema(db: SupportSQLiteDatabase) = delegate.createTemporarySchema(db)

  override fun migrateViews(db: SupportSQLiteDatabase) {
    val migratedTables = db
      .query("SELECT name FROM sqlite_master WHERE type = 'table'")
      .use(Cursor::readStrings)
      .toSet()
    check(migratedTables.containsAll(VERSION_101_PERSISTENT_TABLES))
    throw IllegalStateException(MIGRATION_FAILURE_MESSAGE)
  }
}

private class VersionedMigrationDatabase(
  private val version: Int,
  private val delegate: SqliteMagicDatabase = SqliteMagicDatabase()
) : GeneratedDatabase by delegate {
  override fun getDbVersion() = version

  override fun createTemporarySchema(db: SupportSQLiteDatabase) = delegate.createTemporarySchema(db)
}
