package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DefinitionFailureGeneratedClassesManager
import com.siimkinks.sqlitemagic.DependencyOuterViewTable.Companion.DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.MainSubmoduleDependencyOuterViewTable.Companion.MAIN_SUBMODULE_DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.ReaderRelationshipAuthorTable.Companion.READER_RELATIONSHIP_AUTHOR
import com.siimkinks.sqlitemagic.ReviewRelationshipCompositionViewTable.Companion.REVIEW_RELATIONSHIP_COMPOSITION_VIEW
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.view.DependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.MainSubmoduleDependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipAuthor
import com.siimkinks.sqlitemagic.fixture.view.ReaderRelationshipBook
import com.siimkinks.sqlitemagic.fixture.view.ReviewRelationshipCompositionView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.support.PrefixFailingDatabase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.mainSchemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withEmptyDatabase
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DATABASE_NAME = "persistent-view-manager-integration.db"
private const val DEFINITION_FAILURE_DATABASE_NAME = "persistent-view-definition-failure.db"

class PersistentViewManagerIntegrationTest : RuntimeDatabaseTest() {
  @Test
  fun freshOpenOrdersGeneratedViewChainsAndMainSubmoduleDependencies() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME,
        database = SqliteMagicDatabase()
      ).use { connection ->
        val viewNames = mainSchemaObjectNames(
          connection = connection,
          type = "view"
        )
        assertThat(viewNames)
          .containsAtLeast(
            "a_dependency_outer",
            "m_dependency_middle",
            "z_dependency_base",
            "a_main_submodule_dependency_outer",
            "z_submodule_dependency_base",
            "query_composition_author_view",
            "persistent_email_readback",
            "persistent_embedded_readback",
            "persistent_inner_readback",
            "persistent_nested_readback",
            "review_relationship_composition",
            "persistent_submodule_readback"
          )

        assertSeedInserted(
          result = QueryCompositionAuthor(
            id = 81L,
            name = "Ada"
          )
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "QueryCompositionAuthor"
        )
        assertSeedInserted(
          result = SubmodulePersistentValue(
            id = "graph-submodule",
            value = "submodule value"
          )
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "SubmodulePersistentValue"
        )

        assertThat(
          Select
            .from(DEPENDENCY_OUTER_VIEW)
            .usingConnection(connection)
            .execute()
        ).containsExactly(DependencyOuterView(name = "Ada"))
        assertThat(
          Select
            .from(MAIN_SUBMODULE_DEPENDENCY_OUTER_VIEW)
            .usingConnection(connection)
            .execute()
        ).containsExactly(MainSubmoduleDependencyOuterView(value = "submodule value"))
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
            .from(REVIEW_RELATIONSHIP_COMPOSITION_VIEW)
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
        sqlPrefix = """CREATE INDEX IF NOT EXISTS main."submodule_persistent_value_index"""",
        rolledBackObjects = listOf("submodule_persistent_value")
      ),
      RollbackCase(
        label = "local view",
        databaseName = "persistent-view-local-rollback.db",
        sqlPrefix = """CREATE VIEW IF NOT EXISTS "reader_required_nullable_table_view" AS """,
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
    withEmptyDatabase(databaseName = DEFINITION_FAILURE_DATABASE_NAME) { database ->
      val implementationName = (DefinitionFailureView.query as Any).javaClass.name

      val failure = assertThrows(IllegalStateException::class.java) {
        DefinitionFailureGeneratedClassesManager.createSchema(database)
      }

      assertThat(failure)
        .hasMessageThat()
        .isEqualTo(
          "Cannot create view 'definition_failure_view': defining query uses unsupported implementation " +
              implementationName
        )
      assertThat(mainSchemaObjectNames(database = database))
        .doesNotContain("definition_failure_row")
    }

  private data class RollbackCase(
    val label: String,
    val databaseName: String,
    val sqlPrefix: String,
    val rolledBackObjects: List<String>
  )

  private fun assertRollback(case: RollbackCase) =
    withEmptyDatabase(databaseName = case.databaseName) { database ->
      val expectedFailure = IllegalStateException("Injected ${case.label} failure")
      val failingDatabase = PrefixFailingDatabase(
        delegate = database,
        sqlPrefix = case.sqlPrefix,
        failure = expectedFailure
      )

      val actualFailure = assertThrows(IllegalStateException::class.java) {
        SqliteMagicDatabase().createSchema(failingDatabase)
      }

      assertThat(actualFailure)
        .isSameInstanceAs(expectedFailure)
      assertWithMessage(case.label)
        .that(mainSchemaObjectNames(database = database))
        .containsNoneIn(case.rolledBackObjects)
    }
}
