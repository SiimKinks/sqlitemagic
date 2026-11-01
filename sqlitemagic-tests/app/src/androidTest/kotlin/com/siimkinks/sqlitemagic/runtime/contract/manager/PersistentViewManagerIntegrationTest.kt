@file:OptIn(InternalSqliteMagicApi::class)

package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.DefinitionFailureGeneratedClassesManager
import com.siimkinks.sqlitemagic.DependencyOuterViewTable.Companion.DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.MainSubmoduleDependencyOuterViewTable.Companion.MAIN_SUBMODULE_DEPENDENCY_OUTER_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.fixture.view.DependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.MainSubmoduleDependencyOuterView
import com.siimkinks.sqlitemagic.fixture.view.QueryCompositionAuthor
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.SqlUtil.createSchema
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.PrefixFailingConnection
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.withEmptyDatabase
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

private const val DATABASE_NAME = "persistent-view-manager-integration.db"
private const val DEFINITION_FAILURE_DATABASE_NAME = "persistent-view-definition-failure.db"

internal class PersistentViewManagerIntegrationTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun freshOpenOrdersGeneratedViewChainsAndMainSubmoduleDependencies() =
    withNamedDatabase(databaseName = DATABASE_NAME) { application ->
      openNamedConnection(
        application = application,
        databaseName = DATABASE_NAME
      ).use { connection ->
        assertThat(
          schemaObjectNames(
            master = SQLITE_MASTER,
            type = "view",
            connection = connection
          )
        ).containsAtLeast(
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
          "review_relationship_book",
          "persistent_submodule_readback"
        )

        insertedResult(
          result = QueryCompositionAuthor(
            id = 81L,
            name = "Ada"
          )
            .insert()
            .usingConnection(connection)
            .execute(),
          modelName = "QueryCompositionAuthor"
        )
        insertedResult(
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
          SELECT
            .FROM(DEPENDENCY_OUTER_VIEW)
            .usingConnection(connection)
            .execute()
        ).containsExactly(DependencyOuterView(name = "Ada"))
        assertThat(
          SELECT
            .FROM(MAIN_SUBMODULE_DEPENDENCY_OUTER_VIEW)
            .usingConnection(connection)
            .execute()
        ).containsExactly(MainSubmoduleDependencyOuterView(value = "submodule value"))
      }
    }

  @Test
  fun generatedSchemaCreationRollsBackDelegatedAndLocalFailures() {
    listOf(
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
    ).forEach(::assertRollback)
  }

  @Test
  fun definitionFailureIdentifiesItsViewAndRollsBackEarlierSchemaWork() =
    withEmptyDatabase(databaseName = DEFINITION_FAILURE_DATABASE_NAME) { connection ->
      val failure = assertThrows(IllegalArgumentException::class.java) {
        createSchema(
          connection = connection,
          schema = DefinitionFailureGeneratedClassesManager,
          temporary = false
        )
      }

      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("Cannot create view 'definition_failure_view': its definition cannot take parameter 'id'")
      assertThat(
        connection.schemaObjectNames(
          master = SQLITE_MASTER,
          type = "table"
        )
      ).doesNotContain("definition_failure_row")
    }

  private data class RollbackCase(
    val label: String,
    val databaseName: String,
    val sqlPrefix: String,
    val rolledBackObjects: List<String>
  )

  private fun assertRollback(case: RollbackCase) =
    withEmptyDatabase(databaseName = case.databaseName) { connection ->
      val expectedFailure = IllegalStateException("Injected ${case.label} failure")
      val actualFailure = assertThrows(IllegalStateException::class.java) {
        SqliteMagicDatabase().createSchema(
          PrefixFailingConnection(
            delegate = connection,
            sqlPrefix = case.sqlPrefix,
            failure = expectedFailure
          )
        )
      }

      assertThat(actualFailure)
        .isSameInstanceAs(expectedFailure)
      assertWithMessage(case.label)
        .that(
          connection.schemaObjectNames(
            master = SQLITE_MASTER,
            type = "table"
          )
        )
        .containsNoneIn(case.rolledBackObjects)
    }
}
