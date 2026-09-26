package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DefinitionFailureGeneratedClassesManager
import com.siimkinks.sqlitemagic.PersistentEmailReadbackViewTable.Companion.PERSISTENT_EMAIL_READBACK
import com.siimkinks.sqlitemagic.PersistentEmbeddedReadbackViewTable.Companion.PERSISTENT_EMBEDDED_READBACK
import com.siimkinks.sqlitemagic.PersistentNestedReadbackViewTable.Companion.PERSISTENT_NESTED_READBACK
import com.siimkinks.sqlitemagic.PersistentSubmoduleReadbackViewTable.Companion.PERSISTENT_SUBMODULE_READBACK
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReviewRelationshipCompositionViewTable.Companion.REVIEW_RELATIONSHIP_COMPOSITION
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.Table.Companion.ANONYMOUS_TABLE
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.view.PersistentContact
import com.siimkinks.sqlitemagic.fixture.view.PersistentEmailReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentEmbeddedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentInnerReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentNestedReadbackView
import com.siimkinks.sqlitemagic.fixture.view.PersistentSubmoduleReadbackView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthorView
import com.siimkinks.sqlitemagic.fixture.view.ReaderEmail
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipCompositionView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.contract.query.readTypedCursor
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DATABASE_NAME = "persistent-view-manager-integration.db"
private const val DEFINITION_FAILURE_DATABASE_NAME = "persistent-view-definition-failure.db"

class PersistentViewManagerIntegrationTest : RuntimeDatabaseTest() {
  @Test
  fun freshOpenCreatesViewsAndReadsScalarTransformerEmbeddedAndNestedDtos() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME,
        database = SqliteMagicDatabase()
      ).use { connection ->
        val viewNames = Select
          .raw("SELECT name FROM sqlite_master WHERE type = 'view'")
          .from(ANONYMOUS_TABLE)
          .usingConnection(connection)
          .execute()
          .use(Cursor::readStrings)
        assertThat(viewNames).containsAtLeast(
          "query_composition_author_view",
          "persistent_email_readback",
          "persistent_embedded_readback",
          "persistent_inner_readback",
          "persistent_nested_readback",
          "review_relationship_composition",
          "persistent_submodule_readback"
        )

        QueryCompositionAuthor(
          id = 7L,
          name = "Ada"
        )
          .insert()
          .usingConnection(connection)
          .execute()

        val expectedScalar = QueryCompositionAuthorView(
          name = "Ada",
          id = 7L
        )
        val scalarQuery = Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(connection)
        assertThat(scalarQuery.execute()).containsExactly(expectedScalar)
        assertThat(
          Select
            .from(QUERY_COMPOSITION_AUTHOR_VIEW)
            .usingConnection(connection)
            .takeFirst()
            .execute()
        ).isEqualTo(expectedScalar)
        assertThat(
          Select
            .from(QUERY_COMPOSITION_AUTHOR_VIEW)
            .usingConnection(connection)
            .count()
            .execute()
        ).isEqualTo(1L)
        val cursorQuery = Select
          .from(QUERY_COMPOSITION_AUTHOR_VIEW)
          .usingConnection(connection)
          .toCursor()
        assertThat(
          readTypedCursor(
            cursorSelect = cursorQuery,
            cursor = cursorQuery.execute()
          )
        ).containsExactly(expectedScalar)

        assertThat(
          Select
            .from(PERSISTENT_EMAIL_READBACK)
            .usingConnection(connection)
            .execute()
        ).containsExactly(PersistentEmailReadbackView(email = ReaderEmail(value = "Ada")))
        assertThat(
          Select
            .from(PERSISTENT_EMBEDDED_READBACK)
            .usingConnection(connection)
            .execute()
        ).containsExactly(
          PersistentEmbeddedReadbackView(
            contact = PersistentContact(
              city = "Ada",
              zip = 7
            )
          )
        )
        assertThat(
          Select
            .from(PERSISTENT_NESTED_READBACK)
            .usingConnection(connection)
            .execute()
        ).containsExactly(
          PersistentNestedReadbackView(
            inner = PersistentInnerReadbackView(name = "Ada"),
            tail = "Ada"
          )
        )
      }
    }

  @Test
  fun tableGraphViewReadsCompleteRelationshipFromOneRow() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME,
        database = SqliteMagicDatabase()
      ).use { connection ->
        val author = ReaderRelationshipAuthor(
          id = 17L,
          name = "Ada"
        )
        val book = ReaderRelationshipBook(
          id = 23L,
          author = author
        )
        val insertResult = book
          .insert()
          .usingConnection(connection)
          .execute()
        val bookId = when (insertResult) {
          is EntityInsertResult.Inserted -> checkNotNull(insertResult.rowId)
          EntityInsertResult.Ignored -> error("Relationship book insert was ignored")
        }
        val authorId = checkNotNull(
          Select
            .column(READER_RELATIONSHIP_AUTHOR.ID)
            .from(READER_RELATIONSHIP_AUTHOR)
            .usingConnection(connection)
            .takeFirst()
            .execute()
        )

        assertThat(
          Select
            .from(REVIEW_RELATIONSHIP_COMPOSITION)
            .usingConnection(connection)
            .execute()
        ).containsExactly(
          ReviewRelationshipCompositionView(
            book = ReaderRelationshipBook(
              id = bookId,
              author = author.copy(id = authorId)
            ),
            tail = "tail"
          )
        )
      }
    }

  @Test
  fun generatedSchemaCreationRollsBackDelegatedAndLocalFailures() {
    val cases = listOf(
      RollbackCase(
        label = "delegated index",
        databaseName = "persistent-view-delegated-rollback.db",
        sqlPrefix = "CREATE INDEX IF NOT EXISTS main.\"submodule_persistent_value_index\"",
        rolledBackObjects = listOf("submodule_persistent_value")
      ),
      RollbackCase(
        label = "local view",
        databaseName = "persistent-view-local-rollback.db",
        sqlPrefix = "CREATE VIEW IF NOT EXISTS \"reader_required_nullable_table_view\" AS ",
        rolledBackObjects = listOf(
          "submodule_persistent_value",
          "no_id_entity"
        )
      )
    )

    cases.forEach(::assertRollback)
  }

  @Test
  fun unsupportedDefinitionIdentifiesItsViewAndRollsBackEarlierSchemaWork() =
    withNamedDatabase(databaseName = DEFINITION_FAILURE_DATABASE_NAME) { application ->
      withEmptyDatabase(
        application = application,
        databaseName = DEFINITION_FAILURE_DATABASE_NAME
      ) { database ->
        val implementationName = DefinitionFailureView.query.javaClass.name

        val failure = assertThrows(IllegalStateException::class.java) {
          DefinitionFailureGeneratedClassesManager.createSchema(database)
        }

        assertThat(failure).hasMessageThat().contains("definition_failure_view")
        assertThat(failure).hasMessageThat().contains(implementationName)
        assertThat(database.schemaObjectNames()).doesNotContain("definition_failure_row")
      }
    }

  @Test
  fun mainViewReadsPublicSubmoduleTableScalars() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME,
        database = SqliteMagicDatabase()
      ).use { connection ->
        SubmodulePersistentValue(
          id = "submodule-id",
          value = "submodule-value"
        )
          .insert()
          .usingConnection(connection)
          .execute()

        assertThat(
          Select
            .from(PERSISTENT_SUBMODULE_READBACK)
            .usingConnection(connection)
            .execute()
        ).containsExactly(
          PersistentSubmoduleReadbackView(
            id = "submodule-id",
            value = "submodule-value"
          )
        )
      }
    }

  private data class RollbackCase(
    val label: String,
    val databaseName: String,
    val sqlPrefix: String,
    val rolledBackObjects: List<String>
  )

  private fun assertRollback(case: RollbackCase) =
    withNamedDatabase(databaseName = case.databaseName) { application ->
      withEmptyDatabase(
        application = application,
        databaseName = case.databaseName
      ) { database ->
        val expectedFailure = IllegalStateException("Injected ${case.label} failure")
        val failingDatabase = FailingDatabase(
          delegate = database,
          sqlPrefix = case.sqlPrefix,
          failure = expectedFailure
        )

        val actualFailure = assertThrows(IllegalStateException::class.java) {
          SqliteMagicDatabase().createSchema(failingDatabase)
        }

        assertThat(actualFailure).isSameInstanceAs(expectedFailure)
        assertWithMessage(case.label)
          .that(database.schemaObjectNames())
          .containsNoneIn(case.rolledBackObjects)
      }
    }

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

  private companion object {
    val EMPTY_DATABASE_CALLBACK = object : SupportSQLiteOpenHelper.Callback(1) {
      override fun onCreate(db: SupportSQLiteDatabase) = Unit

      override fun onUpgrade(
        db: SupportSQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
      ) = Unit
    }

    inline fun withEmptyDatabase(
      application: android.app.Application,
      databaseName: String,
      block: (SupportSQLiteDatabase) -> Unit
    ) {
      val configuration = SupportSQLiteOpenHelper.Configuration
        .builder(application)
        .name(databaseName)
        .callback(EMPTY_DATABASE_CALLBACK)
        .build()
      FrameworkSQLiteOpenHelperFactory()
        .create(configuration)
        .use { helper ->
          block(helper.writableDatabase)
        }
    }

    fun SupportSQLiteDatabase.schemaObjectNames() = query(
      "SELECT name FROM sqlite_master WHERE type IN ('table', 'view', 'index')"
    ).use(Cursor::readStrings)
  }
}
