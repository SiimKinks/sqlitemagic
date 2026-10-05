package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DependencyOuterViewTable.Companion.DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DefinitionFailureGeneratedClassesManager
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.GeneratedDatabase
import com.siimkinks.sqlitemagic.LibraryBookTable.Companion.LIBRARY_BOOK
import com.siimkinks.sqlitemagic.PersistentSubmoduleReadbackViewTable.Companion.PERSISTENT_SUBMODULE_READBACK_VIEW
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.fixture.model.LibraryBook
import com.siimkinks.sqlitemagic.fixture.view.DependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSubmoduleReadbackView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.migration.MigrationException
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
private const val VIEW_REBUILD_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200_000
private const val VIEW_REBUILD_VERSION = VIEW_REBUILD_INITIAL_VERSION + 1
private const val VIEW_SKIP_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200_010
private const val VIEW_SKIP_VERSION = VIEW_SKIP_INITIAL_VERSION + 2
private const val VIEW_QUERY_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200_020
private const val VIEW_QUERY_VERSION = VIEW_QUERY_INITIAL_VERSION + 1
private const val VIEW_DEFINITION_FAILURE_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200_030
private const val VIEW_DEFINITION_FAILURE_VERSION = VIEW_DEFINITION_FAILURE_INITIAL_VERSION + 1
private const val VIEW_DDL_FAILURE_INITIAL_VERSION = MIGRATION_VERSION_OFFSET + 200_040
private const val VIEW_DDL_FAILURE_VERSION = VIEW_DDL_FAILURE_INITIAL_VERSION + 1
private const val MIGRATION_BOOK_KEY = "migration-book-key"
private const val MIGRATION_BOOK_TITLE = "migration-book-title"
private const val VIEW_AUTHOR_ID = 41L
private const val VIEW_AUTHOR_NAME = "migration-author-name"
private const val MIGRATION_FAILURE_MESSAGE = "intentional migration failure"
private const val ROLLED_BACK_TABLE = "migration_should_rollback"
private const val ROLLBACK_LEGACY_VIEW = "migration_rollback_legacy_view"
private const val ROLLBACK_LEGACY_VIEW_SQL =
  "CREATE VIEW migration_rollback_legacy_view AS SELECT book_key, title_text FROM library_books"
private const val OLD_AUTHOR_VIEW_SQL =
  "CREATE VIEW query_composition_author_view AS " +
      "SELECT name || '-old' AS name, id FROM query_composition_author"
private const val OLD_DEFINITION_FAILURE_VIEW_SQL =
  "CREATE VIEW definition_failure_view AS SELECT id AS value FROM definition_failure_row"
private const val RETIRED_AUTHOR_TRIGGER_SQL =
  "CREATE TRIGGER retired_author_view AFTER UPDATE ON library_books BEGIN SELECT NEW.title_text; END"

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
    """related_entity INTEGER DEFAULT NULL REFERENCES "simple_mutable_entity"(id) ON DELETE CASCADE""",
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
      database = VersionedMigrationDatabase(dbVersion = MISSING_MIGRATION_VERSION)
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

    val exception = assertThrows(MigrationException::class.java) {
      openMigrationConnection(
        application = application,
        database = VersionedMigrationDatabase(dbVersion = INVALID_MIGRATION_VERSION)
      ).use(::userVersion)
    }
    assertThat(exception.fromVersion)
      .isEqualTo(INVALID_MIGRATION_INITIAL_VERSION)
    assertThat(exception.toVersion)
      .isEqualTo(INVALID_MIGRATION_VERSION)
    assertThat(exception.stepVersion)
      .isEqualTo(INVALID_MIGRATION_VERSION)
    assertThat(exception.resourceName)
      .isEqualTo("$INVALID_MIGRATION_VERSION.sql")
    assertThat(exception.physicalLine)
      .isEqualTo(2)
    assertThat(exception.statementNumber)
      .isEqualTo(2)
    assertThat(exception.statementText)
      .isEqualTo("THIS IS NOT VALID SQL")
    assertThat(exception)
      .hasMessageThat()
      .isEqualTo(
        "Error executing migration script $INVALID_MIGRATION_VERSION.sql at line 2 " +
            "(statement 2, migration $INVALID_MIGRATION_INITIAL_VERSION -> $INVALID_MIGRATION_VERSION, " +
            "step $INVALID_MIGRATION_VERSION): THIS IS NOT VALID SQL"
      )
    assertThat(exception)
      .hasCauseThat()
      .isInstanceOf(SQLiteException::class.java)
    assertThat(exception)
      .hasCauseThat()
      .hasMessageThat()
      .contains("while compiling: THIS IS NOT VALID SQL")

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
      database = VersionedMigrationDatabase(dbVersion = LIBRARY_MIGRATION_VERSION)
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
      database = VersionedMigrationDatabase(dbVersion = REBUILD_MIGRATION_VERSION)
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
  fun upgradeDropsAllPersistentViewsBeforeTableRebuildAndRecreatesCurrentViews() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = VIEW_REBUILD_INITIAL_VERSION,
      statements = librarySeedStatements() + listOf(
        "CREATE TABLE query_composition_author (id INTEGER PRIMARY KEY, legacy_name TEXT NOT NULL)",
        "INSERT INTO query_composition_author (id, legacy_name) VALUES ($VIEW_AUTHOR_ID, '$VIEW_AUTHOR_NAME')",
        "CREATE TABLE submodule_persistent_value (id TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "INSERT INTO submodule_persistent_value (id, value) VALUES ('submodule-id', 'submodule-value')",
        "CREATE VIEW query_composition_author_view AS SELECT legacy_name AS name, id FROM query_composition_author",
        RETIRED_AUTHOR_TRIGGER_SQL,
        "CREATE VIEW retired_author_view AS SELECT legacy_name FROM query_composition_author",
        "CREATE VIEW renamed_author_view AS SELECT legacy_name FROM query_composition_author",
        "CREATE VIEW removed_module_author_view AS SELECT legacy_name FROM query_composition_author",
        "CREATE VIEW persistent_submodule_readback AS SELECT id, value FROM submodule_persistent_value",
        "CREATE VIEW submodule_legacy_view AS SELECT title_text FROM library_books",
        "CREATE VIEW application_owned_view AS SELECT title_text FROM library_books"
      )
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(dbVersion = VIEW_REBUILD_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(VIEW_REBUILD_VERSION)
      assertThat(
        rawRows(
          connection = connection,
          table = QUERY_COMPOSITION_AUTHOR,
          sql = "SELECT id, name FROM query_composition_author"
        )
      ).containsExactly(listOf(VIEW_AUTHOR_ID.toString(), VIEW_AUTHOR_NAME))
      assertThat(
        Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(connection)
          .execute()
      ).containsExactly(
        QueryCompositionAuthorView(
          name = VIEW_AUTHOR_NAME,
          id = VIEW_AUTHOR_ID
        )
      )
      assertThat(
        Select
          .from(PERSISTENT_SUBMODULE_READBACK_VIEW)
          .usingConnection(connection)
          .execute()
      ).containsExactly(
        PersistentSubmoduleReadbackView(
          id = "submodule-id",
          value = "submodule-value"
        )
      )
      assertThat(
        viewSql(
          connection = connection,
          viewName = "query_composition_author_view"
        )
      ).doesNotContain("legacy_name")
      listOf(
        "retired_author_view",
        "renamed_author_view",
        "removed_module_author_view",
        "submodule_legacy_view"
      ).forEach { viewName ->
        assertThat(
          viewSql(
            connection = connection,
            viewName = viewName
          )
        ).isNull()
      }
      assertThat(
        viewSql(
          connection = connection,
          viewName = "application_owned_view"
        )
      ).isNull()
      assertThat(
        Select
          .raw("SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = ?")
          .from(Table.ANONYMOUS_TABLE)
          .withArgs("retired_author_view")
          .usingConnection(connection)
          .execute()
          .use(Cursor::readStrings)
      ).containsExactly(RETIRED_AUTHOR_TRIGGER_SQL)
    }
  }

  @Test
  fun skippedUpgradeRemovesEveryPlannedViewBeforeItsFirstScript() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = VIEW_SKIP_INITIAL_VERSION,
      statements = librarySeedStatements() + currentAuthorSeedStatements() + listOf(
        "CREATE VIEW skip_first_view AS SELECT title_text FROM library_books",
        "CREATE VIEW skip_second_view AS SELECT title_text FROM library_books",
        "CREATE VIEW skip_submodule_view AS SELECT title_text FROM library_books",
        "CREATE TABLE skip_name_now_table (value TEXT NOT NULL)",
        "INSERT INTO skip_name_now_table (value) VALUES ('kept table row')"
      )
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(dbVersion = VIEW_SKIP_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(VIEW_SKIP_VERSION)
      assertThat(
        tableNames(
          connection = connection,
          master = SQLITE_MASTER,
          names = setOf("skip_first_view", "skip_second_view", "skip_name_now_table")
        )
      ).containsExactly("skip_first_view", "skip_second_view", "skip_name_now_table")
      listOf("skip_first_view", "skip_second_view", "skip_submodule_view")
        .forEach { viewName ->
          assertThat(
            viewSql(
              connection = connection,
              viewName = viewName
            )
          ).isNull()
        }
      assertThat(
        rawRows(
          connection = connection,
          table = Table.ANONYMOUS_TABLE,
          sql = "SELECT value FROM skip_name_now_table"
        )
      ).containsExactly(listOf("kept table row"))
      assertThat(
        Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(connection)
          .execute()
      ).containsExactly(
        QueryCompositionAuthorView(
          name = VIEW_AUTHOR_NAME,
          id = VIEW_AUTHOR_ID
        )
      )
    }
  }

  @Test
  fun queryOnlyVersionBumpReplacesCurrentViewWithoutMigrationAssets() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = VIEW_QUERY_INITIAL_VERSION,
      statements = currentAuthorSeedStatements() + OLD_AUTHOR_VIEW_SQL
    )

    openMigrationConnection(
      application = application,
      database = VersionedMigrationDatabase(dbVersion = VIEW_QUERY_VERSION)
    ).use { connection ->
      assertThat(userVersion(connection = connection))
        .isEqualTo(VIEW_QUERY_VERSION)
      assertThat(
        viewSql(
          connection = connection,
          viewName = "query_composition_author_view"
        )
      ).doesNotContain("'-old'")
      assertThat(
        viewSql(
          connection = connection,
          viewName = "a_dependency_outer"
        )
      ).contains("m_dependency_middle")
      assertThat(
        Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(connection)
          .execute()
      ).containsExactly(
        QueryCompositionAuthorView(
          name = VIEW_AUTHOR_NAME,
          id = VIEW_AUTHOR_ID
        )
      )
      assertThat(
        Select
          .from(DEPENDENCY_OUTER_VIEW)
          .usingConnection(connection)
          .execute()
      ).containsExactly(DependencyOuterView(name = VIEW_AUTHOR_NAME))
    }
  }

  @Test
  fun generatedDefinitionFailureRestoresOldViewAndVersion() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = VIEW_DEFINITION_FAILURE_INITIAL_VERSION,
      statements = listOf(
        "CREATE TABLE definition_failure_row (id INTEGER PRIMARY KEY)",
        "INSERT INTO definition_failure_row (id) VALUES (73)",
        OLD_DEFINITION_FAILURE_VIEW_SQL
      )
    )

    val failure = assertThrows(IllegalStateException::class.java) {
      openMigrationConnection(
        application = application,
        database = DefinitionFailureMigrationDatabase(dbVersion = VIEW_DEFINITION_FAILURE_VERSION)
      ).use(::userVersion)
    }
    assertThat(failure)
      .hasMessageThat()
      .isEqualTo(
        "Cannot create view 'definition_failure_view': defining query uses unsupported implementation " +
            DefinitionFailureView.query.javaClass.name
      )

    application
      .openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        assertThat(database.version)
          .isEqualTo(VIEW_DEFINITION_FAILURE_INITIAL_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'definition_failure_view'",
              null
            )
            .use(Cursor::readStrings)
        ).containsExactly(OLD_DEFINITION_FAILURE_VIEW_SQL)
        assertThat(
          database
            .rawQuery("SELECT id FROM definition_failure_row", null)
            .use(Cursor::readStrings)
        ).containsExactly("73")
        assertThat(
          database
            .rawQuery("SELECT value FROM definition_failure_view", null)
            .use(Cursor::readStrings)
        ).containsExactly("73")
      }
  }

  @Test
  fun generatedViewDdlFailureRestoresOldViewAndEarlierSchema() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = VIEW_DDL_FAILURE_INITIAL_VERSION,
      statements = currentAuthorSeedStatements() + OLD_AUTHOR_VIEW_SQL
    )
    val expectedFailure = IllegalStateException("Injected generated view DDL failure")

    val actualFailure = assertThrows(IllegalStateException::class.java) {
      openMigrationConnection(
        application = application,
        database = DdlFailureMigrationDatabase(
          dbVersion = VIEW_DDL_FAILURE_VERSION,
          failure = expectedFailure
        )
      ).use(::userVersion)
    }
    assertThat(actualFailure)
      .isSameInstanceAs(expectedFailure)

    application
      .openOrCreateDatabase(
        MIGRATION_DATABASE_NAME,
        Context.MODE_PRIVATE,
        null
      )
      .use { database ->
        assertThat(database.version)
          .isEqualTo(VIEW_DDL_FAILURE_INITIAL_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'query_composition_author_view'",
              null
            )
            .use(Cursor::readStrings)
        ).containsExactly(OLD_AUTHOR_VIEW_SQL)
        assertThat(
          database
            .rawQuery("SELECT id, name FROM query_composition_author", null)
            .use(Cursor::readRows)
        ).containsExactly(listOf(VIEW_AUTHOR_ID.toString(), VIEW_AUTHOR_NAME))
        assertThat(
          database
            .rawQuery("SELECT id, name FROM query_composition_author_view", null)
            .use(Cursor::readRows)
        ).containsExactly(listOf(VIEW_AUTHOR_ID.toString(), "$VIEW_AUTHOR_NAME-old"))
        assertThat(
          database
            .rawQuery(
              "SELECT name FROM sqlite_master WHERE type = 'view' " +
                  "AND name = 'reader_required_nullable_table_view'",
              null
            )
            .use(Cursor::readStrings)
        ).isEmpty()
      }
  }

  @Test
  fun failedMigrationRollsBackUpgradeTransaction() = withNamedDatabase(
    databaseName = MIGRATION_DATABASE_NAME
  ) { application ->
    seedDatabase(
      application = application,
      version = FAILING_MIGRATION_INITIAL_VERSION,
      statements = librarySeedStatements() + ROLLBACK_LEGACY_VIEW_SQL
    )

    val exception = assertThrows(IllegalStateException::class.java) {
      openMigrationConnection(
        application = application,
        database = FailingMigrationDatabase(dbVersion = FAILING_MIGRATION_VERSION)
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
        assertThat(database.version)
          .isEqualTo(FAILING_MIGRATION_INITIAL_VERSION)
        assertThat(
          database
            .rawQuery(
              "SELECT book_key, title_text FROM library_books",
              null
            )
            .use(Cursor::readRows)
        ).containsExactly(listOf(MIGRATION_BOOK_KEY, MIGRATION_BOOK_TITLE))
        assertThat(
          database
            .rawQuery(
              "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = ?",
              arrayOf(ROLLBACK_LEGACY_VIEW)
            )
            .use(Cursor::readStrings)
        ).containsExactly(ROLLBACK_LEGACY_VIEW_SQL)
        assertThat(
          database
            .rawQuery("SELECT book_key, title_text FROM $ROLLBACK_LEGACY_VIEW", null)
            .use(Cursor::readRows)
        ).containsExactly(listOf(MIGRATION_BOOK_KEY, MIGRATION_BOOK_TITLE))
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
    statements = librarySeedStatements()
  )

  private fun librarySeedStatements() = listOf(
    "CREATE TABLE library_books (book_key TEXT PRIMARY KEY, title_text TEXT DEFAULT 'untitled')",
    "INSERT INTO library_books (book_key, title_text) VALUES ('$MIGRATION_BOOK_KEY', '$MIGRATION_BOOK_TITLE')"
  )

  private fun currentAuthorSeedStatements() = listOf(
    "CREATE TABLE query_composition_author (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
    "INSERT INTO query_composition_author (id, name) VALUES ($VIEW_AUTHOR_ID, '$VIEW_AUTHOR_NAME')"
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

  private fun viewSql(
    connection: DbConnection,
    viewName: String
  ) = Select
    .raw("SELECT sql FROM sqlite_master WHERE type = 'view' AND name = ?")
    .from(Table.ANONYMOUS_TABLE)
    .withArgs(viewName)
    .usingConnection(connection)
    .execute()
    .use { cursor ->
      when {
        cursor.moveToFirst() -> cursor.getString(0)
        else -> null
      }
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
  override val dbVersion: Int
) : GeneratedDatabase by SqliteMagicDatabase() {
  override fun migrateViews(db: SupportSQLiteDatabase) {
    val migratedTables = db
      .query("SELECT name FROM sqlite_master WHERE type = 'table'")
      .use(Cursor::readStrings)
      .toSet()
    check(migratedTables.containsAll(VERSION_101_PERSISTENT_TABLES))
    val remainingViews = db
      .query("SELECT name FROM sqlite_master WHERE type = 'view'")
      .use(Cursor::readStrings)
      .toSet()
    check(ROLLBACK_LEGACY_VIEW !in remainingViews)
    throw IllegalStateException(MIGRATION_FAILURE_MESSAGE)
  }
}

private class DefinitionFailureMigrationDatabase(
  override val dbVersion: Int
) : GeneratedDatabase by SqliteMagicDatabase() {
  override fun migrateViews(db: SupportSQLiteDatabase) = DefinitionFailureGeneratedClassesManager.migrateViews(db)
}

private class DdlFailureMigrationDatabase(
  override val dbVersion: Int,
  private val failure: RuntimeException,
  private val delegate: SqliteMagicDatabase = SqliteMagicDatabase()
) : GeneratedDatabase by delegate {
  override fun migrateViews(db: SupportSQLiteDatabase) = delegate.migrateViews(
    object : SupportSQLiteDatabase by db {
      override fun execSQL(sql: String) {
        if (sql.startsWith("""CREATE VIEW IF NOT EXISTS "query_composition_author_view" AS """)) {
          val earlierViews = db
            .query("SELECT name FROM sqlite_master WHERE type = 'view'")
            .use(Cursor::readStrings)
          check("reader_required_nullable_table_view" in earlierViews)
          throw failure
        }
        db.execSQL(sql)
      }
    }
  )
}

private class VersionedMigrationDatabase(
  override val dbVersion: Int
) : GeneratedDatabase by SqliteMagicDatabase()
